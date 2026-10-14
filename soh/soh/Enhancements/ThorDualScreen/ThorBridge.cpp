// Thor Dual Screen: game-side bridge for the AYN Thor companion screen.
// See ThorBridge.h for the snapshot layout and command set.

#include "ThorBridge.h"

#include <array>
#include <cmath>
#include <cstring>
#include <deque>
#include <mutex>
#include <string>
#include <vector>

#include <libultraship/libultraship.h>
#include "resource/type/Texture.h"
#include <stb_image.h>

#include "soh/Enhancements/game-interactor/GameInteractor.h"
#include "soh/Enhancements/game-interactor/GameInteractor_Hooks.h"
#include "soh/ResourceManagerHelpers.h"
#include "soh/ShipInit.hpp"
#include "soh/cvar_prefixes.h"

extern "C" {
#include "z64.h"
#include "macros.h"
#include "variables.h"
#include "functions.h"
extern PlayState* gPlayState;
extern u8 gEquipAgeReqs[][4];
}

#ifndef AGE_REQ_NONE
#define AGE_REQ_NONE 9 // matches z_kaleido_scope.h
#endif

namespace {

struct Command {
    int type;
    int a;
    int b;
};

std::mutex sSnapMutex;
std::array<int32_t, THOR_SNAPSHOT_SIZE> sSnapshot{};

std::mutex sCmdMutex;
std::deque<Command> sCommands;
std::vector<std::string> sPendingCVarInits; // guarded by sCmdMutex; run on the game thread

// Held-button emulation (bottom screen "touch item" buttons).
uint32_t sHeldMask = 0;
int sHeldFrames = 0;

int sLastResult = TR_OK;
int sLastResultSeq = 0;
int sFrame = 0;

bool TimelessEquipment() {
    return CVarGetInteger(CVAR_CHEAT("TimelessEquipment"), 0) != 0;
}

bool AgeOkSlot(int slot) {
    return TimelessEquipment() || gSlotAgeReqs[slot] == AGE_REQ_NONE || gSlotAgeReqs[slot] == gSaveContext.linkAge;
}

bool AgeOkEquip(int type, int value) {
    return TimelessEquipment() || gEquipAgeReqs[type][value] == AGE_REQ_NONE ||
           gEquipAgeReqs[type][value] == gSaveContext.linkAge;
}

bool InGame() {
    return GameInteractor::IsSaveLoaded(false) && gPlayState != nullptr;
}

// True when it is safe to swap button items / equipment without the pause menu.
bool CanEquipNow() {
    if (!InGame()) {
        return false;
    }
    Player* player = GET_PLAYER(gPlayState);
    if (player == nullptr) {
        return false;
    }
    if (gPlayState->pauseCtx.state != 0 || gPlayState->msgCtx.msgMode != 0) {
        return false;
    }
    if (Player_InBlockingCsMode(gPlayState, player)) {
        return false;
    }
    const u32 busy1 = PLAYER_STATE1_LOADING | PLAYER_STATE1_INPUT_DISABLED | PLAYER_STATE1_TALKING |
                      PLAYER_STATE1_DEAD | PLAYER_STATE1_START_CHANGING_HELD_ITEM | PLAYER_STATE1_READY_TO_FIRE |
                      PLAYER_STATE1_GETTING_ITEM | PLAYER_STATE1_FIRST_PERSON | PLAYER_STATE1_USING_BOOMERANG |
                      PLAYER_STATE1_IN_ITEM_CS | PLAYER_STATE1_IN_CUTSCENE;
    if (player->stateFlags1 & busy1) {
        return false;
    }
    if (player->stateFlags2 & PLAYER_STATE2_OCARINA_PLAYING) {
        return false;
    }
    return true;
}

void PlayDecideSfx() {
    Audio_PlaySoundGeneral(NA_SE_SY_DECIDE, &gSfxDefaultPos, 4, &gSfxDefaultFreqAndVolScale,
                           &gSfxDefaultFreqAndVolScale, &gSfxDefaultReverb);
}

void PlayErrorSfx() {
    Audio_PlaySoundGeneral(NA_SE_SY_ERROR, &gSfxDefaultPos, 4, &gSfxDefaultFreqAndVolScale,
                           &gSfxDefaultFreqAndVolScale, &gSfxDefaultReverb);
}

// Mirrors KaleidoScope_UpdateItemEquip's final assignment, minus the animation.
int AssignSlotToButton(int slot, int button) {
    const int maxButton = CVarGetInteger(CVAR_ENHANCEMENT("DpadEquips"), 0) ? 7 : 3;
    if (button < 1 || button > maxButton) {
        return TR_BAD_ARGS;
    }

    u16 item;
    u16 targetSlot = slot;

    if (slot >= SLOT_TUNIC_KOKIRI && slot <= SLOT_BOOTS_HOVER) {
        // Tunics and boots on buttons are only meaningful with the SoH enhancement on.
        if (!CVarGetInteger(CVAR_ENHANCEMENT("AssignableTunicsAndBoots"), 0)) {
            return TR_BAD_ARGS;
        }
        const int type = (slot <= SLOT_TUNIC_ZORA) ? EQUIP_TYPE_TUNIC : EQUIP_TYPE_BOOTS;
        const int inv = (slot <= SLOT_TUNIC_ZORA) ? slot - SLOT_TUNIC_KOKIRI : slot - SLOT_BOOTS_KOKIRI;
        if (!CHECK_OWNED_EQUIP(type, inv)) {
            return TR_NOT_OWNED;
        }
        if (!AgeOkEquip(type, inv + 1)) {
            return TR_WRONG_AGE;
        }
        item = ITEM_TUNIC_KOKIRI + (slot - SLOT_TUNIC_KOKIRI);
    } else {
        if (slot < 0 || slot >= 24) {
            return TR_BAD_ARGS;
        }
        item = gSaveContext.inventory.items[slot];
        if (item == ITEM_NONE || item == ITEM_SOLD_OUT) {
            return TR_NOT_OWNED;
        }
        if (!AgeOkSlot(slot)) {
            return TR_WRONG_AGE;
        }
    }

    // Magic arrows ride on the bow unless SoH's "separate arrows" is enabled.
    if (item == ITEM_ARROW_FIRE || item == ITEM_ARROW_ICE || item == ITEM_ARROW_LIGHT) {
        if (INV_CONTENT(ITEM_BOW) != ITEM_BOW) {
            return TR_NOT_OWNED;
        }
        item = ITEM_BOW_ARROW_FIRE + (item == ITEM_ARROW_ICE ? 1 : item == ITEM_ARROW_LIGHT ? 2 : 0);
        if (!CVarGetInteger(CVAR_ENHANCEMENT("SeparateArrows"), 0)) {
            targetSlot = SLOT_BOW;
        }
    }

    const int targetCBtn = button - 1;

    for (int otherSlotIndex = 0; otherSlotIndex < (int)ARRAY_COUNT(gSaveContext.equips.cButtonSlots);
         otherSlotIndex++) {
        const int otherButtonIndex = otherSlotIndex + 1;
        if (otherSlotIndex == targetCBtn) {
            continue;
        }

        if (targetSlot == gSaveContext.equips.cButtonSlots[otherSlotIndex]) {
            if (gSaveContext.equips.buttonItems[button] != ITEM_NONE) {
                gSaveContext.equips.buttonItems[otherButtonIndex] = gSaveContext.equips.buttonItems[button];
                gSaveContext.equips.cButtonSlots[otherSlotIndex] = gSaveContext.equips.cButtonSlots[targetCBtn];
                Interface_LoadItemIcon2(gPlayState, otherButtonIndex);
            } else {
                gSaveContext.equips.buttonItems[otherButtonIndex] = ITEM_NONE;
                gSaveContext.equips.cButtonSlots[otherSlotIndex] = SLOT_NONE;
            }
        }

        // Same "equip dupe" guard the pause menu uses for bow + magic arrows.
        if (item == ITEM_BOW && !CVarGetInteger(CVAR_ENHANCEMENT("SeparateArrows"), 0) &&
            gSaveContext.equips.buttonItems[otherButtonIndex] >= ITEM_BOW_ARROW_FIRE &&
            gSaveContext.equips.buttonItems[otherButtonIndex] <= ITEM_BOW_ARROW_LIGHT) {
            gSaveContext.equips.buttonItems[otherButtonIndex] = gSaveContext.equips.buttonItems[button];
            gSaveContext.equips.cButtonSlots[otherSlotIndex] = gSaveContext.equips.cButtonSlots[targetCBtn];
            Interface_LoadItemIcon2(gPlayState, otherButtonIndex);
        }
    }

    gSaveContext.equips.buttonItems[button] = item;
    gSaveContext.equips.cButtonSlots[targetCBtn] = targetSlot;
    Interface_LoadItemIcon1(gPlayState, button);
    return TR_OK;
}

int ClearButton(int button) {
    if (button < 1 || button > 7) {
        return TR_BAD_ARGS;
    }
    gSaveContext.equips.buttonItems[button] = ITEM_NONE;
    gSaveContext.equips.cButtonSlots[button - 1] = SLOT_NONE;
    Interface_LoadItemIcon1(gPlayState, button);
    return TR_OK;
}

// Mirrors the equipment subscreen's A-press path (without the "remove equipment" toggles).
int Equip(int type, int value) {
    if (type < EQUIP_TYPE_SWORD || type > EQUIP_TYPE_BOOTS || value < 1 || value > 3) {
        return TR_BAD_ARGS;
    }
    if (!AgeOkEquip(type, value)) {
        return TR_WRONG_AGE;
    }

    Player* player = GET_PLAYER(gPlayState);

    if (type == EQUIP_TYPE_SWORD) {
        if (player->meleeWeaponState != 0) {
            return TR_BUSY;
        }
        bool owned = CHECK_OWNED_EQUIP(EQUIP_TYPE_SWORD, value - 1);
        if (value == EQUIP_VALUE_SWORD_BIGGORON) {
            owned = owned || gSaveContext.bgsFlag ||
                    CHECK_OWNED_EQUIP_ALT(EQUIP_TYPE_SWORD, EQUIP_INV_SWORD_BROKENGIANTKNIFE);
        }
        if (!owned) {
            return TR_NOT_OWNED;
        }
        Inventory_ChangeEquipment(EQUIP_TYPE_SWORD, value);
        gSaveContext.infTable[29] = 0; // INFTABLE_SWORDLESS cleared, same as the pause menu
        gSaveContext.equips.buttonItems[0] = ITEM_SWORD_KOKIRI + value - 1;
        if (value == EQUIP_VALUE_SWORD_BIGGORON) {
            if (gSaveContext.bgsFlag != 0) {
                gSaveContext.equips.buttonItems[0] = ITEM_SWORD_BGS;
                gSaveContext.swordHealth = 8;
            } else if (CHECK_OWNED_EQUIP_ALT(EQUIP_TYPE_SWORD, EQUIP_INV_SWORD_BROKENGIANTKNIFE)) {
                gSaveContext.equips.buttonItems[0] = ITEM_SWORD_KNIFE;
            }
        }
        Interface_LoadItemIcon1(gPlayState, 0);
    } else {
        if (!CHECK_OWNED_EQUIP(type, value - 1)) {
            return TR_NOT_OWNED;
        }
        Inventory_ChangeEquipment(type, value);
    }

    Player_SetEquipmentData(gPlayState, player);
    return TR_OK;
}

void ApplyCommand(const Command& c) {
    int result = TR_OK;

    if (c.type == TC_PRESS) {
        sHeldMask = (uint32_t)c.a;
        sHeldFrames = c.b < 1 ? 1 : (c.b > 600 ? 600 : c.b);
        return;
    }

    if (!InGame()) {
        result = TR_NOT_IN_GAME;
    } else if (!CanEquipNow()) {
        result = TR_BUSY;
    } else {
        switch (c.type) {
            case TC_ASSIGN_SLOT:
                result = AssignSlotToButton(c.a, c.b);
                break;
            case TC_EQUIP:
                result = Equip(c.a, c.b);
                break;
            case TC_CLEAR_BUTTON:
                result = ClearButton(c.a);
                break;
            default:
                result = TR_BAD_ARGS;
                break;
        }
    }

    if (result == TR_OK) {
        PlayDecideSfx();
    } else if (InGame()) {
        PlayErrorSfx();
    }
    sLastResult = result;
    sLastResultSeq++;
}

void PublishSnapshot() {
    std::array<int32_t, THOR_SNAPSHOT_SIZE> s{};
    s[TS_VERSION] = THOR_SNAPSHOT_VERSION;
    s[TS_FRAME] = ++sFrame;
    s[TS_LAST_RESULT] = sLastResult;
    s[TS_LAST_RESULT_SEQ] = sLastResultSeq;
    s[TS_DPAD_EQUIPS] = CVarGetInteger(CVAR_ENHANCEMENT("DpadEquips"), 0);
    s[TS_ASSIGNABLE_TUNICS] = CVarGetInteger(CVAR_ENHANCEMENT("AssignableTunicsAndBoots"), 0);
    s[TS_SEPARATE_ARROWS] = CVarGetInteger(CVAR_ENHANCEMENT("SeparateArrows"), 0);
    s[TS_TIMELESS_EQUIP] = TimelessEquipment() ? 1 : 0;

    const bool inGame = InGame();
    if (inGame) {
        Player* player = GET_PLAYER(gPlayState);
        int flags = TF_SAVE_LOADED;
        if (gPlayState->pauseCtx.state != 0) {
            flags |= TF_PAUSED;
        }
        if (player != nullptr && Player_InBlockingCsMode(gPlayState, player)) {
            flags |= TF_BLOCKING_CS;
        }
        if (LINK_IS_ADULT) {
            flags |= TF_ADULT;
        }
        if (gSaveContext.nightFlag) {
            flags |= TF_NIGHT;
        }
        if (CanEquipNow()) {
            flags |= TF_CAN_EQUIP;
        }
        if (gPlayState->sceneNum >= 0 && gPlayState->sceneNum < 20) {
            flags |= TF_IN_DUNGEON;
        }
        s[TS_FLAGS] = flags;
        s[TS_SCENE] = gPlayState->sceneNum;
        s[TS_ROOM] = gPlayState->roomCtx.curRoom.num;
        s[TS_SCENE_MQ] = ResourceMgr_IsSceneMasterQuest(gPlayState->sceneNum) ? 1 : 0;
        s[TS_PAUSE_STATE] = gPlayState->pauseCtx.state;
        s[TS_MSG_MODE] = gPlayState->msgCtx.msgMode;
        if (player != nullptr) {
            s[TS_POS_X] = (int32_t)player->actor.world.pos.x;
            s[TS_POS_Y] = (int32_t)player->actor.world.pos.y;
            s[TS_POS_Z] = (int32_t)player->actor.world.pos.z;
            s[TS_YAW] = player->actor.shape.rot.y;
            s[TS_STATE_FLAGS1] = (int32_t)player->stateFlags1;
            s[TS_STATE_FLAGS2] = (int32_t)player->stateFlags2;
        }
    }

    // Save context is valid on file select too (after a file is chosen), so publish it whenever
    // a real file is in memory.
    if (inGame) {
        s[TS_HEALTH] = gSaveContext.health;
        s[TS_HEALTH_CAPACITY] = gSaveContext.healthCapacity;
        s[TS_MAGIC] = gSaveContext.magic;
        s[TS_MAGIC_CAPACITY] = gSaveContext.magicLevel * MAGIC_NORMAL_METER;
        s[TS_RUPEES] = gSaveContext.rupees;
        s[TS_WALLET_UPG] = CUR_UPG_VALUE(UPG_WALLET);
        s[TS_DAY_TIME] = gSaveContext.dayTime;
        s[TS_EQUIPS] = gSaveContext.equips.equipment;
        s[TS_INV_EQUIPMENT] = gSaveContext.inventory.equipment;
        s[TS_UPGRADES] = (int32_t)gSaveContext.inventory.upgrades;
        s[TS_QUEST_ITEMS] = (int32_t)gSaveContext.inventory.questItems;
        s[TS_GS_TOKENS] = gSaveContext.inventory.gsTokens;
        s[TS_DOUBLE_DEFENSE] = gSaveContext.isDoubleDefenseAcquired;
        s[TS_BGS_FLAG] = gSaveContext.bgsFlag;
        s[TS_SWORD_HEALTH] = gSaveContext.swordHealth;
        s[TS_FILE_NUM] = gSaveContext.fileNum;
        s[TS_DEATHS] = gSaveContext.deaths;
        s[TS_LINK_AGE] = gSaveContext.linkAge;
        for (int i = 0; i < 8; i++) {
            s[TS_BUTTON_ITEMS + i] = gSaveContext.equips.buttonItems[i];
            s[TS_NAME + i] = gSaveContext.playerName[i];
        }
        for (int i = 0; i < 7; i++) {
            s[TS_CBUTTON_SLOTS + i] = gSaveContext.equips.cButtonSlots[i];
        }
        for (int i = 0; i < 24; i++) {
            s[TS_ITEMS + i] = gSaveContext.inventory.items[i];
        }
        for (int i = 0; i < 16; i++) {
            s[TS_AMMO + i] = gSaveContext.inventory.ammo[i];
        }
        for (int i = 0; i < 20; i++) {
            s[TS_DUNGEON_ITEMS + i] = gSaveContext.inventory.dungeonItems[i];
        }
        for (int i = 0; i < 19; i++) {
            s[TS_DUNGEON_KEYS + i] = gSaveContext.inventory.dungeonKeys[i];
        }
    }

    std::lock_guard<std::mutex> lock(sSnapMutex);
    sSnapshot = s;
}

void OnFrame() {
    {
        std::deque<Command> pending;
        std::vector<std::string> cvarInits;
        {
            std::lock_guard<std::mutex> lock(sCmdMutex);
            pending.swap(sCommands);
            cvarInits.swap(sPendingCVarInits);
        }
        for (const auto& name : cvarInits) {
            ShipInit::Init(name);
        }
        for (const auto& c : pending) {
            ApplyCommand(c);
        }
    }

    // Emulated buttons are consumed by padmgr on the next frame, so set them every frame we hold.
    if (sHeldFrames > 0) {
        GameInteractor_SetEmulatedButtons(GameInteractor_GetEmulatedButtons() | sHeldMask);
        sHeldFrames--;
        if (sHeldFrames == 0) {
            sHeldMask = 0;
        }
    }

    PublishSnapshot();
}

void RegisterThorBridge() {
    static bool registered = false;
    if (registered) {
        return;
    }
    registered = true;
    GameInteractor::Instance->RegisterGameHook<GameInteractor::OnGameFrameUpdate>(OnFrame);
}

// ---- Icon export ------------------------------------------------------------------------------

inline uint32_t Argb(uint32_t r, uint32_t g, uint32_t b, uint32_t a) {
    return (a << 24) | (r << 16) | (g << 8) | b;
}

// Decodes an OTR texture into ARGB8888. Returns false for formats we do not handle (CI).
bool DecodeTexture(const char* path, int& outW, int& outH, std::vector<uint32_t>& out) {
    if (path == nullptr) {
        return false;
    }
    auto res = std::static_pointer_cast<Fast::Texture>(ResourceMgr_GetResourceByNameHandlingMQ(path));
    if (res == nullptr || res->ImageData == nullptr) {
        return false;
    }

    const uint8_t* d = res->ImageData;
    const uint32_t size = res->ImageDataSize;

    if (res->Flags & TEX_FLAG_LOAD_AS_IMG) {
        int w, h, comp;
        unsigned char* px = stbi_load_from_memory(d, (int)size, &w, &h, &comp, STBI_rgb_alpha);
        if (px == nullptr) {
            return false;
        }
        outW = w;
        outH = h;
        out.resize((size_t)w * h);
        for (int i = 0; i < w * h; i++) {
            out[i] = Argb(px[i * 4], px[i * 4 + 1], px[i * 4 + 2], px[i * 4 + 3]);
        }
        stbi_image_free(px);
        return true;
    }

    int w = res->Width;
    int h = res->Height;

    if (res->Flags & TEX_FLAG_LOAD_AS_RAW) {
        // Pre-decoded RGBA8888, possibly upscaled by a texture pack.
        const uint32_t pixels = size / 4;
        if ((uint32_t)(w * h) != pixels && w > 0 && h > 0) {
            const double scale = std::sqrt((double)pixels / (double)(w * h));
            w = (int)std::lround(w * scale);
            h = (int)std::lround(h * scale);
        }
        if ((uint32_t)(w * h) * 4 > size) {
            return false;
        }
        outW = w;
        outH = h;
        out.resize((size_t)w * h);
        for (int i = 0; i < w * h; i++) {
            out[i] = Argb(d[i * 4], d[i * 4 + 1], d[i * 4 + 2], d[i * 4 + 3]);
        }
        return true;
    }

    const int n = w * h;
    if (n <= 0) {
        return false;
    }
    out.resize(n);

    switch (res->Type) {
        case Fast::TextureType::RGBA32bpp:
            if (size < (uint32_t)n * 4) {
                return false;
            }
            for (int i = 0; i < n; i++) {
                out[i] = Argb(d[i * 4], d[i * 4 + 1], d[i * 4 + 2], d[i * 4 + 3]);
            }
            break;
        case Fast::TextureType::RGBA16bpp:
            if (size < (uint32_t)n * 2) {
                return false;
            }
            for (int i = 0; i < n; i++) {
                const uint16_t v = (uint16_t)((d[i * 2] << 8) | d[i * 2 + 1]);
                const uint32_t r = ((v >> 11) & 0x1F) * 255 / 31;
                const uint32_t g = ((v >> 6) & 0x1F) * 255 / 31;
                const uint32_t b = ((v >> 1) & 0x1F) * 255 / 31;
                out[i] = Argb(r, g, b, (v & 1) ? 255 : 0);
            }
            break;
        case Fast::TextureType::GrayscaleAlpha16bpp:
            if (size < (uint32_t)n * 2) {
                return false;
            }
            for (int i = 0; i < n; i++) {
                out[i] = Argb(d[i * 2], d[i * 2], d[i * 2], d[i * 2 + 1]);
            }
            break;
        case Fast::TextureType::GrayscaleAlpha8bpp:
            if (size < (uint32_t)n) {
                return false;
            }
            for (int i = 0; i < n; i++) {
                const uint32_t iv = (d[i] >> 4) * 17;
                const uint32_t av = (d[i] & 0xF) * 17;
                out[i] = Argb(iv, iv, iv, av);
            }
            break;
        case Fast::TextureType::GrayscaleAlpha4bpp:
            if (size < (uint32_t)(n + 1) / 2) {
                return false;
            }
            for (int i = 0; i < n; i++) {
                const uint8_t nib = (i & 1) ? (d[i / 2] & 0xF) : (d[i / 2] >> 4);
                const uint32_t iv = ((nib >> 1) & 7) * 255 / 7;
                out[i] = Argb(iv, iv, iv, (nib & 1) ? 255 : 0);
            }
            break;
        case Fast::TextureType::Grayscale8bpp:
            if (size < (uint32_t)n) {
                return false;
            }
            for (int i = 0; i < n; i++) {
                out[i] = Argb(d[i], d[i], d[i], d[i]);
            }
            break;
        case Fast::TextureType::Grayscale4bpp:
            if (size < (uint32_t)(n + 1) / 2) {
                return false;
            }
            for (int i = 0; i < n; i++) {
                const uint8_t nib = (i & 1) ? (d[i / 2] & 0xF) : (d[i / 2] >> 4);
                const uint32_t iv = nib * 17;
                out[i] = Argb(iv, iv, iv, iv);
            }
            break;
        default:
            return false;
    }

    outW = w;
    outH = h;
    return true;
}

std::mutex sIconMutex;

} // namespace

