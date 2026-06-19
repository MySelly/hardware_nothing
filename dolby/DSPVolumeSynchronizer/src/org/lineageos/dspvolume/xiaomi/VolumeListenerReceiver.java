package org.lineageos.dspvolume.xiaomi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;

public class VolumeListenerReceiver extends BroadcastReceiver {

    private static final String PREFS = "dolby_prefs";
    private static final String KEY_ENABLED = "dsp_volume_boost_enabled";
    private static final String KEY_STRENGTH = "dsp_volume_boost_strength";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null) {
            return;
        }

        if (intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", 0) != AudioManager.STREAM_MUSIC) {
            return;
        }

        AudioManager audioManager = context.getSystemService(AudioManager.class);
        int current = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", 0);

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean boostEnabled = prefs.getBoolean(KEY_ENABLED, false);
        int strength = prefs.getInt(KEY_STRENGTH, 0);

        if (!boostEnabled || strength <= 0) {
            audioManager.setParameters("volume_change=" + current + ";flags=8");
            return;
        }

        int maxSteps = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        if (maxSteps <= 0) {
            maxSteps = 15;
        }
        float fraction = (float) current / (float) maxSteps;
        int extra = Math.round(fraction * strength / 100f * maxSteps * 0.35f);
        int scaled = Math.max(0, Math.min(maxSteps, current + extra));
        audioManager.setParameters("volume_change=" + scaled + ";flags=8");
        audioManager.setParameters("dsp_loudness_boost=" + strength);
    }
}
