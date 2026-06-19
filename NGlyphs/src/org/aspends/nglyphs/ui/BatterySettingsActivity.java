package org.aspends.nglyphs.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import org.aspends.nglyphs.R;
import org.aspends.nglyphs.services.BatteryGlyphService;
import org.aspends.nglyphs.services.PowershareService;

public class BatterySettingsActivity extends AppCompatActivity {
    private SharedPreferences prefs;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_battery_settings);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.nestedScroll), (v, windowInsets) -> {
                    androidx.core.graphics.Insets insets =
                            windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                            insets.bottom);
                    return windowInsets;
                });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        prefs = getSharedPreferences(getString(R.string.pref_file), MODE_PRIVATE);
        vibrator = getSystemService(Vibrator.class);

        MaterialSwitch switchBattery = findViewById(R.id.switchBattery);
        MaterialSwitch switchBatteryWhileOn = findViewById(R.id.switchBatteryWhileOn);
        MaterialSwitch switchBluetoothBattery = findViewById(R.id.switchBluetoothBattery);
        MaterialSwitch switchBluetoothBatteryCombine =
                findViewById(R.id.switchBluetoothBatteryCombine);
        MaterialSwitch switchPowershare = findViewById(R.id.switchPowershare);

        switchBattery.setChecked(prefs.getBoolean("battery_glyph_enabled", false));
        switchBatteryWhileOn.setChecked(prefs.getBoolean("battery_screen_on_enabled", false));
        switchBluetoothBattery.setChecked(
                prefs.getBoolean("bluetooth_battery_glyph_enabled", false));
        switchBluetoothBatteryCombine.setChecked(
                prefs.getBoolean("bluetooth_battery_combine_enabled", false));
        switchPowershare.setChecked(prefs.getBoolean("powershare_glyph_enabled", false));

        switchBatteryWhileOn.setEnabled(switchBattery.isChecked());
        switchBluetoothBatteryCombine.setEnabled(switchBluetoothBattery.isChecked());

        switchBattery.setOnCheckedChangeListener((v, ic) -> {
            quickTick(15, 100);
            prefs.edit().putBoolean("battery_glyph_enabled", ic).apply();
            switchBatteryWhileOn.setEnabled(ic);
            Intent intent = new Intent(this, BatteryGlyphService.class);
            if (ic && prefs.getBoolean("master_allow", false)) {
                startService(intent);
            } else {
                stopService(intent);
            }
        });

        switchBatteryWhileOn.setOnCheckedChangeListener((v, ic) -> {
            quickTick(15, 100);
            prefs.edit().putBoolean("battery_screen_on_enabled", ic).apply();
        });

        switchBluetoothBattery.setOnCheckedChangeListener((v, ic) -> {
            quickTick(15, 100);
            prefs.edit().putBoolean("bluetooth_battery_glyph_enabled", ic).apply();
            switchBluetoothBatteryCombine.setEnabled(ic);
        });

        switchBluetoothBatteryCombine.setOnCheckedChangeListener((v, ic) -> {
            quickTick(15, 100);
            prefs.edit().putBoolean("bluetooth_battery_combine_enabled", ic).apply();
        });

        switchPowershare.setOnCheckedChangeListener((v, ic) -> {
            quickTick(15, 100);
            prefs.edit().putBoolean("powershare_glyph_enabled", ic).apply();
            Intent psIntent = new Intent(this, PowershareService.class);
            if (ic && prefs.getBoolean("master_allow", false)) {
                startService(psIntent);
            } else {
                stopService(psIntent);
            }
        });

        MaterialSwitch switchLowBattery = findViewById(R.id.switchLowBattery);
        MaterialSwitch switchChargeComplete = findViewById(R.id.switchChargeComplete);
        com.google.android.material.slider.Slider sliderLowBattery = findViewById(R.id.sliderLowBattery);
        android.widget.TextView textLowBatteryThreshold = findViewById(R.id.textLowBatteryThreshold);
        if (switchLowBattery != null && sliderLowBattery != null) {
            switchLowBattery.setChecked(prefs.getBoolean("low_battery_glyph_enabled", false));
            int threshold = prefs.getInt("low_battery_threshold", 15);
            sliderLowBattery.setValue(threshold);
            updateLowBatteryLabel(textLowBatteryThreshold, threshold);
            switchLowBattery.setOnCheckedChangeListener((v, ic) -> {
                quickTick(15, 100);
                prefs.edit().putBoolean("low_battery_glyph_enabled", ic).apply();
            });
            sliderLowBattery.addOnChangeListener((s, value, fromUser) -> {
                if (fromUser) {
                    int t = (int) value;
                    prefs.edit().putInt("low_battery_threshold", t).apply();
                    updateLowBatteryLabel(textLowBatteryThreshold, t);
                }
            });
        }
        if (switchChargeComplete != null) {
            switchChargeComplete.setChecked(prefs.getBoolean("charge_complete_glyph_enabled", false));
            switchChargeComplete.setOnCheckedChangeListener((v, ic) -> {
                quickTick(15, 100);
                prefs.edit().putBoolean("charge_complete_glyph_enabled", ic).apply();
            });
        }
    }

    private void updateLowBatteryLabel(android.widget.TextView label, int threshold) {
        if (label != null) {
            label.setText(getString(R.string.low_battery_threshold, threshold));
        }
    }

    private void quickTick(int d, int a) {
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(d, a));
        }
    }
}