static RegisterShipInitFunc sThorInit(RegisterThorBridge);

// ---- JNI ----------------------------------------------------------------------------------------

#ifdef __ANDROID__
#include <jni.h>

extern "C" {

JNIEXPORT jint JNICALL Java_com_dishii_soh_ThorBridge_nativeSnapshotVersion(JNIEnv*, jclass) {
    return THOR_SNAPSHOT_VERSION;
}

// Copies the latest snapshot into `out` (must hold THOR_SNAPSHOT_SIZE ints). Returns the frame counter.
JNIEXPORT jint JNICALL Java_com_dishii_soh_ThorBridge_nativeGetSnapshot(JNIEnv* env, jclass, jintArray out) {
    if (out == nullptr || env->GetArrayLength(out) < THOR_SNAPSHOT_SIZE) {
        return -1;
    }
    std::array<int32_t, THOR_SNAPSHOT_SIZE> copy;
    {
        std::lock_guard<std::mutex> lock(sSnapMutex);
        copy = sSnapshot;
    }
    env->SetIntArrayRegion(out, 0, THOR_SNAPSHOT_SIZE, reinterpret_cast<const jint*>(copy.data()));
    return copy[TS_FRAME];
}

JNIEXPORT void JNICALL Java_com_dishii_soh_ThorBridge_nativeCommand(JNIEnv*, jclass, jint type, jint a, jint b) {
    std::lock_guard<std::mutex> lock(sCmdMutex);
    if (sCommands.size() < 64) {
        sCommands.push_back({ type, a, b });
    }
}

JNIEXPORT jint JNICALL Java_com_dishii_soh_ThorBridge_nativeGetCVarInt(JNIEnv* env, jclass, jstring name, jint def) {
    const char* n = env->GetStringUTFChars(name, nullptr);
    const int v = CVarGetInteger(n, def);
    env->ReleaseStringUTFChars(name, n);
    return v;
}

JNIEXPORT void JNICALL Java_com_dishii_soh_ThorBridge_nativeSetCVarInt(JNIEnv* env, jclass, jstring name, jint value) {
    const char* n = env->GetStringUTFChars(name, nullptr);
    std::string sn(n);
    env->ReleaseStringUTFChars(name, n);
    CVarSetInteger(sn.c_str(), value);
    Ship::Context::GetInstance()->GetWindow()->GetGui()->SaveConsoleVariablesNextFrame();
    // Hooks that depend on this CVar get (re)registered on the game thread next frame.
    std::lock_guard<std::mutex> lock(sCmdMutex);
    sPendingCVarInits.push_back(sn);
}

JNIEXPORT jint JNICALL Java_com_dishii_soh_ThorBridge_nativeIconCount(JNIEnv*, jclass) {
    return (jint)ARRAY_COUNT(gItemIcons);
}

// Returns {width, height, argb...} for gItemIcons[index], or null if unavailable.
// Indices match ITEM_* ids (0x00-0x81), see z_inventory.c.
JNIEXPORT jintArray JNICALL Java_com_dishii_soh_ThorBridge_nativeGetItemIcon(JNIEnv* env, jclass, jint index) {
    if (index < 0 || index >= (jint)ARRAY_COUNT(gItemIcons)) {
        return nullptr;
    }
    std::lock_guard<std::mutex> lock(sIconMutex);
    int w = 0, h = 0;
    std::vector<uint32_t> px;
    try {
        if (!DecodeTexture((const char*)gItemIcons[index], w, h, px)) {
            return nullptr;
        }
    } catch (...) {
        return nullptr;
    }
    jintArray arr = env->NewIntArray(2 + w * h);
    if (arr == nullptr) {
        return nullptr;
    }
    const jint dims[2] = { w, h };
    env->SetIntArrayRegion(arr, 0, 2, dims);
    env->SetIntArrayRegion(arr, 2, w * h, reinterpret_cast<const jint*>(px.data()));
    return arr;
}

// Generic texture export by OTR path (e.g. "__OTR__textures/icon_item_static/gItemIconBowTex").
JNIEXPORT jintArray JNICALL Java_com_dishii_soh_ThorBridge_nativeGetTexture(JNIEnv* env, jclass, jstring path) {
    const char* p = env->GetStringUTFChars(path, nullptr);
    std::string sp(p);
    env->ReleaseStringUTFChars(path, p);
    std::lock_guard<std::mutex> lock(sIconMutex);
    int w = 0, h = 0;
    std::vector<uint32_t> px;
    try {
        if (!DecodeTexture(sp.c_str(), w, h, px)) {
            return nullptr;
        }
    } catch (...) {
        return nullptr;
    }
    jintArray arr = env->NewIntArray(2 + w * h);
    if (arr == nullptr) {
        return nullptr;
    }
    const jint dims[2] = { w, h };
    env->SetIntArrayRegion(arr, 0, 2, dims);
    env->SetIntArrayRegion(arr, 2, w * h, reinterpret_cast<const jint*>(px.data()));
    return arr;
}

} // extern "C"
#endif
