package com.dishii.soh;

/** Static Ocarina of Time tables used by the companion screen. */
public final class GameData {
    private GameData() {}

    public static final int AGE_ANY = 0, AGE_CHILD = 1, AGE_ADULT = 2;

    // ---- Items (ITEM_* ids, also the index into gItemIcons) ----

    public static final String[] ITEM_NAMES = {
            "Deku Stick", "Deku Nut", "Bombs", "Fairy Bow", "Fire Arrow", "Din's Fire",
            "Fairy Slingshot", "Fairy Ocarina", "Ocarina of Time", "Bombchu", "Hookshot", "Longshot",
            "Ice Arrow", "Farore's Wind", "Boomerang", "Lens of Truth", "Magic Beans", "Megaton Hammer",
            "Light Arrow", "Nayru's Love", "Empty Bottle", "Red Potion", "Green Potion", "Blue Potion",
            "Bottled Fairy", "Fish", "Lon Lon Milk", "Ruto's Letter", "Blue Fire", "Bug",
            "Big Poe", "Lon Lon Milk (Half)", "Poe", "Weird Egg", "Cucco", "Zelda's Letter",
            "Keaton Mask", "Skull Mask", "Spooky Mask", "Bunny Hood", "Goron Mask", "Zora Mask",
            "Gerudo Mask", "Mask of Truth", "SOLD OUT", "Pocket Egg", "Pocket Cucco", "Cojiro",
            "Odd Mushroom", "Odd Potion", "Poacher's Saw", "Broken Goron's Sword", "Prescription", "Eyeball Frog",
            "Eye Drops", "Claim Check", "Bow (Fire)", "Bow (Ice)", "Bow (Light)", "Kokiri Sword",
            "Master Sword", "Biggoron's Sword", "Deku Shield", "Hylian Shield", "Mirror Shield", "Kokiri Tunic",
            "Goron Tunic", "Zora Tunic", "Kokiri Boots", "Iron Boots", "Hover Boots", "Bullet Bag (30)",
            "Bullet Bag (40)", "Bullet Bag (50)", "Quiver (30)", "Quiver (40)", "Quiver (50)", "Bomb Bag (20)",
            "Bomb Bag (30)", "Bomb Bag (40)", "Goron's Bracelet", "Silver Gauntlets", "Golden Gauntlets", "Silver Scale",
            "Golden Scale", "Giant's Knife (Broken)", "Adult's Wallet", "Giant's Wallet", "Deku Seeds", "Fishing Pole",
            "Minuet of Forest", "Bolero of Fire", "Serenade of Water", "Requiem of Spirit", "Nocturne of Shadow", "Prelude of Light",
            "Zelda's Lullaby", "Epona's Song", "Saria's Song", "Sun's Song", "Song of Time", "Song of Storms",
            "Forest Medallion", "Fire Medallion", "Water Medallion", "Spirit Medallion", "Shadow Medallion", "Light Medallion",
            "Kokiri's Emerald", "Goron's Ruby", "Zora's Sapphire", "Stone of Agony", "Gerudo's Card", "Gold Skulltula Token",
            "Heart Container", "Piece of Heart", "Boss Key", "Compass", "Dungeon Map", "Small Key",
            "Small Magic Jar", "Large Magic Jar", "Piece of Heart", "", "", "",
            "", "", "", "",
    };

    public static final int ITEM_BOW = 0x03;
    public static final int ITEM_SLINGSHOT = 0x06;
    public static final int ITEM_OCARINA_FAIRY = 0x07;
    public static final int ITEM_OCARINA_TIME = 0x08;
    public static final int ITEM_SOLD_OUT = 0x2C;
    public static final int ITEM_SWORD_KOKIRI = 0x3B;
    public static final int ITEM_SHIELD_DEKU = 0x3E;
    public static final int ITEM_TUNIC_KOKIRI = 0x41;
    public static final int ITEM_BOOTS_KOKIRI = 0x44;
    public static final int ITEM_BULLET_BAG_30 = 0x47;
    public static final int ITEM_QUIVER_30 = 0x4A;
    public static final int ITEM_BOMB_BAG_20 = 0x4D;
    public static final int ITEM_BRACELET = 0x50;
    public static final int ITEM_SCALE_SILVER = 0x53;
    public static final int ITEM_SWORD_KNIFE = 0x55;
    public static final int ITEM_WALLET_ADULT = 0x56;
    public static final int ITEM_SONG_MINUET = 0x5A;
    public static final int ITEM_MEDALLION_FOREST = 0x66;
    public static final int ITEM_KOKIRI_EMERALD = 0x6C;
    public static final int ITEM_STONE_OF_AGONY = 0x6F;
    public static final int ITEM_GERUDO_CARD = 0x70;
    public static final int ITEM_SKULL_TOKEN = 0x71;
    public static final int ITEM_HEART_CONTAINER = 0x72;
    public static final int ITEM_HEART_PIECE = 0x73;
    public static final int ITEM_KEY_BOSS = 0x74;
    public static final int ITEM_COMPASS = 0x75;
    public static final int ITEM_DUNGEON_MAP = 0x76;
    public static final int ITEM_KEY_SMALL = 0x77;
    public static final int ITEM_SWORD_BGS = 0x3D;

