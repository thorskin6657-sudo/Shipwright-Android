#pragma once

// Thor Dual Screen bridge.
// Game thread publishes a snapshot of save/play state every frame and drains a
// command queue filled by the Android companion screen (second display).
// Everything that touches game memory runs on the game thread; JNI calls only
// copy the snapshot or enqueue commands.

#define THOR_SNAPSHOT_VERSION 1
#define THOR_SNAPSHOT_SIZE 160

// Snapshot layout (int array). Keep in sync with ThorState.java.
enum ThorSnap {
    TS_VERSION = 0,
    TS_FLAGS = 1, // see ThorFlags
    TS_SCENE = 2,
    TS_ROOM = 3,
    TS_HEALTH = 4,          // 1/16 hearts
    TS_HEALTH_CAPACITY = 5, // 1/16 hearts
    TS_MAGIC = 6,
    TS_MAGIC_CAPACITY = 7,
    TS_RUPEES = 8,
    TS_WALLET_UPG = 9,
    TS_DAY_TIME = 10,
    TS_EQUIPS = 11,        // gSaveContext.equips.equipment (current)
    TS_INV_EQUIPMENT = 12, // gSaveContext.inventory.equipment (owned)
    TS_UPGRADES = 13,
    TS_QUEST_ITEMS = 14,
    TS_GS_TOKENS = 15,
    TS_DOUBLE_DEFENSE = 16,
    TS_BGS_FLAG = 17,
    TS_SWORD_HEALTH = 18,
    TS_BUTTON_ITEMS = 19,  // 8 entries: B, C-L, C-D, C-R, D-U, D-D, D-L, D-R
    TS_CBUTTON_SLOTS = 27, // 7 entries
    TS_ITEMS = 34,         // 24 entries
    TS_AMMO = 58,          // 16 entries
    TS_DUNGEON_ITEMS = 74, // 20 entries
    TS_DUNGEON_KEYS = 94,  // 19 entries
    TS_FILE_NUM = 113,
    TS_POS_X = 114,
    TS_POS_Y = 115,
    TS_POS_Z = 116,
    TS_YAW = 117,
    TS_DEATHS = 118,
    TS_DPAD_EQUIPS = 119,
    TS_FRAME = 120,
    TS_SCENE_MQ = 121,
    TS_STATE_FLAGS1 = 122,
    TS_LINK_AGE = 123,
    TS_PAUSE_STATE = 124,
    TS_MSG_MODE = 125,
    TS_ASSIGNABLE_TUNICS = 126,
    TS_SEPARATE_ARROWS = 127,
    TS_TIMELESS_EQUIP = 128,
    TS_NAME = 129, // 8 entries, raw OoT filename chars
    TS_LAST_RESULT = 137,
    TS_LAST_RESULT_SEQ = 138,
    TS_STATE_FLAGS2 = 139,
    TS_END = 140
};

enum ThorFlags {
    TF_SAVE_LOADED = 1 << 0,
    TF_PAUSED = 1 << 1,
    TF_BLOCKING_CS = 1 << 2,
    TF_ADULT = 1 << 3,
    TF_NIGHT = 1 << 4,
    TF_CAN_EQUIP = 1 << 5,
    TF_IN_DUNGEON = 1 << 6,
};

enum ThorCmd {
    TC_NONE = 0,
    TC_ASSIGN_SLOT = 1, // a = inventory slot (0-23, or 0x18-0x1D tunic/boots), b = button 1..7
    TC_EQUIP = 2,       // a = equip type 0-3, b = equip value 1-3
    TC_PRESS = 3,       // a = button mask, b = frames to hold
    TC_CLEAR_BUTTON = 4 // a = button 1..7
};

enum ThorResult {
    TR_OK = 0,
    TR_NOT_IN_GAME = 1,
    TR_BUSY = 2,
    TR_NOT_OWNED = 3,
    TR_WRONG_AGE = 4,
    TR_BAD_ARGS = 5,
};
