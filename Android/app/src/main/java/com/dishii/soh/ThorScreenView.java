package com.dishii.soh;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * The bottom-screen UI. Everything is drawn on a Canvas so it scales to whatever
 * the second display reports (Thor bottom panel is ~1240x1080).
 *
 * Tabs:
 *   GEAR  - item grid, equipment, B/C(/D-pad) targets. Tap or drag an item onto a
 *           button to assign it, tap equipment to equip, tap a button to use it,
 *           long-press a button to clear it.
 *   QUEST - medallions, stones, songs (tap a song while the ocarina is out to play it).
 *   MAP   - live map with zoom/pan. TRACKER - spoiler-free area collectible counts.
 */
public class ThorScreenView extends View {

    // ---- Palette ----
    private static final int BG_TOP = 0xFF183326;
    private static final int BG_BOTTOM = 0xFF081A13;
    private static final int PANEL = 0xFF203B2B;
    private static final int PANEL_EDGE = 0xFF6B7951;
    private static final int GOLD = 0xFFE7C976;
    private static final int GOLD_DIM = 0xFF9F8B52;
    private static final int TEXT = 0xFFF3E9CF;
    private static final int TEXT_DIM = 0xFFA9B29A;
    private static final int SELECT = 0xFFA7E28A;
    private static final int C_YELLOW = 0xFFF6D43C;
    private static final int A_BLUE = 0xFF4C8DF6;
    private static final int B_GREEN = 0xFF3FBF5F;
    private static final int D_GREY = 0xFFB8C0CC;
    private static final int HEART_RED = 0xFFE5383B;
    private static final int MAGIC_GREEN = 0xFF2BD96B;

    private static final int TAB_GEAR = 0, TAB_QUEST = 1, TAB_MAP = 2, TAB_TRACKER = 3;
    private static final String[] TAB_NAMES = {"GEAR", "QUEST", "MAP", "TRACKER"};

    private final ThorState state;
    private final ThorIcons icons;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint();
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF tmpF = new RectF();
    private final Rect tmp = new Rect();

    private Bitmap mapBitmap;
    private int mapRevision = -1;
    private boolean dungeonTable;
    private final RectF mapToggle = new RectF();
    private final RectF mapViewport = new RectF();
    private final RectF[] mapControls = {new RectF(), new RectF(), new RectF()};
    private final RectF trackerViewport = new RectF();
    private final ScaleGestureDetector mapScale;
    private float mapZoom = 1, mapPanX, mapPanY;
    private float lastTouchX, lastTouchY, trackerScroll;
    private boolean mapTouch, trackerTouch;

    private void zoomMap(float next, float focusX, float focusY) {
        next = Math.max(1, Math.min(6, next));
        float ratio = next / mapZoom;
        float fx = focusX - mapViewport.centerX(), fy = focusY - mapViewport.centerY();
        mapPanX = (mapPanX - fx) * ratio + fx;
        mapPanY = (mapPanY - fy) * ratio + fy;
        mapZoom = next;
        if (mapZoom == 1) mapPanX = mapPanY = 0;
        invalidate();
    }

    public void setMap(Bitmap bitmap, int revision) {
        if (mapRevision != revision) {
            mapZoom = 1;
            mapPanX = mapPanY = 0;
        }
        mapBitmap = bitmap;
        mapRevision = revision;
    }

    private int tab = TAB_GEAR;
    private float u; // layout unit

    // Layout rects
    private final RectF header = new RectF();
    private final RectF content = new RectF();
    private final RectF tabBar = new RectF();
    private final RectF[] tabRects = new RectF[TAB_NAMES.length];
    private final RectF[] slotRects = new RectF[24];
    private final RectF[][] equipRects = new RectF[4][3];
    private final RectF[] upgradeRects = new RectF[4];
    private final RectF[] buttonRects = new RectF[8]; // 0 = B, 1..7 = C-L..D-R
    private final RectF[] songRects = new RectF[12];
    private final RectF ocarinaRect = new RectF();

    // Interaction
    private int selectedSlot = -1;     // inventory slot, or 0x18.. for tunic/boots
    private int downSlot = -1;
    private int downButton = -1;
    private int downEquipType = -1, downEquipValue = -1;
    private int downSong = -1;
    private boolean downOcarina;
    private boolean dragging;
    private float downX, downY, dragX, dragY;
    private boolean longPressFired;
    private int selectedSong = -1;

    private String toast;
    private long toastUntil;
    private int lastSeenResultSeq = -1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable longPress = this::onLongPress;
    private boolean songPlaying;