    public static String itemName(int item) {
        if (item >= 0 && item < ITEM_NAMES.length) return ITEM_NAMES[item];
        return "";
    }

    // ---- Inventory slots ----

    public static final int SLOT_COUNT = 24;
    public static final int SLOT_TUNIC_KOKIRI = 0x18;

    /** gSlotAgeReqs */
    private static final int[] SLOT_AGE = {
            AGE_CHILD, AGE_ANY, AGE_ANY, AGE_ADULT, AGE_ADULT, AGE_ANY,
            AGE_CHILD, AGE_ANY, AGE_ANY, AGE_ADULT, AGE_ADULT, AGE_ANY,
            AGE_CHILD, AGE_ANY, AGE_CHILD, AGE_ADULT, AGE_ADULT, AGE_ANY,
            AGE_ANY, AGE_ANY, AGE_ANY, AGE_ANY, AGE_ADULT, AGE_CHILD,
    };

    public static int slotAgeReq(int slot) {
        return slot >= 0 && slot < SLOT_AGE.length ? SLOT_AGE[slot] : AGE_ANY;
    }

    /** gEquipAgeReqs[type][value] */
    private static final int[][] EQUIP_AGE = {
            {AGE_ADULT, AGE_CHILD, AGE_ADULT, AGE_ADULT},
            {AGE_ANY, AGE_CHILD, AGE_ANY, AGE_ADULT},
            {AGE_ADULT, AGE_ANY, AGE_ADULT, AGE_ADULT},
            {AGE_ANY, AGE_ANY, AGE_ADULT, AGE_ADULT},
    };

    public static int equipAgeReq(int type, int value) {
        return EQUIP_AGE[type][value];
    }

    /** Ammo shown for these slots (gAmmoItems). */
    public static boolean slotUsesAmmo(int slot) {
        return slot == 0 || slot == 1 || slot == 2 || slot == 3 || slot == 6 || slot == 8 || slot == 14;
    }

    // ---- Quest ----

    public static final int QUEST_MEDALLION_FOREST = 0;
    public static final int QUEST_SONG_MINUET = 6;
    public static final int QUEST_KOKIRI_EMERALD = 18;
    public static final int QUEST_STONE_OF_AGONY = 21;
    public static final int QUEST_GERUDO_CARD = 22;
    public static final int QUEST_SKULL_TOKEN = 23;

    public static final int[] MEDALLION_COLORS = {
            0xFF3CB44B, 0xFFE6463C, 0xFF4682F0, 0xFFF09A2C, 0xFFA05AD2, 0xFFF0E68C,
    };

    // ---- Songs ----
    // Note symbols: 'A' = A, 'v' = C-down, '>' = C-right, '<' = C-left, '^' = C-up

    public static final String[] SONG_NAMES = {
            "Minuet of Forest", "Bolero of Fire", "Serenade of Water", "Requiem of Spirit",
            "Nocturne of Shadow", "Prelude of Light", "Zelda's Lullaby", "Epona's Song",
            "Saria's Song", "Sun's Song", "Song of Time", "Song of Storms",
    };

    public static final String[] SONG_NOTES = {
            "A^<><>", "vAvA>v>v", "Av>><", "AvA>vA",
            "<>>A<>v", "^>^><^", "<^><^>", "^<>^<>",
            "v><v><", ">v^>v^", ">Av>Av", "Av^Av^",
    };

    public static final int[] SONG_COLORS = {
            0xFF3CB44B, 0xFFE6463C, 0xFF4682F0, 0xFFF09A2C,
            0xFFA05AD2, 0xFFF0E68C, 0xFFE8E8F0, 0xFFE8E8F0,
            0xFFE8E8F0, 0xFFE8E8F0, 0xFFE8E8F0, 0xFFE8E8F0,
    };

    public static int noteMask(char c) {
        switch (c) {
            case 'A': return ThorBridge.BTN_A;
            case 'v': return ThorBridge.BTN_CDOWN;
            case '>': return ThorBridge.BTN_CRIGHT;
            case '<': return ThorBridge.BTN_CLEFT;
            case '^': return ThorBridge.BTN_CUP;
            default: return 0;
        }
    }

    // ---- Dungeons (index into dungeonItems / dungeonKeys == dungeon scene id) ----

