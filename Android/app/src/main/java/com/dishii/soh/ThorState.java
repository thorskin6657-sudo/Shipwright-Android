package com.dishii.soh;

import java.util.Arrays;

/** Read-only view of the native snapshot. Indices mirror ThorBridge.h (ThorSnap). */
public final class ThorState {
    public static final int VERSION = 1;

    static final int TS_VERSION = 0;
    static final int TS_FLAGS = 1;
    static final int TS_SCENE = 2;
    static final int TS_ROOM = 3;
    static final int TS_HEALTH = 4;
    static final int TS_HEALTH_CAPACITY = 5;
    static final int TS_MAGIC = 6;
    static final int TS_MAGIC_CAPACITY = 7;
    static final int TS_RUPEES = 8;
    static final int TS_WALLET_UPG = 9;
    static final int TS_DAY_TIME = 10;
    static final int TS_EQUIPS = 11;
    static final int TS_INV_EQUIPMENT = 12;
    static final int TS_UPGRADES = 13;
    static final int TS_QUEST_ITEMS = 14;
    static final int TS_GS_TOKENS = 15;
    static final int TS_DOUBLE_DEFENSE = 16;
    static final int TS_BGS_FLAG = 17;
    static final int TS_SWORD_HEALTH = 18;
    static final int TS_BUTTON_ITEMS = 19;
    static final int TS_CBUTTON_SLOTS = 27;
    static final int TS_ITEMS = 34;
    static final int TS_AMMO = 58;
    static final int TS_DUNGEON_ITEMS = 74;
    static final int TS_DUNGEON_KEYS = 94;
    static final int TS_FILE_NUM = 113;
    static final int TS_POS_X = 114;
    static final int TS_POS_Y = 115;
    static final int TS_POS_Z = 116;
    static final int TS_YAW = 117;
    static final int TS_DEATHS = 118;
    static final int TS_DPAD_EQUIPS = 119;
    static final int TS_FRAME = 120;
    static final int TS_SCENE_MQ = 121;
    static final int TS_STATE_FLAGS1 = 122;
    static final int TS_LINK_AGE = 123;
    static final int TS_PAUSE_STATE = 124;
    static final int TS_MSG_MODE = 125;
    static final int TS_ASSIGNABLE_TUNICS = 126;
    static final int TS_SEPARATE_ARROWS = 127;
    static final int TS_TIMELESS_EQUIP = 128;
    static final int TS_NAME = 129;
    static final int TS_LAST_RESULT = 137;
    static final int TS_LAST_RESULT_SEQ = 138;
    static final int TS_STATE_FLAGS2 = 139;

    static final int TS_MAP_REVISION = 140, TS_MAP_FLAGS = 141, TS_MAP_X = 142,
            TS_MAP_Y = 143, TS_MAP_ROOM = 144, TS_MAP_FLOOR = 145;

    public int mapRevision() { return raw[TS_MAP_REVISION]; }
    public boolean hasLiveMap() { return (raw[TS_MAP_FLAGS] & 1) != 0; }
    public boolean mapMirrored() { return (raw[TS_MAP_FLAGS] & 2) != 0; }
    public float mapX() { return raw[TS_MAP_X] / 100000f; }
    public float mapY() { return raw[TS_MAP_Y] / 100000f; }
    public String mapFloor() {
        int floor = raw[TS_MAP_FLOOR];
        return floor == 0 ? "" : floor <= 8 ? (9 - floor) + "F" : "B" + (floor - 8);
    }

    static final int TF_SAVE_LOADED = 1;
    static final int TF_PAUSED = 1 << 1;
    static final int TF_BLOCKING_CS = 1 << 2;
    static final int TF_ADULT = 1 << 3;
    static final int TF_NIGHT = 1 << 4;
    static final int TF_CAN_EQUIP = 1 << 5;

    static final int PLAYER_STATE2_OCARINA_PLAYING = 1 << 27;

    public static final int ITEM_NONE = 0xFF;
    public static final int SLOT_NONE = 0xFF;

    public final int[] raw = new int[ThorBridge.SNAPSHOT_SIZE];

    public boolean copyFrom(int[] src) {
        if (Arrays.equals(raw, src)) return false;
        System.arraycopy(src, 0, raw, 0, raw.length);
        return true;
    }

    public boolean valid() { return raw[TS_VERSION] == VERSION; }
    public int frame() { return raw[TS_FRAME]; }
    public boolean saveLoaded() { return valid() && (raw[TS_FLAGS] & TF_SAVE_LOADED) != 0; }
    public boolean paused() { return (raw[TS_FLAGS] & TF_PAUSED) != 0; }
    public boolean canEquip() { return (raw[TS_FLAGS] & TF_CAN_EQUIP) != 0; }
    public boolean isAdult() { return (raw[TS_FLAGS] & TF_ADULT) != 0; }
    public boolean isNight() { return (raw[TS_FLAGS] & TF_NIGHT) != 0; }
    public boolean ocarinaPlaying() { return (raw[TS_STATE_FLAGS2] & PLAYER_STATE2_OCARINA_PLAYING) != 0; }

