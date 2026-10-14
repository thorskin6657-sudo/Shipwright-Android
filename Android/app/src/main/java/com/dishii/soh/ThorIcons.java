package com.dishii.soh;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pulls item icons out of the loaded OTR archives (so texture packs show up too)
 * and keeps them as Bitmaps. Loading happens once, on a background thread, after
 * a save is loaded (resources are guaranteed to be mounted by then).
 */
public final class ThorIcons {
    private static final String TAG = "ThorIcons";

    public interface Listener {
        void onIconsLoaded();
    }

    private final Bitmap[] icons = new Bitmap[0x82];
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean loading;
    private volatile boolean loaded;

    public Bitmap get(int item) {
        if (item < 0 || item >= icons.length) return null;
        return icons[item];
    }

    public boolean isLoaded() {
        return loaded;
    }

    public void loadAsync(final Listener listener) {
        if (loading || loaded || !ThorBridge.isAvailable()) return;
        loading = true;
        executor.execute(() -> {
            int count = Math.min(icons.length, ThorBridge.iconCount());
            int ok = 0;
            for (int i = 0; i < count; i++) {
                try {
                    int[] data = ThorBridge.itemIcon(i);
                    if (data == null || data.length < 2) continue;
                    int w = data[0], h = data[1];
                    if (w <= 0 || h <= 0 || data.length < 2 + w * h) continue;
                    Bitmap bmp = Bitmap.createBitmap(data, 2, w, w, h, Bitmap.Config.ARGB_8888);
                    icons[i] = bmp;
                    ok++;
                } catch (Throwable t) {
                    Log.w(TAG, "icon " + i + " failed", t);
                }
            }
            Log.i(TAG, "Loaded " + ok + "/" + count + " icons");
            loaded = ok > 0;
            loading = false;
            main.post(() -> {
                if (listener != null) listener.onIconsLoaded();
            });
        });
    }

    /** Drop everything (e.g. texture pack changed); next loadAsync re-reads. */
    public void reset() {
        if (loading) return;
        for (int i = 0; i < icons.length; i++) icons[i] = null;
        loaded = false;
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