    public static final String[] DUNGEON_NAMES = {
            "Deku Tree", "Dodongo's Cavern", "Jabu-Jabu's Belly", "Forest Temple",
            "Fire Temple", "Water Temple", "Spirit Temple", "Shadow Temple",
            "Bottom of the Well", "Ice Cavern", "Ganon's Tower", "Gerudo Training Ground",
            "Thieves' Hideout", "Ganon's Castle",
    };

    /** Which of map / compass / boss key / small keys exist for each dungeon. */
    public static boolean dungeonHasMapCompass(int d) { return d <= 9; }
    public static boolean dungeonHasBossKey(int d) { return d >= 3 && d <= 7 || d == 10; }
    public static boolean dungeonHasSmallKeys(int d) { return d >= 3 && d <= 8 || d >= 10 && d <= 13; }

    /** Maps a scene id to its dungeon index, or -1. Boss rooms map to their dungeon. */
    public static int dungeonForScene(int scene) {
        if (scene >= 0x00 && scene <= 0x0D) return scene;
        if (scene == 0x0E || scene == 0x0F || scene == 0x1A) return 0x0A; // collapse sequence
        if (scene >= 0x11 && scene <= 0x18) return scene - 0x11;
        if (scene == 0x19 || scene == 0x4F) return 0x0A; // Ganondorf / Ganon
        return -1;
    }

    // ---- Scenes ----

    private static final String[] SCENE_NAMES = {
            "Inside the Deku Tree", "Dodongo's Cavern", "Inside Jabu-Jabu's Belly", "Forest Temple",
            "Fire Temple", "Water Temple", "Spirit Temple", "Shadow Temple",
            "Bottom of the Well", "Ice Cavern", "Ganon's Tower", "Gerudo Training Ground",
            "Thieves' Hideout", "Inside Ganon's Castle", "Ganon's Tower (Collapsing)", "Ganon's Castle (Collapsing)",
            "Treasure Box Shop", "Gohma's Lair", "King Dodongo's Lair", "Barinade's Lair",
            "Phantom Ganon's Lair", "Volvagia's Lair", "Morpha's Lair", "Twinrova's Lair",
            "Bongo Bongo's Lair", "Ganondorf's Lair", "Tower Collapse Exterior", "Market Entrance",
            "Market Entrance", "Market Entrance", "Back Alley", "Back Alley",
            "Market", "Market", "Market", "Temple of Time Exterior",
            "Temple of Time Exterior", "Temple of Time Exterior", "Know-It-All Brothers' House", "Twins' House",
            "Mido's House", "Saria's House", "Carpenter Boss's House", "Back Alley House",
            "Bazaar", "Kokiri Shop", "Goron Shop", "Zora Shop",
            "Kakariko Potion Shop", "Market Potion Shop", "Bombchu Shop", "Happy Mask Shop",
            "Link's House", "Dog Lady's House", "Stable", "Impa's House",
            "Lakeside Laboratory", "Carpenters' Tent", "Gravekeeper's Hut", "Great Fairy's Fountain",
            "Fairy's Fountain", "Great Fairy's Fountain", "Grottos", "Redead Grave",
            "Fairy Fountain Grave", "Royal Family's Tomb", "Shooting Gallery", "Temple of Time",
            "Chamber of the Sages", "Castle Courtyard", "Castle Courtyard", "Cutscene Map",
            "Windmill & Dampe's Grave", "Fishing Pond", "Castle Courtyard", "Bombchu Bowling Alley",
            "Lon Lon Ranch House & Tower", "Market Guard House", "Granny's Potion Shop", "Ganon's Arena",
            "House of Skulltula", "Hyrule Field", "Kakariko Village", "Graveyard",
            "Zora's River", "Kokiri Forest", "Sacred Forest Meadow", "Lake Hylia",
            "Zora's Domain", "Zora's Fountain", "Gerudo Valley", "Lost Woods",
            "Desert Colossus", "Gerudo's Fortress", "Haunted Wasteland", "Hyrule Castle",
            "Death Mountain Trail", "Death Mountain Crater", "Goron City", "Lon Lon Ranch",
            "Outside Ganon's Castle",
    };

    public static String sceneName(int scene, boolean adult) {
        if (scene == 0x5F && adult) return "Outside Ganon's Castle";
        if (scene >= 0 && scene < SCENE_NAMES.length) return SCENE_NAMES[scene];
        return "Test Map";
    }

    // ---- File name charset ----

    public static char filenameChar(int c) {
        c &= 0xFF;
        if (c <= 0x09) return (char) ('0' + c);
        if (c >= 0x0A && c <= 0x23) return (char) ('A' + c - 0x0A);
        if (c >= 0x24 && c <= 0x3D) return (char) ('a' + c - 0x24);
        if (c == 0x3E || c == 0xDF) return ' ';
        if (c == 0x3F) return '-';
        if (c == 0x40) return '.';
        return 0;
    }
}
