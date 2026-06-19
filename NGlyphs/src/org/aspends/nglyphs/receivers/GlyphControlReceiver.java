package org.aspends.nglyphs.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import org.aspends.nglyphs.R;
import org.aspends.nglyphs.core.AnimationManager;
import org.aspends.nglyphs.core.GlyphManagerV2;

/** Tasker / automation hooks for Glyph Manager. */
public class GlyphControlReceiver extends BroadcastReceiver {
    public static final String ACTION_SET_MASTER = "org.aspends.nglyphs.ACTION_SET_MASTER";
    public static final String ACTION_TOGGLE_MASTER = "org.aspends.nglyphs.ACTION_TOGGLE_MASTER";
    public static final String ACTION_PREVIEW_BRIGHTNESS = "org.aspends.nglyphs.ACTION_PREVIEW_BRIGHTNESS";
    public static final String EXTRA_ENABLED = "enabled";
    public static final String EXTRA_BRIGHTNESS = "brightness";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        SharedPreferences prefs =
                context.getSharedPreferences(context.getString(R.string.pref_file), Context.MODE_PRIVATE);
        String action = intent.getAction();

        switch (action) {
            case ACTION_SET_MASTER:
                prefs.edit().putBoolean("master_allow", intent.getBooleanExtra(EXTRA_ENABLED, false)).apply();
                AnimationManager.refreshBackgroundState();
                break;
            case ACTION_TOGGLE_MASTER:
                boolean next = !prefs.getBoolean("master_allow", false);
                prefs.edit().putBoolean("master_allow", next).apply();
                AnimationManager.refreshBackgroundState();
                break;
            case ACTION_PREVIEW_BRIGHTNESS:
                int brightness = intent.getIntExtra(EXTRA_BRIGHTNESS, prefs.getInt("brightness", 2048));
                for (GlyphManagerV2.Glyph g : GlyphManagerV2.Glyph.getBasicGlyphs()) {
                    GlyphManagerV2.getInstance().setBrightness(g, brightness);
                }
                break;
            default:
                break;
        }
    }
}
