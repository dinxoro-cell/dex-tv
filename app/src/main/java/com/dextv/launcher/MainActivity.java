package com.dextv.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Build;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private final ArrayList<AppEntry> apps = new ArrayList<>();
    private TvView tvView;
    private RemoteServer remote;
    private int selection = 0;
    private int page = 0;
    private static final int COLS = 5;
    private static final int ROWS = 2;
    private static final int PAGE_SIZE = COLS * ROWS;

    static final class AppEntry {
        final String label;
        final String packageName;
        final Drawable icon;
        AppEntry(String label, String packageName, Drawable icon) {
            this.label = label; this.packageName = packageName; this.icon = icon;
        }
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        loadApps();
        tvView = new TvView();
        setContentView(tvView);
        remote = new RemoteServer(this);
        remote.start();
    }

    @SuppressWarnings("deprecation")
    private void loadApps() {
        apps.clear();
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> results = pm.queryIntentActivities(query, 0);
        for (ResolveInfo info : results) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean exists = false;
            for (AppEntry app : apps) if (app.packageName.equals(pkg)) { exists = true; break; }
            if (exists) continue;
            try {
                apps.add(new AppEntry(info.loadLabel(pm).toString(), pkg, info.loadIcon(pm)));
            } catch (Exception ignored) {}
        }
        // Put common entertainment apps first when installed. Everything else is alphabetical.
        Collections.sort(apps, Comparator.comparing(a -> a.label.toLowerCase(Locale.ROOT)));
        String[] favorites = {"com.google.android.youtube", "com.netflix.mediaclient", "com.disney.disneyplus",
            "com.amazon.avod.thirdpartyclient", "com.spotify.music", "org.videolan.vlc", "com.plexapp.android"};
        ArrayList<AppEntry> ordered = new ArrayList<>();
        for (String pkg : favorites) {
            for (AppEntry e : apps) if (pkg.equals(e.packageName)) { ordered.add(e); break; }
        }
        for (AppEntry e : apps) if (!ordered.contains(e)) ordered.add(e);
        apps.clear(); apps.addAll(ordered);
    }

    private void launchSelected() {
        if (apps.isEmpty()) return;
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(apps.get(selection).packageName);
            if (launch != null) startActivity(launch);
        } catch (Exception ignored) {}
    }

    void command(String key) {
        runOnUiThread(() -> {
            int old = selection;
            switch (key) {
                case "left": selection = Math.max(0, selection - 1); break;
                case "right": selection = Math.min(apps.size() - 1, selection + 1); break;
                case "up": selection = Math.max(0, selection - COLS); break;
                case "down": selection = Math.min(apps.size() - 1, selection + COLS); break;
                case "ok": launchSelected(); break;
                case "home": selection = 0; break;
                case "back": onBackPressed(); break;
                case "refresh": loadApps(); selection = 0; break;
            }
            if (apps.isEmpty()) selection = 0;
            if (old != selection || key.equals("refresh")) {
                page = selection / PAGE_SIZE;
                tvView.invalidate();
            }
        });
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) { command("left"); return true; }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { command("right"); return true; }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) { command("up"); return true; }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { command("down"); return true; }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) { command("ok"); return true; }
        if (keyCode == KeyEvent.KEYCODE_HOME) { command("home"); return true; }
        return super.onKeyDown(keyCode, event);
    }
    @Override protected void onDestroy() {
        if (remote != null) remote.stop();
        super.onDestroy();
    }

    private final class TvView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF rect = new RectF();
        TvView() { super(MainActivity.this); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        void txt(Canvas c, String value, float x, float y, float size, int color, boolean bold) {
            p.reset(); p.setAntiAlias(true); p.setColor(color); p.setTextSize(size);
            p.setTypeface(bold ? android.graphics.Typeface.create("sans-serif", 1) :
                android.graphics.Typeface.create("sans-serif", 0));
            c.drawText(value, x, y, p);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            canvas.save(); canvas.scale(w / 1280f, h / 720f);
            p.setShader(new LinearGradient(0, 0, 1280, 720,
                new int[]{0xff111b32, 0xff090d1a, 0xff161126}, null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, 1280, 720, p); p.setShader(null);
            txt(canvas, "DeX TV", 65, 81, 35, Color.WHITE, true);
            txt(canvas, "HOME", 65, 135, 15, 0xff9aa6bc, true);
            txt(canvas, "Your entertainment, beautifully simple.", 65, 182, 26, 0xffe3e9f8, false);
            txt(canvas, "APPS", 65, 242, 18, 0xffaab8cf, true);
            int start = page * PAGE_SIZE;
            for (int i = start; i < Math.min(start + PAGE_SIZE, apps.size()); i++) {
                int index = i - start;
                int col = index % COLS, row = index / COLS;
                float x = 65 + col * 234, y = 266 + row * 154;
                boolean selected = i == selection;
                rect.set(x, y, x + 215, y + 137);
                p.setColor(selected ? 0xffe9f0ff : 0xff232e45);
                p.setShadowLayer(selected ? 24 : 0, 0, selected ? 4 : 0, 0x668abfff);
                canvas.drawRoundRect(rect, 18, 18, p); p.clearShadowLayer();
                try {
                    Drawable icon = apps.get(i).icon;
                    icon.setBounds((int)x + 83, (int)y + 17, (int)x + 133, (int)y + 67);
                    icon.draw(canvas);
                } catch (Exception ignored) {}
                String label = apps.get(i).label;
                if (label.length() > 23) label = label.substring(0, 21) + "…";
                txt(canvas, label, x + 14, y + 106, 17, selected ? 0xff18223b : 0xfff0f3fa, selected);
            }
            if (apps.isEmpty()) txt(canvas, "No launchable apps found. Install apps and press Refresh on the remote.", 65, 350, 22, Color.WHITE, false);
            String info = String.format(Locale.US, "PAGE %d / %d", page + 1, Math.max(1, (apps.size() + PAGE_SIZE - 1) / PAGE_SIZE));
            txt(canvas, info, 65, 615, 16, 0xffa8b6ca, true);
            String address = remote == null ? "Starting remote..." : remote.getAddressLabel();
            txt(canvas, "PHONE REMOTE   " + address, 65, 663, 18, 0xffd3dafa, false);
            txt(canvas, "PAIRING PIN   " + (remote == null ? "------" : remote.getPin()), 65, 695, 17, 0xffd3dafa, true);
            txt(canvas, "Use arrow keys + Enter, or pair a second phone", 760, 663, 16, 0xff9aa6bc, false);
            canvas.restore();
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() != MotionEvent.ACTION_UP) return true;
            float x = e.getX() * 1280f / getWidth(), y = e.getY() * 720f / getHeight();
            if (x >= 65 && y >= 266 && y < 557) {
                int col = (int)((x - 65) / 234f), row = (int)((y - 266) / 154f);
                if (col >= 0 && col < COLS && row >= 0 && row < ROWS) {
                    int i = page * PAGE_SIZE + row * COLS + col;
                    if (i < apps.size()) {
                        if (selection == i) launchSelected(); else { selection = i; invalidate(); }
                    }
                }
            }
            return true;
        }
    }
}