    public ThorScreenView(Context context, ThorState state, ThorIcons icons) {
        super(context);
        this.state = state;
        this.icons = icons;
        mapScale = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                zoomMap(mapZoom * detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                return true;
            }
        });
        iconPaint.setFilterBitmap(false); // crisp pixel art
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        for (int i = 0; i < tabRects.length; i++) tabRects[i] = new RectF();
        for (int i = 0; i < slotRects.length; i++) slotRects[i] = new RectF();
        for (int i = 0; i < 4; i++) for (int j = 0; j < 3; j++) equipRects[i][j] = new RectF();
        for (int i = 0; i < upgradeRects.length; i++) upgradeRects[i] = new RectF();
        for (int i = 0; i < buttonRects.length; i++) buttonRects[i] = new RectF();
        for (int i = 0; i < songRects.length; i++) songRects[i] = new RectF();
        setHapticFeedbackEnabled(true);
    }

    /** Called by the companion when a fresh snapshot arrives. */
    public void onStateChanged() {
        if (state.lastResultSeq() != lastSeenResultSeq) {
            if (lastSeenResultSeq != -1 && state.lastResult() != ThorBridge.RESULT_OK) {
                showToast(resultText(state.lastResult()));
            }
            lastSeenResultSeq = state.lastResultSeq();
        }
        invalidate();
    }

    private static String resultText(int r) {
        switch (r) {
            case ThorBridge.RESULT_NOT_IN_GAME: return "Load a file first";
            case ThorBridge.RESULT_BUSY: return "Can't change that right now";
            case ThorBridge.RESULT_NOT_OWNED: return "You don't have that yet";
            case ThorBridge.RESULT_WRONG_AGE: return "Wrong age for that";
            default: return "That didn't work";
        }
    }

    private void showToast(String s) {
        toast = s;
        toastUntil = SystemClock.uptimeMillis() + 1800;
        invalidate();
        handler.postDelayed(this::invalidate, 1900);
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        u = Math.min(w, h) / 100f;
        float headerH = h * 0.15f;
        float tabH = h * 0.09f;
        header.set(0, 0, w, headerH);
        tabBar.set(0, h - tabH, w, h);
        content.set(0, headerH, w, h - tabH);

        float tw = w / (float)TAB_NAMES.length;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            tabRects[i].set(i * tw + u, tabBar.top + u * 0.8f, (i + 1) * tw - u, tabBar.bottom - u * 0.8f);
        }
        layoutGear();
        layoutQuest();
    }

    private void layoutGear() {
        float pad = u * 2f;
        float buttonsH = content.height() * 0.22f;
        float gridBottom = content.bottom - buttonsH - pad;
        float gridTop = content.top + pad;
        float gridH = gridBottom - gridTop;

        // Item grid: 6 x 4 on the left ~62%
        float leftW = content.width() * 0.62f;
        float cell = Math.min((leftW - pad * 2) / 6f, gridH / 4f);
        float gx = content.left + pad + ((leftW - pad * 2) - cell * 6) / 2f;
        float gy = gridTop + (gridH - cell * 4) / 2f;
        for (int i = 0; i < 24; i++) {
            int col = i % 6, row = i / 6;
            slotRects[i].set(gx + col * cell, gy + row * cell, gx + (col + 1) * cell, gy + (row + 1) * cell);
            slotRects[i].inset(cell * 0.05f, cell * 0.05f);
        }

        // Equipment 3 x 4 + upgrades column on the right
        float rx = content.left + leftW + pad;
        float rw = content.right - pad - rx;
        float ecell = Math.min(rw / 4f, gridH / 4f);
        float ex = rx + (rw - ecell * 4) / 2f;
        float ey = gridTop + (gridH - ecell * 4) / 2f;
        for (int t = 0; t < 4; t++) {
            for (int v = 0; v < 3; v++) {
                equipRects[t][v].set(ex + (v + 1) * ecell, ey + t * ecell, ex + (v + 2) * ecell, ey + (t + 1) * ecell);
                equipRects[t][v].inset(ecell * 0.06f, ecell * 0.06f);
            }
            upgradeRects[t].set(ex, ey + t * ecell, ex + ecell, ey + (t + 1) * ecell);
            upgradeRects[t].inset(ecell * 0.12f, ecell * 0.12f);
        }

        // Button targets
        float by = content.bottom - buttonsH;
        float bh = buttonsH - pad;
        int count = 8;
        float bw = (content.width() - pad * 2) / count;
        float size = Math.min(bw, bh) * 0.92f;
        for (int i = 0; i < count; i++) {
            float cx = content.left + pad + bw * (i + 0.5f);
            float cy = by + bh / 2f;
            buttonRects[i].set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
        }
    }

    private void layoutQuest() {
        float pad = u * 2f;
        float leftW = content.width() * 0.44f;
        float rx = content.left + leftW + pad;
        float rw = content.right - pad - rx;
        float rowH = (content.height() - pad * 2) / 12f * 0.98f;
        float ry = content.top + pad;
        for (int i = 0; i < 12; i++) {
            songRects[i].set(rx, ry + i * rowH, rx + rw, ry + (i + 1) * rowH);
            songRects[i].inset(0, rowH * 0.06f);
        }
        float ob = Math.min(leftW * 0.3f, content.height() * 0.13f);
        ocarinaRect.set(content.left + pad, content.bottom - pad - ob, content.left + pad + ob * 2.6f, content.bottom - pad);
    }

    // ------------------------------------------------------------------ draw

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        paint.setShader(new LinearGradient(0, 0, 0, h, BG_TOP, BG_BOTTOM, Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.FILL);
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
        drawTriforce(c, getWidth() * 0.5f, header.centerY(), u * 3.2f, 0x24E7C976);

        if (!ThorBridge.isAvailable()) {
            drawCentered(c, "Companion bridge not available in this build", h / 2f, u * 4, TEXT_DIM);
            return;
        }
        if (!state.saveLoaded()) {
            drawIdle(c);
            return;
        }

        drawHeader(c);
        switch (tab) {
            case TAB_GEAR: drawGear(c); break;
            case TAB_QUEST: drawQuest(c); break;
            case TAB_MAP: drawMap(c); break;
            case TAB_TRACKER: drawTracker(c); break;
        }
        drawTabs(c);

        if (dragging && downSlot >= 0) {
            Bitmap b = icons.get(itemForSelectable(downSlot));
            float s = slotRects[0].width() * 1.1f;
            if (b != null) {
                tmpF.set(dragX - s / 2, dragY - s / 2, dragX + s / 2, dragY + s / 2);
                c.drawBitmap(b, null, tmpF, iconPaint);
            }
        }

        if (toast != null && SystemClock.uptimeMillis() < toastUntil) {
            textPaint.setTextSize(u * 3.6f);
            float tw = textPaint.measureText(toast);
            float cy = content.top + u * 6;
            tmpF.set(w / 2f - tw / 2 - u * 3, cy - u * 4, w / 2f + tw / 2 + u * 3, cy + u * 3);
            paint.setColor(0xE0101820);
            c.drawRoundRect(tmpF, u * 2, u * 2, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(u * 0.4f);
            paint.setColor(GOLD);
            c.drawRoundRect(tmpF, u * 2, u * 2, paint);
            paint.setStyle(Paint.Style.FILL);
            drawCentered(c, toast, cy + u * 1.2f, u * 3.6f, TEXT);
        }
    }

    private void drawIdle(Canvas c) {
        float h = getHeight();
        drawCentered(c, "Ship of Harkinian", h * 0.42f, u * 7, GOLD);
        drawCentered(c, "Dual Screen", h * 0.52f, u * 4.5f, TEXT);
        drawCentered(c, "Load a file to bring up your gear", h * 0.64f, u * 3.2f, TEXT_DIM);
    }

    private void drawHeader(Canvas c) {
        float pad = u * 2;
        // Hearts
        int cap = state.healthCapacity() / 16;
        int hp = state.health();
        float hs = u * 3.6f;
        float hx0 = pad, hy0 = header.top + pad * 0.8f;
        for (int i = 0; i < cap; i++) {
            int col = i % 10, row = i / 10;
            float x = hx0 + col * hs * 1.1f, y = hy0 + row * hs * 1.1f;
            int fill = Math.max(0, Math.min(16, hp - i * 16));
            drawHeart(c, x, y, hs, fill / 16f, state.doubleDefense());
        }
        // Magic bar
        int mcap = state.magicCapacity();
        if (mcap > 0) {
            float my = hy0 + hs * 2.3f;
            float mw = u * 0.6f * mcap;
            tmpF.set(hx0, my, hx0 + mw, my + u * 2f);
            paint.setColor(0xFF283D26);
            c.drawRoundRect(tmpF, u, u, paint);
            tmpF.right = hx0 + mw * (state.magic() / (float) mcap);
            paint.setColor(MAGIC_GREEN);
            c.drawRoundRect(tmpF, u, u, paint);
        }

        // Right side: area name, rupees, clock
        textPaint.setTextAlign(Paint.Align.RIGHT);
        textPaint.setTextSize(u * 3.6f);
        textPaint.setColor(TEXT);
        String area = GameData.sceneName(state.scene(), state.isAdult());
        float areaWidth = header.width() * 0.52f - pad;
        fitText(area, u * 3.6f, areaWidth);
        c.drawText(area, header.right - pad, header.top + u * 4.5f, textPaint);

        textPaint.setTextSize(u * 3.2f);
        int wallet = state.walletCapacity();
        textPaint.setColor(state.rupees() >= wallet ? 0xFF7CFC9A : TEXT);
        String rupees = state.rupees() + " / " + wallet;
        float rx = header.right - pad;
        c.drawText(rupees, rx, header.top + u * 12.2f, textPaint);
        float rw = textPaint.measureText(rupees);
        drawRupee(c, rx - rw - u * 3, header.top + u * 10.8f, u * 1.7f, walletColor());

        textPaint.setColor(state.isNight() ? 0xFF9DB4FF : GOLD);
        String clock = (state.isNight() ? "☾ " : "☀ ") + state.clock();
        c.drawText(clock, rx - rw - u * 7, header.top + u * 12.2f, textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);

        paint.setColor(PANEL_EDGE);
        c.drawRect(0, header.bottom - u * 0.3f, getWidth(), header.bottom, paint);
    }

    private int walletColor() {
        switch (state.upgrade(ThorState.UPG_WALLET)) {
            case 1: return 0xFF4C8DF6;
            case 2: return 0xFFE5383B;
            case 3: return 0xFFA05AD2;
            default: return 0xFF3FBF5F;
        }
    }

    private void drawTabs(Canvas c) {
        for (int i = 0; i < TAB_NAMES.length; i++) {
            boolean on = i == tab;
            paint.setColor(on ? 0xFF39583A : PANEL);
            c.drawRoundRect(tabRects[i], u * 2, u * 2, paint);
            if (on) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(u * 0.4f);
                paint.setColor(GOLD);
                c.drawRoundRect(tabRects[i], u * 2, u * 2, paint);
                paint.setStyle(Paint.Style.FILL);
            }
            drawCentered(c, TAB_NAMES[i], tabRects[i].centerY() + u * 1.4f, u * 3.6f, on ? GOLD : TEXT_DIM, tabRects[i].centerX());
        }
    }

    // ---- GEAR

    private void drawGear(Canvas c) {
        // Panels
        tmpF.set(slotRects[0].left - u, slotRects[0].top - u, slotRects[23].right + u, slotRects[23].bottom + u);
        drawPanel(c, tmpF);
        tmpF.set(upgradeRects[0].left - u * 1.5f, equipRects[0][0].top - u, equipRects[3][2].right + u, equipRects[3][2].bottom + u);
        drawPanel(c, tmpF);

        for (int s = 0; s < 24; s++) {
            int item = state.item(s);
            RectF r = slotRects[s];
            boolean sel = selectedSlot == s;
            drawSlotBg(c, r, sel);
            if (item == ThorState.ITEM_NONE) continue;
            boolean ok = state.slotAgeOk(s);
            drawItem(c, item, r, ok ? 255 : 90);
            if (GameData.slotUsesAmmo(s)) {
                drawAmmo(c, r, state.ammo(s), ammoCapacity(s));
            }
            drawEquippedMarker(c, r, s);
        }

        // Equipment
        for (int t = 0; t < 4; t++) {
            int cur = state.currentEquip(t);
            for (int v = 0; v < 3; v++) {
                RectF r = equipRects[t][v];
                boolean owned = state.ownsEquip(t, v) || (t == 0 && v == 2 && (state.hasBgs() || state.ownsBrokenKnife()));
                boolean equipped = cur == v + 1;
                int tunicSlot = (t >= 2) ? GameData.SLOT_TUNIC_KOKIRI + (t - 2) * 3 + v : -1;
                drawSlotBg(c, r, tunicSlot >= 0 && selectedSlot == tunicSlot);
                if (!owned) continue;
                int item = equipItem(t, v);
                drawItem(c, item, r, state.equipAgeOk(t, v + 1) ? 255 : 90);
                if (equipped) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(u * 0.6f);
                    paint.setColor(GOLD);
                    c.drawRoundRect(r, u * 1.4f, u * 1.4f, paint);
                    paint.setStyle(Paint.Style.FILL);
                }
                if (t == 0 && v == 2 && !state.hasBgs() && state.ownsBrokenKnife()) {
                    drawCorner(c, r, "BRK", 0xFFFF9090);
                }
            }
            // Upgrade column
            int upItem = upgradeItem(t);
            if (upItem >= 0) drawItem(c, upItem, upgradeRects[t], 220);
        }

        // Buttons
        int maxButton = state.dpadEquips() ? 8 : 4;
        for (int i = 0; i < 8; i++) {
            if (i >= maxButton) break;
            drawButtonTarget(c, i);
        }
        if (!state.dpadEquips()) {
            float x = buttonRects[4].left;
            textPaint.setTextSize(u * 2.6f);
            textPaint.setColor(TEXT_DIM);
            c.drawText("Turn on D-pad items for 4 more", x, buttonRects[4].centerY() - u * 1.2f, textPaint);
            c.drawText("touch slots  (tap here)", x, buttonRects[4].centerY() + u * 2.4f, textPaint);
        }
    }

    private int ammoCapacity(int slot) {
        switch (slot) {
            case 0: return state.capacity(ThorState.UPG_STICKS);
            case 1: return state.capacity(ThorState.UPG_NUTS);
            case 2: case 8: return slot == 8 ? 50 : state.capacity(ThorState.UPG_BOMB_BAG);
            case 3: return state.capacity(ThorState.UPG_QUIVER);
            case 6: return state.capacity(ThorState.UPG_BULLET_BAG);
            default: return 99;
        }
    }

    private int equipItem(int type, int v) {
        switch (type) {
            case 0:
                if (v == 2 && !state.hasBgs() && state.ownsBrokenKnife()) return GameData.ITEM_SWORD_KNIFE;
                return GameData.ITEM_SWORD_KOKIRI + v;
            case 1: return GameData.ITEM_SHIELD_DEKU + v;
            case 2: return GameData.ITEM_TUNIC_KOKIRI + v;
            default: return GameData.ITEM_BOOTS_KOKIRI + v;
        }
    }

    /** Upgrade icon for the left column: bag/quiver, bomb bag, strength, scale. */
    private int upgradeItem(int row) {
        switch (row) {
            case 0: {
                if (state.isAdult()) {
                    int q = state.upgrade(ThorState.UPG_QUIVER);
                    return q > 0 ? GameData.ITEM_QUIVER_30 + q - 1 : -1;
                }
                int b = state.upgrade(ThorState.UPG_BULLET_BAG);
                return b > 0 ? GameData.ITEM_BULLET_BAG_30 + b - 1 : -1;
            }
            case 1: {
                int b = state.upgrade(ThorState.UPG_BOMB_BAG);
                return b > 0 ? GameData.ITEM_BOMB_BAG_20 + b - 1 : -1;
            }
            case 2: {
                int s = state.upgrade(ThorState.UPG_STRENGTH);
                return s > 0 ? GameData.ITEM_BRACELET + s - 1 : -1;
            }
            default: {
                int s = state.upgrade(ThorState.UPG_SCALE);
                return s > 0 ? GameData.ITEM_SCALE_SILVER + s - 1 : -1;
            }
        }
    }

    private void drawEquippedMarker(Canvas c, RectF r, int slot) {
        int max = state.dpadEquips() ? 7 : 3;
        for (int i = 0; i < max; i++) {
            int bs = state.cButtonSlot(i);
            boolean match = bs == slot;
            // Magic arrows share the bow slot unless separated
            if (!match) continue;
            String label = buttonLabel(i + 1);
            drawCorner(c, r, label, i < 3 ? C_YELLOW : D_GREY);
            return;
        }
    }

    private static String buttonLabel(int button) {
        switch (button) {
            case 0: return "B";
            case 1: return "X";
            case 2: return "Y";
            case 3: return "RB";
            case 4: return "D↑";
            case 5: return "D↓";
            case 6: return "D←";
            default: return "D→";
        }
    }

    private void drawButtonTarget(Canvas c, int i) {
        RectF r = buttonRects[i];
        int color = i == 0 ? B_GREEN : (i <= 3 ? C_YELLOW : D_GREY);
        boolean hot = (dragging && r.contains(dragX, dragY)) || downButton == i;
        paint.setColor(0xFF0C2117);
        c.drawOval(r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(u * (hot ? 1.0f : 0.6f));
        paint.setColor(hot ? SELECT : color);
        c.drawOval(r, paint);
        paint.setStyle(Paint.Style.FILL);

        int item = state.buttonItem(i);
        if (item != ThorState.ITEM_NONE && item != 0) {
            tmpF.set(r);
            tmpF.inset(r.width() * 0.17f, r.height() * 0.17f);
            drawItem(c, item, tmpF, 255);
            if (i > 0) {
                int slot = state.cButtonSlot(i - 1);
                if (slot >= 0 && slot < 16 && GameData.slotUsesAmmo(slot)) {
                    drawAmmo(c, r, state.ammo(slot), ammoCapacity(slot));
                }
            }
        } else if (i == 0 && item == ThorState.ITEM_NONE) {
            // swordless
        }
        // Label badge
        float bs = r.width() * 0.32f;
        tmpF.set(r.left - bs * 0.15f, r.top - bs * 0.15f, r.left + bs * 0.85f, r.top + bs * 0.85f);
        paint.setColor(color);
        c.drawOval(tmpF, paint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(bs * (i == 3 ? 0.46f : 0.55f));
        textPaint.setColor(0xFF132016);
        c.drawText(buttonLabel(i), tmpF.centerX(), tmpF.centerY() + bs * 0.2f, textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    // ---- QUEST

    private void drawQuest(Canvas c) {
        float pad = u * 2;
        float leftW = content.width() * 0.44f;
        tmpF.set(content.left + pad, content.top + pad, content.left + leftW, content.bottom - pad);
        drawPanel(c, tmpF);

        // Medallions in a hexagon
        float cx = content.left + pad + (leftW - pad) / 2f;
        float cy = content.top + content.height() * 0.30f;
        float rad = Math.min(leftW, content.height()) * 0.20f;
        float ms = rad * 0.62f;
        // Light at top, then clockwise: forest, fire, water, shadow, spirit (OoT pause layout)
        int[] order = {5, 0, 1, 2, 4, 3};
        for (int k = 0; k < 6; k++) {
            int m = order[k];
            double ang = Math.toRadians(-90 + k * 60);
            float x = cx + (float) Math.cos(ang) * rad, y = cy + (float) Math.sin(ang) * rad;
            tmpF.set(x - ms / 2, y - ms / 2, x + ms / 2, y + ms / 2);
            boolean has = state.questItem(GameData.QUEST_MEDALLION_FOREST + m);
            paint.setColor(has ? (GameData.MEDALLION_COLORS[m] & 0x55FFFFFF) : 0x22FFFFFF);
            c.drawOval(tmpF, paint);
            if (has) drawItem(c, GameData.ITEM_MEDALLION_FOREST + m, tmpF, 255);
        }

        // Spiritual stones
        float sy = cy + rad + ms * 1.0f;
        float ss = ms * 0.9f;
        for (int i = 0; i < 3; i++) {
            float x = cx + (i - 1) * ss * 1.2f;
            tmpF.set(x - ss / 2, sy, x + ss / 2, sy + ss);
            drawSlotBg(c, tmpF, false);
            if (state.questItem(GameData.QUEST_KOKIRI_EMERALD + i)) {
                drawItem(c, GameData.ITEM_KOKIRI_EMERALD + i, tmpF, 255);
            }
        }

        // Agony, card, tokens, heart pieces
        float iy = sy + ss * 1.3f;
        float is = ss * 0.85f;
        float x0 = content.left + pad * 2;
        float step = (leftW - pad * 3) / 4f;
        drawQuestCell(c, x0, iy, is, GameData.ITEM_STONE_OF_AGONY, state.questItem(GameData.QUEST_STONE_OF_AGONY), null);
        drawQuestCell(c, x0 + step, iy, is, GameData.ITEM_GERUDO_CARD, state.questItem(GameData.QUEST_GERUDO_CARD), null);
        drawQuestCell(c, x0 + step * 2, iy, is, GameData.ITEM_SKULL_TOKEN, state.gsTokens() > 0, String.valueOf(state.gsTokens()));
        drawQuestCell(c, x0 + step * 3, iy, is, GameData.ITEM_HEART_PIECE, true, state.heartPieces() + "/4");

        // Ocarina shortcut
        int ocarinaButton = findOcarinaButton();
        boolean playing = state.ocarinaPlaying();
        paint.setColor(playing ? 0xFF1E4A35 : PANEL);
        c.drawRoundRect(ocarinaRect, u * 2, u * 2, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(u * 0.4f);
        paint.setColor(ocarinaButton >= 0 || playing ? GOLD : GOLD_DIM);
        c.drawRoundRect(ocarinaRect, u * 2, u * 2, paint);
        paint.setStyle(Paint.Style.FILL);
        int ocItem = state.item(7);
        if (ocItem != ThorState.ITEM_NONE) {
            tmpF.set(ocarinaRect.left + u, ocarinaRect.top + u, ocarinaRect.left + ocarinaRect.height() - u, ocarinaRect.bottom - u);
            drawItem(c, ocItem, tmpF, 255);
        }
        textPaint.setTextSize(u * 3f);
        textPaint.setColor(TEXT);
        String ocText = playing ? "Playing..." : (ocarinaButton >= 0 ? "Take out" : "Assign ocarina");
        float ocX = ocarinaRect.left + ocarinaRect.height() + u;
        fitText(ocText, u * 3f, ocarinaRect.right - ocX - u);
        c.drawText(ocText, ocX, ocarinaRect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2, textPaint);

        // Songs
        for (int i = 0; i < 12; i++) drawSong(c, i, playing);
    }

    private void drawQuestCell(Canvas c, float x, float y, float s, int item, boolean has, String label) {
        tmpF.set(x, y, x + s, y + s);
        drawSlotBg(c, tmpF, false);
        if (has) drawItem(c, item, tmpF, 255);
        if (label != null) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(u * 2.8f);
            textPaint.setColor(TEXT);
            c.drawText(label, x + s / 2, y + s + u * 3.2f, textPaint);
            textPaint.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawSong(Canvas c, int i, boolean playing) {
        RectF r = songRects[i];
        boolean has = state.questItem(GameData.QUEST_SONG_MINUET + i);
        boolean sel = selectedSong == i;
        paint.setColor(sel ? 0xFF39583A : PANEL);
        c.drawRoundRect(r, u * 1.4f, u * 1.4f, paint);
        if (sel || (playing && has)) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(u * 0.35f);
            paint.setColor(sel ? SELECT : GOLD_DIM);
            c.drawRoundRect(r, u * 1.4f, u * 1.4f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        // Note icon
        float h = r.height();
        float ns = h * 0.7f;
        tmpF.set(r.left + u, r.centerY() - ns / 2, r.left + u + ns, r.centerY() + ns / 2);
        if (has) {
            Bitmap b = icons.get(GameData.ITEM_SONG_MINUET + i);
            if (b != null) {
                iconPaint.setColorFilter(new android.graphics.PorterDuffColorFilter(GameData.SONG_COLORS[i],
                        android.graphics.PorterDuff.Mode.MULTIPLY));
                c.drawBitmap(b, null, tmpF, iconPaint);
                iconPaint.setColorFilter(null);
            } else {
                paint.setColor(GameData.SONG_COLORS[i]);
                c.drawCircle(tmpF.centerX(), tmpF.centerY(), ns * 0.3f, paint);
            }
        }
        textPaint.setTextSize(h * 0.30f);
        textPaint.setColor(has ? TEXT : TEXT_DIM);
        String name = has ? GameData.SONG_NAMES[i] : "???";
        float tx = tmpF.right + u * 1.2f;
        fitText(name, h * 0.30f, r.right - tx - u);
        c.drawText(name, tx, r.top + h * 0.39f, textPaint);

        // Notes on the right
        if (has || sel) {
            String notes = GameData.SONG_NOTES[i];
            float noteS = h * 0.33f;
            float nx = tx;
            for (int k = 0; k < notes.length(); k++) {
                drawNote(c, notes.charAt(k), nx + k * noteS * 1.05f + noteS / 2, r.top + h * 0.73f, noteS);
            }
        }
    }

    private void drawNote(Canvas c, char n, float cx, float cy, float s) {
        int color = n == 'A' ? A_BLUE : C_YELLOW;
        paint.setColor(color);
        c.drawCircle(cx, cy, s / 2, paint);
        paint.setColor(0xFF132016);
        float a = s * 0.22f;
        path.reset();
        switch (n) {
            case '^': path.moveTo(cx, cy - a); path.lineTo(cx + a, cy + a * 0.7f); path.lineTo(cx - a, cy + a * 0.7f); break;
            case 'v': path.moveTo(cx, cy + a); path.lineTo(cx + a, cy - a * 0.7f); path.lineTo(cx - a, cy - a * 0.7f); break;
            case '<': path.moveTo(cx - a, cy); path.lineTo(cx + a * 0.7f, cy - a); path.lineTo(cx + a * 0.7f, cy + a); break;
            case '>': path.moveTo(cx + a, cy); path.lineTo(cx - a * 0.7f, cy - a); path.lineTo(cx - a * 0.7f, cy + a); break;
            default:
                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setTextSize(s * 0.6f);
                textPaint.setColor(0xFFFFFFFF);
                c.drawText("A", cx, cy + s * 0.21f, textPaint);
                textPaint.setTextAlign(Paint.Align.LEFT);
                return;
        }
        path.close();
        c.drawPath(path, paint);
    }

    private int findOcarinaButton() {
        int max = state.dpadEquips() ? 7 : 3;
        for (int i = 1; i <= max; i++) {
            int it = state.buttonItem(i);
            if (it == GameData.ITEM_OCARINA_FAIRY || it == GameData.ITEM_OCARINA_TIME) return i;
        }
        return -1;
    }

    // ---- MAP

    private void drawMap(Canvas c) {
        float pad = u * 2;
        mapToggle.set(content.right - pad - u * 23, content.top + pad,
                content.right - pad, content.top + u * 9);
        paint.setColor(PANEL);
        c.drawRoundRect(mapToggle, u * 1.4f, u * 1.4f, paint);
        drawCentered(c, dungeonTable ? "LIVE MAP" : "DUNGEONS",
                mapToggle.centerY() + u, u * 2.7f, GOLD, mapToggle.centerX());
        String area = GameData.sceneName(state.scene(), state.isAdult());
        drawFittedText(c, area, content.left + pad, content.top + u * 6.5f,
                mapToggle.left - content.left - pad * 2, u * 4f, GOLD);
        float top = content.top + u * 11;
        if (dungeonTable) {
            drawDungeonTable(c, top);
            return;
        }
        RectF card = new RectF(content.left + pad, top, content.right - pad, content.bottom - pad);
        mapViewport.set(card);
        drawPanel(c, card);
        if (state.hasLiveMap() && mapBitmap != null && mapRevision == state.mapRevision()) {
            float margin = u * 4;
            float scale = Math.min((card.width() - margin * 2) / mapBitmap.getWidth(),
                    (card.height() - margin * 2) / mapBitmap.getHeight());
            float width = mapBitmap.getWidth() * scale * mapZoom, height = mapBitmap.getHeight() * scale * mapZoom;
            float maxX = Math.max(0, (width - card.width()) / 2 + margin);
            float maxY = Math.max(0, (height - card.height()) / 2 + margin);
            mapPanX = Math.max(-maxX, Math.min(maxX, mapPanX));
            mapPanY = Math.max(-maxY, Math.min(maxY, mapPanY));
            RectF map = new RectF(card.centerX() + mapPanX - width / 2, card.centerY() + mapPanY - height / 2,
                    card.centerX() + mapPanX + width / 2, card.centerY() + mapPanY + height / 2);
            c.save();
            c.clipRect(card);
            if (state.mapMirrored()) {
                c.save();
                c.scale(-1, 1, map.centerX(), map.centerY());
                c.drawBitmap(mapBitmap, null, map, iconPaint);
                c.restore();
            } else {
                c.drawBitmap(mapBitmap, null, map, iconPaint);
            }
            // Position is normalized against the original map dimensions, so texture packs scale too.
            float x = map.left + state.mapX() * map.width();
            float y = map.top + state.mapY() * map.height();
            double yaw = state.yaw() * Math.PI * 2 / 65536.0;
            float dx = (float)Math.sin(yaw) * (state.mapMirrored() ? -1 : 1);
            float dy = (float)Math.cos(yaw);
            float radius = u * 2.2f;
            path.reset();
            path.moveTo(x + dx * radius, y + dy * radius);
            path.lineTo(x - dx * radius * 0.7f - dy * radius * 0.65f,
                    y - dy * radius * 0.7f + dx * radius * 0.65f);
            path.lineTo(x - dx * radius * 0.35f, y - dy * radius * 0.35f);
            path.lineTo(x - dx * radius * 0.7f + dy * radius * 0.65f,
                    y - dy * radius * 0.7f - dx * radius * 0.65f);
            path.close();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(u * 0.65f);
            paint.setColor(0xFF081A13);
            c.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFCCFF55);
            c.drawPath(path, paint);
            c.restore();
        } else {
            drawCentered(c, state.hasLiveMap() ? "Loading map…" : "No map for this area",
                    card.centerY(), u * 3.6f, TEXT_DIM, card.centerX());
        }
        float right = card.right - u * 1.5f;
        float bottom = card.bottom - u * 1.5f;
        for (int i = 2; i >= 0; i--) {
            float width = u * (i == 2 ? 14 : 8);
            mapControls[i].set(right - width, bottom - u * 7, right, bottom);
            paint.setColor(0xEE0C2117);
            c.drawRoundRect(mapControls[i], u * 1.2f, u * 1.2f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(u * 0.2f);
            paint.setColor(GOLD_DIM);
            c.drawRoundRect(mapControls[i], u * 1.2f, u * 1.2f, paint);
            paint.setStyle(Paint.Style.FILL);
            drawCentered(c, i == 0 ? "−" : i == 1 ? "+" : "Reset",
                    mapControls[i].centerY() + u, u * (i == 2 ? 2.8f : 4), GOLD, mapControls[i].centerX());
            right -= width + u;
        }
    }

    private void drawTracker(Canvas c) {
        float pad = u * 2;
        drawFittedText(c, "COLLECTIBLES", pad, content.top + u * 5.5f,
                content.width() - pad * 2, u * 4, GOLD);
        String summary = "Gold Skulltulas " + state.gsTokens() + "/100   ·   Heart pieces "
                + state.totalHeartPieces() + "/36";
        drawFittedText(c, summary, pad, content.top + u * 10,
                content.width() - pad * 2, u * 2.8f, TEXT);
        float skullX = content.right - u * 28, heartX = content.right - u * 12;
        drawFittedText(c, "AREA", pad + u, content.top + u * 15, u * 60, u * 2.5f, TEXT_DIM);
        drawCentered(c, "SKULLS", content.top + u * 15, u * 2.4f, GOLD, skullX);
        drawCentered(c, "HEARTS", content.top + u * 15, u * 2.4f, GOLD, heartX);
        trackerViewport.set(content.left + pad, content.top + u * 17, content.right - pad, content.bottom - pad);
        float rowH = u * 7.8f;
        trackerScroll = Math.max(0, Math.min(trackerScroll, GameData.TRACKER_AREAS.length * rowH - trackerViewport.height()));
        c.save();
        c.clipRect(trackerViewport);
        for (int i = 0; i < GameData.TRACKER_AREAS.length; i++) {
            float y = trackerViewport.top + i * rowH - trackerScroll;
            if (y + rowH < trackerViewport.top || y > trackerViewport.bottom) continue;
            RectF row = new RectF(trackerViewport.left, y + u * 0.3f, trackerViewport.right, y + rowH - u * 0.3f);
            paint.setColor(i == state.trackerArea() ? 0xFF39583A : PANEL);
            c.drawRoundRect(row, u, u, paint);
            float baseline = row.centerY() + u;
            drawFittedText(c, GameData.TRACKER_AREAS[i], row.left + u * 1.5f, baseline,
                    skullX - u * 9 - row.left - u * 2, u * 2.8f, i == state.trackerArea() ? GOLD : TEXT);
            for (int column = 0; column < 2; column++) {
                int got = state.tracker(i, column * 2), total = state.tracker(i, column * 2 + 1);
                String count = total == 0 || (column == 1 && state.randomizedTracker()) ? "—" : got + " / " + total;
                drawCentered(c, count, baseline, u * 2.8f, total > 0 && got == total ? SELECT : TEXT,
                        column == 0 ? skullX : heartX);
            }
        }
        c.restore();
        // A thin scrollbar makes the longer list discoverable without location spoilers.
        float all = GameData.TRACKER_AREAS.length * rowH;
        float bar = trackerViewport.height() * trackerViewport.height() / all;
        float y = trackerViewport.top + trackerScroll / all * trackerViewport.height();
        paint.setColor(GOLD_DIM);
        c.drawRoundRect(new RectF(content.right - u, y, content.right - u * 0.5f, y + bar), u * 0.25f, u * 0.25f, paint);
    }

    private void drawTriforce(Canvas c, float cx, float cy, float size, int color) {
        paint.setColor(color);
        float h = size * 0.866f;
        for (int i = 0; i < 3; i++) {
            float x = i == 0 ? cx : cx + (i == 1 ? -size / 2 : size / 2);
            float y = i == 0 ? cy - h : cy;
            path.reset();
            path.moveTo(x, y);
            path.lineTo(x - size / 2, y + h);
            path.lineTo(x + size / 2, y + h);
            path.close();
            c.drawPath(path, paint);
        }
    }

    private void drawDungeonTable(Canvas c, float top) {
        float pad = u * 2;
        float rowH = (content.bottom - pad - top) / 7f;
        float colW = (content.width() - pad * 3) / 2f;
        int dungeon = GameData.dungeonForScene(state.scene());
        for (int d = 0; d < 14; d++) {
            int col = d / 7, row = d % 7;
            float x = content.left + pad + col * (colW + pad);
            float y = top + row * rowH;
            RectF cell = new RectF(x, y + rowH * 0.06f, x + colW, y + rowH * 0.94f);
            paint.setColor(d == dungeon ? 0xFF39583A : PANEL);
            c.drawRoundRect(cell, u * 1.2f, u * 1.2f, paint);
            drawFittedText(c, GameData.DUNGEON_NAMES[d], x + u * 1.5f, y + rowH * 0.33f,
                    colW - u * 3, rowH * 0.27f, d == dungeon ? GOLD : TEXT);
            float size = rowH * 0.4f, cx = x + u * 1.5f + size / 2;
            float cy = y + rowH * 0.66f;
            if (GameData.dungeonHasMapCompass(d)) {
                drawMini(c, cx, cy, size, GameData.ITEM_DUNGEON_MAP, state.hasMap(d));
                cx += size * 1.3f;
                drawMini(c, cx, cy, size, GameData.ITEM_COMPASS, state.hasCompass(d));
                cx += size * 1.3f;
            }
            if (GameData.dungeonHasBossKey(d)) {
                drawMini(c, cx, cy, size, GameData.ITEM_KEY_BOSS, state.hasBossKey(d));
                cx += size * 1.3f;
            }
            if (GameData.dungeonHasSmallKeys(d)) {
                drawMini(c, cx, cy, size, GameData.ITEM_KEY_SMALL, true);
                drawFittedText(c, "×" + Math.max(0, state.dungeonKeys(d)), cx + size * 0.7f,
                        cy + size * 0.25f, cell.right - cx - size, size * 0.7f, TEXT);
            }
        }
    }

    private void drawDungeonIcon(Canvas c, float x, float y, float s, int item, boolean has, boolean exists) {
        tmpF.set(x, y, x + s, y + s);
        drawSlotBg(c, tmpF, false);
        if (exists) drawItem(c, item, tmpF, has ? 255 : 45);
    }

    private void drawMini(Canvas c, float rightX, float cy, float s, int item, boolean has) {
        tmpF.set(rightX - s, cy - s / 2, rightX, cy + s / 2);
        drawItem(c, item, tmpF, has ? 255 : 40);
    }

    // ---- shared drawing helpers

    private void drawPanel(Canvas c, RectF r) {
        paint.setColor(PANEL);
        c.drawRoundRect(r, u * 2, u * 2, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(u * 0.3f);
        paint.setColor(PANEL_EDGE);
        c.drawRoundRect(r, u * 2, u * 2, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawSlotBg(Canvas c, RectF r, boolean selected) {
        paint.setColor(selected ? 0xFF1F4B70 : 0xFF0C1828);
        c.drawRoundRect(r, u * 1.2f, u * 1.2f, paint);
        if (selected) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(u * 0.6f);
            paint.setColor(SELECT);
            c.drawRoundRect(r, u * 1.2f, u * 1.2f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawItem(Canvas c, int item, RectF r, int alpha) {
        Bitmap b = icons.get(item);
        if (b != null) {
            tmpF.set(r);
            float inset = r.width() * 0.08f;
            RectF dst = new RectF(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset);
            iconPaint.setAlpha(alpha);
            c.drawBitmap(b, null, dst, iconPaint);
            iconPaint.setAlpha(255);
        } else {
            // Fallback until icons are decoded: initials
            String name = GameData.itemName(item);
            StringBuilder sb = new StringBuilder();
            for (String part : name.split(" ")) {
                if (!part.isEmpty() && Character.isLetter(part.charAt(0))) sb.append(part.charAt(0));
                if (sb.length() == 2) break;
            }
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(r.height() * 0.38f);
            textPaint.setColor(Color.argb(alpha, 0xE8, 0xC7, 0x66));
            c.drawText(sb.toString(), r.centerX(), r.centerY() + r.height() * 0.13f, textPaint);
            textPaint.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawAmmo(Canvas c, RectF r, int ammo, int cap) {
        textPaint.setTextAlign(Paint.Align.RIGHT);
        textPaint.setTextSize(r.height() * 0.26f);
        textPaint.setColor(ammo >= cap && cap > 0 ? 0xFF7CFC9A : (ammo == 0 ? 0xFFB0B0B0 : TEXT));
        textPaint.setShadowLayer(u * 0.4f, 0, 0, 0xFF000000);
        c.drawText(String.valueOf(ammo), r.right - r.width() * 0.06f, r.bottom - r.height() * 0.06f, textPaint);
        textPaint.clearShadowLayer();
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawCorner(Canvas c, RectF r, String label, int color) {
        textPaint.setTextSize(r.height() * 0.22f);
        float tw = textPaint.measureText(label);
        float bw = tw + r.width() * 0.1f, bh = r.height() * 0.28f;
        tmpF.set(r.left, r.top, r.left + bw, r.top + bh);
        paint.setColor(color);
        c.drawRoundRect(tmpF, bh / 3, bh / 3, paint);
        textPaint.setColor(0xFF132016);
        c.drawText(label, r.left + r.width() * 0.05f, r.top + bh * 0.78f, textPaint);
    }

    private void drawHeart(Canvas c, float x, float y, float s, float fill, boolean doubleDef) {
        path.reset();
        float w = s, h = s * 0.9f;
        path.moveTo(x + w / 2, y + h);
        path.cubicTo(x - w * 0.1f, y + h * 0.55f, x + w * 0.05f, y - h * 0.1f, x + w / 2, y + h * 0.28f);
        path.cubicTo(x + w * 0.95f, y - h * 0.1f, x + w * 1.1f, y + h * 0.55f, x + w / 2, y + h);
        path.close();
        paint.setColor(0xFF3A1418);
        c.drawPath(path, paint);
        if (fill > 0) {
            c.save();
            c.clipRect(x - w, y - h, x + w * fill, y + h * 2);
            paint.setColor(HEART_RED);
            c.drawPath(path, paint);
            c.restore();
        }
        if (doubleDef) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(s * 0.1f);
            paint.setColor(0xFFFFFFFF);
            c.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawRupee(Canvas c, float cx, float cy, float s, int color) {
        path.reset();
        path.moveTo(cx, cy - s);
        path.lineTo(cx + s * 0.6f, cy - s * 0.5f);
        path.lineTo(cx + s * 0.6f, cy + s * 0.5f);
        path.lineTo(cx, cy + s);
        path.lineTo(cx - s * 0.6f, cy + s * 0.5f);
        path.lineTo(cx - s * 0.6f, cy - s * 0.5f);
        path.close();
        paint.setColor(color);
        c.drawPath(path, paint);
    }

    private void fitText(String text, float size, float width) {
        textPaint.setTextSize(size);
        float measured = textPaint.measureText(text);
        if (measured > width && measured > 0) textPaint.setTextSize(size * Math.max(0, width) / measured);
    }

    private void drawFittedText(Canvas c, String text, float x, float baseline, float width, float size, int color) {
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setColor(color);
        fitText(text, size, width);
        c.drawText(text, x, baseline, textPaint);
    }

    private void drawCentered(Canvas c, String s, float baseline, float size, int color) {
        drawCentered(c, s, baseline, size, color, getWidth() / 2f);
    }

    private void drawCentered(Canvas c, String s, float baseline, float size, int color, float cx) {
        textPaint.setTypeface(Typeface.create("serif", Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(size);
        textPaint.setColor(color);
        c.drawText(s, cx, baseline, textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    }

    // ------------------------------------------------------------------ input

    private int itemForSelectable(int sel) {
        if (sel >= GameData.SLOT_TUNIC_KOKIRI) {
            return GameData.ITEM_TUNIC_KOKIRI + (sel - GameData.SLOT_TUNIC_KOKIRI);
        }
        return sel >= 0 && sel < 24 ? state.item(sel) : -1;
    }

    private int hitSlot(float x, float y) {
        for (int i = 0; i < 24; i++) if (slotRects[i].contains(x, y)) return i;
        return -1;
    }

    private int hitButton(float x, float y) {
        int max = state.dpadEquips() ? 8 : 4;
        for (int i = 0; i < max; i++) {
            tmpF.set(buttonRects[i]);
            tmpF.inset(-u, -u);
            if (tmpF.contains(x, y)) return i;
        }
        return -1;
    }

    private boolean hitEquip(float x, float y) {
        for (int t = 0; t < 4; t++) {
            for (int v = 0; v < 3; v++) {
                if (equipRects[t][v].contains(x, y)) {
                    downEquipType = t;
                    downEquipValue = v + 1;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        if (tab == TAB_MAP && !dungeonTable) mapScale.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                dragX = x;
                dragY = y;
                dragging = false;
                longPressFired = false;
                downSlot = -1;
                downButton = -1;
                downEquipType = -1;
                downSong = -1;
                downOcarina = false;
                mapTouch = trackerTouch = false;
                lastTouchX = x;
                lastTouchY = y;
                if (!state.saveLoaded()) return true;
                for (int i = 0; i < TAB_NAMES.length; i++) {
                    if (tabRects[i].contains(x, y)) {
                        if (tab != i) {
                            tab = i;
                            if (tab == TAB_TRACKER && state.trackerArea() >= 0) trackerScroll = state.trackerArea() * u * 7.8f;
                            selectedSlot = -1;
                            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                            invalidate();
                        }
                        return true;
                    }
                }
                if (tab == TAB_TRACKER && trackerViewport.contains(x, y)) {
                    trackerTouch = true;
                    return true;
                }
                if (tab == TAB_MAP && !dungeonTable) {
                    for (int i = 0; i < 3; i++) if (mapControls[i].contains(x, y)) {
                        zoomMap(i == 0 ? mapZoom / 1.5f : i == 1 ? mapZoom * 1.5f : 1,
                                mapViewport.centerX(), mapViewport.centerY());
                        return true;
                    }
                    mapTouch = mapViewport.contains(x, y);
                }
                if (tab == TAB_MAP && mapToggle.contains(x, y)) {
                    dungeonTable = !dungeonTable;
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    invalidate();
                    return true;
                }
                if (tab == TAB_GEAR) {
                    downSlot = hitSlot(x, y);
                    if (downSlot >= 0 && state.item(downSlot) == ThorState.ITEM_NONE) downSlot = -1;
                    if (downSlot < 0) {
                        downButton = hitButton(x, y);
                        if (downButton > 0) handler.postDelayed(longPress, 550);
                        if (downButton < 0 && hitEquip(x, y)) {
                            // tunics/boots can be dragged onto buttons when AssignableTunicsAndBoots is on
                            if (downEquipType >= 2 && state.assignableTunics()) {
                                downSlot = GameData.SLOT_TUNIC_KOKIRI + (downEquipType - 2) * 3 + (downEquipValue - 1);
                            }
                        }
                        if (downButton < 0 && downEquipType < 0 && !state.dpadEquips()
                                && x > buttonRects[4].left && y > buttonRects[4].top - u * 4) {
                            ThorBridge.setCVar("gEnhancements.DpadEquips", 1);
                            showToast("D-pad items on");
                        }
                    }
                    invalidate();
                } else if (tab == TAB_QUEST) {
                    for (int i = 0; i < 12; i++) if (songRects[i].contains(x, y)) downSong = i;
                    downOcarina = ocarinaRect.contains(x, y);
                }
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                lastTouchX = x;
                lastTouchY = y;
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                if (mapTouch && e.getPointerCount() > 1) {
                    int remaining = e.getActionIndex() == 0 ? 1 : 0;
                    lastTouchX = e.getX(remaining);
                    lastTouchY = e.getY(remaining);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (trackerTouch) {
                    trackerScroll += lastTouchY - y;
                    lastTouchY = y;
                    invalidate();
                    return true;
                }
                if (mapTouch) {
                    if (e.getPointerCount() == 1 && !mapScale.isInProgress() && mapZoom > 1) {
                        mapPanX += x - lastTouchX;
                        mapPanY += y - lastTouchY;
                    }
                    lastTouchX = x;
                    lastTouchY = y;
                    invalidate();
                    return true;
                }
                dragX = x;
                dragY = y;
                if (downSlot >= 0 && !dragging && Math.hypot(x - downX, y - downY) > u * 3) {
                    dragging = true;
                    downEquipType = -1;
                }
                if (downButton >= 0 && Math.hypot(x - downX, y - downY) > u * 4) {
                    handler.removeCallbacks(longPress);
                }
                if (dragging) invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                mapTouch = trackerTouch = false;
                handler.removeCallbacks(longPress);
                onUp(x, y);
                dragging = false;
                downSlot = -1;
                downButton = -1;
                invalidate();
                return true;

            case MotionEvent.ACTION_CANCEL:
                mapTouch = trackerTouch = false;
                handler.removeCallbacks(longPress);
                dragging = false;
                downSlot = -1;
                downButton = -1;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void onUp(float x, float y) {
        if (!state.saveLoaded()) return;
        if (tab == TAB_GEAR) {
            if (dragging && downSlot >= 0) {
                int b = hitButton(x, y);
                if (b > 0) {
                    ThorBridge.assignSlot(downSlot, b);
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                }
                selectedSlot = -1;
                return;
            }
            if (downEquipType >= 0) {
                if (isCurrentEquip()) return;
                ThorBridge.equip(downEquipType, downEquipValue);
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                return;
            }
            if (downSlot >= 0) {
                selectedSlot = selectedSlot == downSlot ? -1 : downSlot;
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                return;
            }
            if (downButton >= 0 && !longPressFired) {
                if (selectedSlot >= 0 && downButton > 0) {
                    ThorBridge.assignSlot(selectedSlot, downButton);
                    selectedSlot = -1;
                } else if (!state.paused()) {
                    // Use the item on that button, like OoT3D's touch slots
                    ThorBridge.press(ThorBridge.BUTTON_MASKS[downButton], 3);
                }
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                return;
            }
            selectedSlot = -1;
        } else if (tab == TAB_QUEST) {
            if (downOcarina && ocarinaRect.contains(x, y)) {
                int ob = findOcarinaButton();
                if (ob > 0 && !state.ocarinaPlaying()) {
                    ThorBridge.press(ThorBridge.BUTTON_MASKS[ob], 3);
                } else if (ob < 0) {
                    showToast("Assign your ocarina to a button first");
                }
                return;
            }
            if (downSong >= 0 && songRects[downSong].contains(x, y)) {
                boolean has = state.questItem(GameData.QUEST_SONG_MINUET + downSong);
                if (state.ocarinaPlaying() && has) {
                    playSong(downSong);
                } else {
                    selectedSong = selectedSong == downSong ? -1 : downSong;
                }
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
        }
    }

    private boolean isCurrentEquip() {
        return downEquipType >= 0 && state.currentEquip(downEquipType) == downEquipValue;
    }

    private void onLongPress() {
        if (downButton > 0 && tab == TAB_GEAR) {
            longPressFired = true;
            ThorBridge.clearButton(downButton);
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            showToast("Cleared " + buttonLabel(downButton));
        }
    }

    /** Feeds the song's notes into the game while the ocarina is out. */
    private void playSong(int song) {
        if (songPlaying) return;
        songPlaying = true;
        selectedSong = song;
        final String notes = GameData.SONG_NOTES[song];
        final long step = 300; // 3 frames held + ~3 frames released at 20 fps
        for (int i = 0; i < notes.length(); i++) {
            final int mask = GameData.noteMask(notes.charAt(i));
            handler.postDelayed(() -> {
                if (state.ocarinaPlaying()) ThorBridge.press(mask, 3);
            }, i * step);
        }
        handler.postDelayed(() -> songPlaying = false, notes.length() * step + 100);
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }
}