    public int scene() { return raw[TS_SCENE]; }
    public int room() { return raw[TS_ROOM]; }
    public boolean sceneIsMq() { return raw[TS_SCENE_MQ] != 0; }
    public int health() { return raw[TS_HEALTH]; }
    public int healthCapacity() { return raw[TS_HEALTH_CAPACITY]; }
    public int magic() { return raw[TS_MAGIC]; }
    public int magicCapacity() { return raw[TS_MAGIC_CAPACITY]; }
    public int rupees() { return raw[TS_RUPEES]; }
    public int dayTime() { return raw[TS_DAY_TIME] & 0xFFFF; }
    public int gsTokens() { return raw[TS_GS_TOKENS]; }
    public boolean doubleDefense() { return raw[TS_DOUBLE_DEFENSE] != 0; }
    public int deaths() { return raw[TS_DEATHS]; }
    public int yaw() { return (short) raw[TS_YAW]; }
    public int posX() { return raw[TS_POS_X]; }
    public int posY() { return raw[TS_POS_Y]; }
    public int posZ() { return raw[TS_POS_Z]; }
    public boolean dpadEquips() { return raw[TS_DPAD_EQUIPS] != 0; }
    public boolean assignableTunics() { return raw[TS_ASSIGNABLE_TUNICS] != 0; }
    public boolean separateArrows() { return raw[TS_SEPARATE_ARROWS] != 0; }
    public boolean timeless() { return raw[TS_TIMELESS_EQUIP] != 0; }
    public int lastResult() { return raw[TS_LAST_RESULT]; }
    public int lastResultSeq() { return raw[TS_LAST_RESULT_SEQ]; }

    public int buttonItem(int i) { return raw[TS_BUTTON_ITEMS + i]; }
    public int cButtonSlot(int i) { return raw[TS_CBUTTON_SLOTS + i]; }
    public int item(int slot) { return raw[TS_ITEMS + slot]; }
    public int ammo(int slot) { return slot < 16 ? (byte) raw[TS_AMMO + slot] : 0; }
    public int dungeonItems(int dungeon) { return raw[TS_DUNGEON_ITEMS + dungeon]; }
    public int dungeonKeys(int dungeon) { return (byte) raw[TS_DUNGEON_KEYS + dungeon]; }
    public boolean hasBossKey(int dungeon) { return (dungeonItems(dungeon) & 1) != 0; }
    public boolean hasCompass(int dungeon) { return (dungeonItems(dungeon) & 2) != 0; }
    public boolean hasMap(int dungeon) { return (dungeonItems(dungeon) & 4) != 0; }

    public boolean questItem(int bit) { return (raw[TS_QUEST_ITEMS] & (1 << bit)) != 0; }
    public int heartPieces() { return (raw[TS_QUEST_ITEMS] >>> 28) & 0xF; }

    private static final int[] EQUIP_SHIFTS = {0, 4, 8, 12};

    /** Current equip value (1-based) for type 0 sword, 1 shield, 2 tunic, 3 boots. 0 = none. */
    public int currentEquip(int type) {
        return (raw[TS_EQUIPS] >> EQUIP_SHIFTS[type]) & 0xF;
    }

    /** Owned check for equipment; inv is 0-based (0 = Kokiri Sword / Deku Shield / ...). */
    public boolean ownsEquip(int type, int inv) {
        return (raw[TS_INV_EQUIPMENT] & (1 << (inv + EQUIP_SHIFTS[type]))) != 0;
    }

    public boolean ownsBrokenKnife() { return ownsEquip(0, 3); }
    public boolean hasBgs() { return raw[TS_BGS_FLAG] != 0; }

    // Upgrades: shifts {quiver 0, bomb 3, strength 6, scale 9, wallet 12, bullet 14, sticks 17, nuts 20}
    private static final int[] UPG_SHIFTS = {0, 3, 6, 9, 12, 14, 17, 20};
    private static final int[] UPG_MASKS = {0x7, 0x7, 0x7, 0x7, 0x3, 0x7, 0x7, 0x7};
    public static final int UPG_QUIVER = 0, UPG_BOMB_BAG = 1, UPG_STRENGTH = 2, UPG_SCALE = 3,
            UPG_WALLET = 4, UPG_BULLET_BAG = 5, UPG_STICKS = 6, UPG_NUTS = 7;

    public int upgrade(int upg) {
        return (raw[TS_UPGRADES] >>> UPG_SHIFTS[upg]) & UPG_MASKS[upg];
    }

    private static final int[][] CAPACITIES = {
            {0, 30, 40, 50}, {0, 20, 30, 40}, {0, 0, 0, 0}, {0, 0, 0, 0},
            {99, 200, 500, 999}, {0, 30, 40, 50}, {0, 10, 20, 30}, {0, 20, 30, 40},
    };

    public int capacity(int upg) {
        int v = upgrade(upg);
        int[] c = CAPACITIES[upg];
        return c[Math.min(v, c.length - 1)];
    }

    public int walletCapacity() { return capacity(UPG_WALLET); }

    public String playerName() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            char c = GameData.filenameChar(raw[TS_NAME + i]);
            if (c != 0) sb.append(c);
        }
        return sb.toString().trim();
    }

    /** "HH:MM" from dayTime (0x0000 = midnight, 0x8000 = noon). */
    public String clock() {
        int minutes = (int) ((long) dayTime() * 24 * 60 / 0x10000);
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }

    /** Returns 0 when the item fits the current age, otherwise the age that can use it. */
    public boolean slotAgeOk(int slot) {
        if (timeless()) return true;
        int req = GameData.slotAgeReq(slot);
        if (req == GameData.AGE_ANY) return true;
        return (req == GameData.AGE_ADULT) == isAdult();
    }

    public boolean equipAgeOk(int type, int value) {
        if (timeless()) return true;
        int req = GameData.equipAgeReq(type, value);
        if (req == GameData.AGE_ANY) return true;
        return (req == GameData.AGE_ADULT) == isAdult();
    }
}
