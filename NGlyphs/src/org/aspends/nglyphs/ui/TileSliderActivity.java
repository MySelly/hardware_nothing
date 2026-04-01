package org.aspends.nglyphs.ui;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.service.quicksettings.TileService;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.slider.Slider;
import org.aspends.nglyphs.R;
import org.aspends.nglyphs.core.GlyphManagerV2;
import org.aspends.nglyphs.services.AutoBrightnessService;
import org.aspends.nglyphs.services.GlyphTileService;

public class TileSliderActivity extends AppCompatActivity {
    private SharedPreferences prefs;
    private Vibrator vibrator;
    private int currentBrightness;
    private Slider slider;
    private boolean isMasterAllowed;

    private final BroadcastReceiver screenOffReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                finish();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);

        org.aspends.nglyphs.core.AnimationManager.init(this);
        org.aspends.nglyphs.core.GlyphManagerV2.getInstance().init(this);

        ComponentName component =
                getIntent().getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName.class);
        if (component != null
                && !component.getClassName().equals(GlyphTileService.class.getName())) {
            // Long-press on master tile → open the app
            Intent launch = new Intent(this, MainActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
            finish();
            return;
        }

        setContentView(R.layout.activity_tile_slider);

        Window window = getWindow();
        if (window != null) {
            window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.90),
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setDimAmount(0.5f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        registerReceiver(screenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));

        prefs = getSharedPreferences(getString(R.string.pref_file), MODE_PRIVATE);
        vibrator = getSystemService(Vibrator.class);
        isMasterAllowed = prefs.getBoolean("master_allow", false);
        currentBrightness = prefs.getInt("torch_brightness", 2048);

        slider = findViewById(R.id.sliderTile);

        if (!isMasterAllowed) {
            android.widget.Toast
                    .makeText(this, "Enable Glyphs first!", android.widget.Toast.LENGTH_SHORT)
                    .show();
            finish();
            return;
        }

        slider.setValue(mapBrightnessToPosition(currentBrightness));
        if (!prefs.getBoolean("is_light_on", false)) {
            slider.setEnabled(false);
        }

        com.google.android.material.button.MaterialButton btnToggle =
                findViewById(R.id.btnToggleTorch);
        btnToggle.setOnClickListener(v -> {
            boolean wasOn = prefs.getBoolean("is_light_on", false);
            boolean newState = !wasOn;

            prefs.edit().putBoolean("is_light_on", newState).apply();
            org.aspends.nglyphs.core.AnimationManager.refreshBackgroundState();

            slider.setEnabled(newState);
            if (newState) {
                slider.setValue(mapBrightnessToPosition(currentBrightness));
            }

            try {
                TileService.requestListeningState(
                        this, new ComponentName(this, GlyphTileService.class));
            } catch (Exception e) {
                android.util.Log.e(
                        "TileSliderActivity", "Failed to request tile listening state", e);
            }
            quickTick(20, 100);
        });

        slider.addOnChangeListener((s, value, fromUser) -> {
            if (fromUser) {
                if (prefs.getBoolean("auto_brightness_enabled", false)) {
                    prefs.edit().putBoolean("auto_brightness_enabled", false).apply();
                    stopService(new android.content.Intent(this, AutoBrightnessService.class));
                }

                int brightness = mapPositionToBrightness(value);
                if (brightness != currentBrightness) {
                    quickTick(10, 50);
                    currentBrightness = brightness;
                    prefs.edit().putInt("torch_brightness", currentBrightness).apply();

                    if (prefs.getBoolean("is_light_on", false)) {
                        org.aspends.nglyphs.core.AnimationManager.refreshBackgroundState();
                    }
                }
            }
        });
    }

    private void quickTick(int d, int a) {
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(d, a));
        }
    }

    private int mapPositionToBrightness(float p) {
        if (p <= 0)
            return 0;
        if (p >= 100)
            return 4095;
        return (int) ((p / 100f) * 4095);
    }

    private float mapBrightnessToPosition(int b) {
        if (b <= 0)
            return 0f;
        if (b >= 4095)
            return 100f;
        return (float) Math.round((b / 4095f) * 100f);
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(screenOffReceiver);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }
}
