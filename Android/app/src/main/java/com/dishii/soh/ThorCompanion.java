package com.dishii.soh;

import android.app.Activity;
import android.app.Presentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/**
 * Owns the bottom-screen Presentation on dual-screen devices (AYN Thor / Thor Max).
 *
 * - Finds a secondary display (presentation category first, then any non-default display).
 * - Shows ThorScreenView on it, non-focusable so the gamepad keeps talking to the game.
 * - Polls the native snapshot ~20x/s and redraws only when something changed.
 * - Survives the display being turned off/on via DisplayManager callbacks.
 */
public final class ThorCompanion implements DisplayManager.DisplayListener, ThorIcons.Listener {
    private static final String TAG = "ThorCompanion";
    private static final long POLL_MS = 50;

    private final Activity activity;
    private final DisplayManager displayManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ThorState state = new ThorState();
    private final ThorIcons icons = new ThorIcons();
    private final int[] buffer = new int[ThorBridge.SNAPSHOT_SIZE];

    private CompanionPresentation presentation;
    private boolean running;
    private boolean mainMapHidden;
    private int mapRevision = -1;
    private Bitmap mapBitmap;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            tick();
            handler.postDelayed(this, POLL_MS);
        }
    };

    public ThorCompanion(Activity activity) {
        this.activity = activity;
        this.displayManager = (DisplayManager) activity.getSystemService(Context.DISPLAY_SERVICE);
    }

    public void start() {
        if (running) return;
        running = true;
        displayManager.registerDisplayListener(this, handler);
        showIfPossible();
        handler.post(poll);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(poll);
        displayManager.unregisterDisplayListener(this);
        dismiss();
    }

    public void destroy() {
        stop();
        icons.shutdown();
    }

    private void tick() {
        int frame = ThorBridge.getSnapshot(buffer);
        if (frame < 0) return;
        boolean changed = state.copyFrom(buffer);
        if (!mainMapHidden && state.valid()) {
            // Keep navigation on the companion; this cosmetic hides only the main-screen HUD map.
            ThorBridge.setCVar("gCosmetics.HUD.Minimap.PosType", 4);
            mainMapHidden = true;
        }
        if (state.mapRevision() != mapRevision) {
            int[] data = ThorBridge.nativeGetMap();
            if (data != null && data.length >= 3 && data[0] == state.mapRevision()) {
                mapRevision = data[0];
                int width = data[1], height = data[2];
                mapBitmap = width > 0 && height > 0 && data.length == 3 + width * height
                        ? Bitmap.createBitmap(data, 3, width, width, height, Bitmap.Config.ARGB_8888) : null;
            }
        }
        if (presentation != null) presentation.view.setMap(mapBitmap, mapRevision);
        if (state.saveLoaded() && !icons.isLoaded()) {
            icons.loadAsync(this);
        }
        if (changed && presentation != null) {
            presentation.view.onStateChanged();
        }
    }

    @Override
    public void onIconsLoaded() {
        if (presentation != null) presentation.view.invalidate();
    }

    private Display findCompanionDisplay() {
        Display[] displays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        for (Display d : displays) {
            if (d.getDisplayId() != Display.DEFAULT_DISPLAY && d.getState() != Display.STATE_OFF) return d;
        }
        for (Display d : displayManager.getDisplays()) {
            if (d.getDisplayId() != Display.DEFAULT_DISPLAY) return d;
        }
        return null;
    }

    private void showIfPossible() {
        if (activity.isFinishing()) return;
        Display target = findCompanionDisplay();
        if (target == null) {
            Log.i(TAG, "No secondary display; companion idle");
            dismiss();
            return;
        }
        if (presentation != null) {
            if (presentation.getDisplay().getDisplayId() == target.getDisplayId()) return;
            dismiss();
        }
        try {
            presentation = new CompanionPresentation(activity, target, state, icons);
            presentation.view.setMap(mapBitmap, mapRevision);
            presentation.show();
            Log.i(TAG, "Companion on display " + target.getDisplayId() + " (" + target.getName() + ")");
        } catch (WindowManager.InvalidDisplayException e) {
            Log.w(TAG, "Couldn't show on display " + target.getDisplayId(), e);
            presentation = null;
        }
    }

    private void dismiss() {
        if (presentation != null) {
            try {
                presentation.dismiss();
            } catch (Exception ignored) {
            }
            presentation = null;
        }
    }

    @Override
    public void onDisplayAdded(int displayId) {
        if (running) showIfPossible();
    }

    @Override
    public void onDisplayRemoved(int displayId) {
        if (presentation != null && presentation.getDisplay().getDisplayId() == displayId) {
            dismiss();
        }
        if (running) showIfPossible();
    }

    @Override
    public void onDisplayChanged(int displayId) {
        if (running && presentation == null) showIfPossible();
    }

    static final class CompanionPresentation extends Presentation {
        final ThorScreenView view;

        CompanionPresentation(Context outer, Display display, ThorState state, ThorIcons icons) {
            super(outer, display);
            view = new ThorScreenView(getContext(), state, icons);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Window w = getWindow();
            if (w != null) {
                // Keep key/gamepad focus on the game's activity; touches still reach us.
                w.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
            setContentView(view);
        }
    }
}
