package com.tvlauncher.home;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A simple Apple-TV-style home screen for old Android TVs (API 19 / Android 4.4).
 * Dark gradient background, big clock, rows of rounded tiles that grow when focused.
 * Hold OK on an app tile to add or remove it from Favorites.
 */
public class LauncherActivity extends Activity {

    private static final String PREFS = "launcher_prefs";
    private static final String KEY_FAVS = "favorites";
    private static final String CAT_LEANBACK = "android.intent.category.LEANBACK_LAUNCHER";

    /** One tile: an installed app, or a quick-settings shortcut (key == null). */
    private static final class Entry {
        String key;       // "package/class" for apps, null for quick settings
        String label;
        Drawable icon;    // null -> a letter glyph is drawn instead
        Intent intent;
    }

    private PackageManager pm;
    private float density;
    private int tileSize;
    private ScrollView scroller;
    private LinearLayout content;
    private final Map<String, View> tiles = new HashMap<String, View>();
    private View firstTile;
    private String pendingFocusKey;

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pm = getPackageManager();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        density = dm.density;
        int longSide = Math.max(dm.widthPixels, dm.heightPixels);
        tileSize = Math.max(dp(110), Math.min(dp(210), longSide / 8));
        setContentView(buildRoot());
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload(); // picks up newly installed / removed apps
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already on the launcher: jump back to the top.
        if (scroller != null) scroller.scrollTo(0, 0);
        if (firstTile != null) firstTile.requestFocus();
    }

    @Override
    public void onBackPressed() {
        // A launcher has nowhere to go back to.
    }

    // ---------------------------------------------------------------- layout

    private View buildRoot() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(56), dp(28), dp(56), dp(14));
        page.setClipChildren(false);
        page.setClipToPadding(false);
        page.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0B1020, 0xFF1B1038, 0xFF2D1655}));

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm a");
        clock.setFormat24Hour("HH:mm");
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 52);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        page.addView(clock);

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextColor(0xB3FFFFFF);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        page.addView(date);

        scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        scroller.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView hint = new TextView(this);
        hint.setText("Hold OK on an app to add or remove it from Favorites");
        hint.setTextColor(0x80FFFFFF);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        hint.setPadding(dp(14), dp(6), 0, 0);
        page.addView(hint);

        return page;
    }

    private void reload() {
        content.removeAllViews();
        tiles.clear();
        firstTile = null;

        List<Entry> apps = loadApps();
        Set<String> favs = getFavs();
        List<Entry> favApps = new ArrayList<Entry>();
        for (Entry e : apps) {
            if (favs.contains(e.key)) favApps.add(e);
        }

        if (!favApps.isEmpty()) addRow("Favorites", "fav:", favApps);
        addRow("Apps", "app:", apps);
        addRow("Quick Settings", "set:", quickSettings());

        View target = pendingFocusKey == null ? null : tiles.get("app:" + pendingFocusKey);
        pendingFocusKey = null;
        if (target == null) target = firstTile;
        if (target != null) target.requestFocus();
    }

    private void addRow(String title, String prefix, List<Entry> entries) {
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextColor(0xE6FFFFFF);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setPadding(dp(14), dp(18), 0, 0);
        content.addView(heading);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hsv.setClipChildren(false);
        hsv.setClipToPadding(false);
        hsv.setPadding(0, dp(14), 0, dp(20));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setPadding(dp(4), 0, dp(80), 0);

        for (Entry e : entries) {
            View tile = makeTile(e);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileSize, tileSize);
            lp.setMargins(dp(10), 0, dp(10), 0);
            row.addView(tile, lp);
            tiles.put(prefix + (e.key != null ? e.key : e.label), tile);
            if (firstTile == null) firstTile = tile;
        }

        hsv.addView(row, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(hsv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private View makeTile(final Entry e) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(6), dp(8), dp(6), dp(8));
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setBackground(tileBackground());

        int iconSize = Math.round(tileSize * 0.5f);
        View icon;
        if (e.icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(e.icon);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon = iv;
        } else {
            icon = glyph(e.label);
        }
        tile.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));

        TextView label = new TextView(this);
        label.setText(e.label);
        label.setTextColor(Color.WHITE);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dp(8);
        tile.addView(label, llp);

        // Focus effect: the selected tile grows slightly (kept small so old TV boxes stay smooth).
        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float s = hasFocus ? 1.10f : 1f;
                v.animate().scaleX(s).scaleY(s).setDuration(120).start();
            }
        });

        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launch(e);
            }
        });

        if (e.key != null) {
            tile.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    toggleFavorite(e);
                    return true;
                }
            });
        }
        return tile;
    }

    private View glyph(String label) {
        TextView t = new TextView(this);
        String first = (label != null && label.length() > 0) ? label.substring(0, 1) : "?";
        t.setText(first.toUpperCase());
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0x33FFFFFF);
        t.setBackground(g);
        return t;
    }

    private Drawable tileBackground() {
        GradientDrawable normal = new GradientDrawable();
        normal.setCornerRadius(dp(18));
        normal.setColor(0x24FFFFFF);

        GradientDrawable focused = new GradientDrawable();
        focused.setCornerRadius(dp(18));
        focused.setColor(0x4DFFFFFF);
        focused.setStroke(dp(3), 0xFFFFFFFF);

        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, focused);
        sl.addState(new int[]{}, normal);
        return sl;
    }

    // ---------------------------------------------------------------- data

    private List<Entry> loadApps() {
        Set<String> seen = new HashSet<String>();
        List<Entry> out = new ArrayList<Entry>();
        addFromCategory(Intent.CATEGORY_LAUNCHER, seen, out);
        addFromCategory(CAT_LEANBACK, seen, out);
        Collections.sort(out, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return out;
    }

    private void addFromCategory(String category, Set<String> seen, List<Entry> out) {
        Intent query = new Intent(Intent.ACTION_MAIN, null);
        query.addCategory(category);
        List<ResolveInfo> found = pm.queryIntentActivities(query, 0);
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            String cls = ri.activityInfo.name;
            if (pkg.equals(getPackageName())) continue; // don't list ourselves
            String key = pkg + "/" + cls;
            if (!seen.add(key)) continue;

            Entry e = new Entry();
            e.key = key;
            e.label = String.valueOf(ri.loadLabel(pm));
            e.icon = ri.loadIcon(pm);
            Intent launch = new Intent(Intent.ACTION_MAIN);
            launch.addCategory(category);
            launch.setComponent(new ComponentName(pkg, cls));
            launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            e.intent = launch;
            out.add(e);
        }
    }

    private List<Entry> quickSettings() {
        List<Entry> l = new ArrayList<Entry>();
        l.add(quick("Settings", Settings.ACTION_SETTINGS));
        l.add(quick("Wi-Fi", Settings.ACTION_WIFI_SETTINGS));
        l.add(quick("Display", Settings.ACTION_DISPLAY_SETTINGS));
        l.add(quick("Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
        l.add(quick("Manage Apps", Settings.ACTION_APPLICATION_SETTINGS));
        return l;
    }

    private Entry quick(String label, String action) {
        Entry e = new Entry();
        e.label = label;
        e.intent = new Intent(action);
        return e;
    }

    private Set<String> getFavs() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        return new HashSet<String>(sp.getStringSet(KEY_FAVS, new HashSet<String>()));
    }

    private void toggleFavorite(Entry e) {
        Set<String> favs = getFavs();
        boolean added;
        if (favs.contains(e.key)) {
            favs.remove(e.key);
            added = false;
        } else {
            favs.add(e.key);
            added = true;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putStringSet(KEY_FAVS, favs).apply();
        toast((added ? "Added to Favorites: " : "Removed from Favorites: ") + e.label);
        pendingFocusKey = e.key;
        reload();
    }

    // ---------------------------------------------------------------- helpers

    private void launch(Entry e) {
        try {
            startActivity(e.intent);
        } catch (ActivityNotFoundException ex) {
            toast("Can't open " + e.label);
        } catch (SecurityException ex) {
            toast("Can't open " + e.label);
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
