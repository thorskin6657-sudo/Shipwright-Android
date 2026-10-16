package com.dishii.soh;

import android.util.Log;

/**
 * JNI surface of soh/soh/Enhancements/ThorDualScreen/ThorBridge.cpp.
 * All calls are cheap and safe from the UI thread except the icon getters,
 * which decode textures and should run on a background thread.
 */
public final class ThorBridge {
    private static final String TAG = "ThorBridge";

    public static final int SNAPSHOT_SIZE = 256;

    // Commands (ThorCmd)
    public static final int CMD_ASSIGN_SLOT = 1;
    public static final int CMD_EQUIP = 2;
    public static final int CMD_PRESS = 3;
    public static final int CMD_CLEAR_BUTTON = 4;

    // Results (ThorResult)
    public static final int RESULT_OK = 0;
    public static final int RESULT_NOT_IN_GAME = 1;
    public static final int RESULT_BUSY = 2;
    public static final int RESULT_NOT_OWNED = 3;
    public static final int RESULT_WRONG_AGE = 4;
    public static final int RESULT_BAD_ARGS = 5;

    // N64 button masks (libultraship controller.h)
    public static final int BTN_CRIGHT = 0x0001;
    public static final int BTN_CLEFT = 0x0002;
    public static final int BTN_CDOWN = 0x0004;
    public static final int BTN_CUP = 0x0008;
    public static final int BTN_R = 0x0010;
    public static final int BTN_L = 0x0020;
    public static final int BTN_DRIGHT = 0x0100;
    public static final int BTN_DLEFT = 0x0200;
    public static final int BTN_DDOWN = 0x0400;
    public static final int BTN_DUP = 0x0800;
    public static final int BTN_START = 0x1000;
    public static final int BTN_Z = 0x2000;
    public static final int BTN_B = 0x4000;
    public static final int BTN_A = 0x8000;

    /** Masks for buttonItems[1..7]: C-L, C-D, C-R, D-U, D-D, D-L, D-R. Index 0 is B. */
    public static final int[] BUTTON_MASKS = {
            BTN_B, BTN_CLEFT, BTN_CDOWN, BTN_CRIGHT, BTN_DUP, BTN_DDOWN, BTN_DLEFT, BTN_DRIGHT
    };

    private static boolean sAvailable = true;

    private ThorBridge() {}

    static native int[] nativeGetMap();
    static native int nativeSnapshotVersion();
    static native int nativeGetSnapshot(int[] out);
    static native void nativeCommand(int type, int a, int b);
    static native int nativeGetCVarInt(String name, int def);
    static native void nativeSetCVarInt(String name, int value);
    static native int nativeIconCount();
    static native int[] nativeGetItemIcon(int index);
    static native int[] nativeGetTexture(String path);

    public static boolean isAvailable() {
        return sAvailable;
    }

    private static void markUnavailable(Throwable t) {
        if (sAvailable) {
            Log.e(TAG, "Native bridge unavailable", t);
        }
        sAvailable = false;
    }

    /** Fills out with the latest snapshot. Returns the frame counter, or -1 if not ready. */
    public static int getSnapshot(int[] out) {
        if (!sAvailable) return -1;
        try {
            return nativeGetSnapshot(out);
        } catch (UnsatisfiedLinkError e) {
            markUnavailable(e);
            return -1;
        }
    }

    public static void command(int type, int a, int b) {
        if (!sAvailable) return;
        try {
            nativeCommand(type, a, b);
        } catch (UnsatisfiedLinkError e) {
            markUnavailable(e);
        }
    }

    public static void assignSlot(int slot, int button) {
        command(CMD_ASSIGN_SLOT, slot, button);
    }

    public static void equip(int type, int value) {
        command(CMD_EQUIP, type, value);
    }

    public static void press(int mask, int frames) {
        command(CMD_PRESS, mask, frames);
    }

    public static void clearButton(int button) {
        command(CMD_CLEAR_BUTTON, button, 0);
    }

    public static int getCVar(String name, int def) {
        if (!sAvailable) return def;
        try {
            return nativeGetCVarInt(name, def);
        } catch (UnsatisfiedLinkError e) {
            markUnavailable(e);
            return def;
        }
    }

    public static void setCVar(String name, int value) {
        if (!sAvailable) return;
        try {
            nativeSetCVarInt(name, value);
        } catch (UnsatisfiedLinkError e) {
            markUnavailable(e);
        }
    }

    public static int iconCount() {
        if (!sAvailable) return 0;
        try {
            return nativeIconCount();
        } catch (UnsatisfiedLinkError e) {
            markUnavailable(e);
            return 0;
        }
    }

    /** {width, height, argb...} or null. Call off the UI thread. */
    public static int[] itemIcon(int index) {
        if (!sAvailable) return null;
        try {
            return nativeGetItemIcon(index);
        } catch (UnsatisfiedLinkError e) {
            markUnavailable(e);
            return null;
        }
    }

    /** {width, height, argb...} or null. Call off the UI thread. */
    public static int[] texture(String otrPath) {
        if (!sAvailable) return null;
        try {
            return nativeGetTexture(otrPath);
        } catch (UnsatisfiedLinkError e) {
            markUnavailable(e);
            return null;
        }
    }
}
