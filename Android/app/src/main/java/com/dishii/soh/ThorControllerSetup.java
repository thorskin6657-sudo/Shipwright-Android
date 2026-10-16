package com.dishii.soh;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.AtomicFile;
import android.util.Log;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** One-time migration of the Thor's duplicated LT/C-Up binding. */
final class ThorControllerSetup {
    private static final String APPLIED = "thorRightStickClickCUpV1";
    private static final String CLICK = "P0-B8-SDLB8";

    static void apply(Context context, SharedPreferences preferences) {
        if (preferences.getBoolean(APPLIED, false)) return;
        File file = new File(Environment.getExternalStorageDirectory(), "SOH/shipofharkinian.json");
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream output = null;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (FileInputStream input = atomic.openRead()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            }
            JSONObject root = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            if (!rewrite(root)) return; // No physical-controller configuration yet; retry on a later launch.
            output = atomic.startWrite();
            output.write(root.toString(4).getBytes(StandardCharsets.UTF_8));
            atomic.finishWrite(output);
            output = null;
            preferences.edit().putBoolean(APPLIED, true).apply();
            Log.i("ThorControllerSetup", "C-Up moved to right-stick click; LT targeting retained");
        } catch (Exception error) {
            if (output != null) atomic.failWrite(output);
            Log.w("ThorControllerSetup", "Controller migration deferred", error);
        }
    }

    static boolean rewrite(JSONObject root) throws Exception {
        JSONObject cvars = root.optJSONObject("CVars");
        JSONObject settings = cvars == null ? null : cvars.optJSONObject("gSettings");
        JSONObject controllers = settings == null ? null : settings.optJSONObject("Controllers");
        JSONObject port = controllers == null ? null : controllers.optJSONObject("Port1");
        JSONObject buttons = port == null ? null : port.optJSONObject("Buttons");
        JSONObject mappings = controllers == null ? null : controllers.optJSONObject("ButtonMappings");
        if (buttons == null || mappings == null) return false;
        String old = buttons.optString("CUpButtonMappingIds", "");
        for (String id : old.split(",")) {
            JSONObject mapping = mappings.optJSONObject(id);
            if (mapping != null && mapping.optInt("Bitmask") == 8 &&
                    "SDLAxisDirectionToButtonMapping".equals(mapping.optString("ButtonMappingClass")) &&
                    mapping.optInt("SDLControllerAxis", -1) == 4) {
                mappings.remove(id);
            }
        }
        JSONObject click = new JSONObject();
        click.put("Bitmask", 8);
        click.put("ButtonMappingClass", "SDLButtonToButtonMapping");
        click.put("SDLControllerButton", 8);
        mappings.put(CLICK, click);
        buttons.put("CUpButtonMappingIds", CLICK + ",");
        return true;
    }
}
