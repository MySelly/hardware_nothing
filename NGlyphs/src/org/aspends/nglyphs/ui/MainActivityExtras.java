package org.aspends.nglyphs.ui;

import android.content.Intent;
import android.net.Uri;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import android.content.SharedPreferences;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import java.io.OutputStream;
import org.aspends.nglyphs.R;
import org.aspends.nglyphs.util.GlyphSettingsBackup;
import org.json.JSONObject;

/** Wires advanced settings cards on the main screen. */
public final class MainActivityExtras {
    private MainActivityExtras() {}

    public static void bind(AppCompatActivity activity, SharedPreferences prefs) {
        bindCooldown(activity, prefs);
        bindBackup(activity, prefs);
        bindDnd(activity, prefs);
        bindProgressPriority(activity, prefs);
        bindCredits(activity);
        bindCrashLog(activity);
    }

    private static void bindCooldown(AppCompatActivity activity, SharedPreferences prefs) {
        Slider slider = activity.findViewById(R.id.sliderCooldown);
        TextView label = activity.findViewById(R.id.textCooldownValue);
        MaterialCardView manage = activity.findViewById(R.id.cardCooldownVip);
        if (slider == null) {
            return;
        }
        int seconds = prefs.getInt("notif_cooldown_seconds", 60);
        slider.setValue(seconds);
        updateCooldownLabel(label, seconds);
        slider.addOnChangeListener((s, value, fromUser) -> {
            int sec = Math.max(15, (int) value);
            prefs.edit().putInt("notif_cooldown_seconds", sec).apply();
            updateCooldownLabel(label, sec);
        });
        if (manage != null) {
            manage.setOnClickListener(
                    v -> activity.startActivity(new Intent(activity, VipAppsActivity.class)));
        }
    }

    private static void updateCooldownLabel(TextView label, int seconds) {
        if (label != null) {
            label.setText(label.getContext().getString(R.string.notification_cooldown_seconds, seconds));
        }
    }

    private static void bindBackup(AppCompatActivity activity, SharedPreferences prefs) {
        MaterialCardView exportCard = activity.findViewById(R.id.cardBackupExport);
        MaterialCardView importCard = activity.findViewById(R.id.cardBackupImport);
        if (exportCard == null || importCard == null) {
            return;
        }

        ActivityResultLauncher<String> exportLauncher =
                activity.registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"),
                        uri -> {
                            if (uri == null) {
                                return;
                            }
                            try (OutputStream os = activity.getContentResolver().openOutputStream(uri)) {
                                if (os == null) {
                                    throw new IllegalStateException("No output stream");
                                }
                                GlyphSettingsBackup.writeJsonToStream(os, GlyphSettingsBackup.exportSettings(activity));
                                Toast.makeText(activity, R.string.backup_export_success, Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                Toast.makeText(activity,
                                        activity.getString(R.string.backup_import_failed, e.getMessage()),
                                        Toast.LENGTH_LONG)
                                        .show();
                            }
                        });

        ActivityResultLauncher<String[]> importLauncher =
                activity.registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                    if (uri == null) {
                        return;
                    }
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle(R.string.backup_import_confirm_title)
                            .setMessage(R.string.backup_import_confirm_message)
                            .setPositiveButton(R.string.backup_restore, (d, w) -> {
                                try (java.io.InputStream is =
                                        activity.getContentResolver().openInputStream(uri)) {
                                    if (is == null) {
                                        throw new IllegalStateException("No input stream");
                                    }
                                    JSONObject root = GlyphSettingsBackup.readJsonFromStream(is);
                                    GlyphSettingsBackup.importSettings(activity, root);
                                    Toast.makeText(activity, R.string.backup_import_success, Toast.LENGTH_SHORT)
                                            .show();
                                    activity.recreate();
                                } catch (Exception e) {
                                    Toast.makeText(activity,
                                            activity.getString(R.string.backup_import_failed, e.getMessage()),
                                            Toast.LENGTH_LONG)
                                            .show();
                                }
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                });

        exportCard.setOnClickListener(v -> exportLauncher.launch("glyph_settings_backup.json"));
        importCard.setOnClickListener(v -> importLauncher.launch(new String[] {"application/json", "*/*"}));
    }

    private static void bindDnd(AppCompatActivity activity, SharedPreferences prefs) {
        MaterialSwitch sw = activity.findViewById(R.id.switchRespectDnd);
        if (sw == null) {
            return;
        }
        sw.setChecked(prefs.getBoolean("respect_dnd", false));
        sw.setOnCheckedChangeListener((v, checked) -> prefs.edit().putBoolean("respect_dnd", checked).apply());
    }

    private static void bindProgressPriority(AppCompatActivity activity, SharedPreferences prefs) {
        MaterialCardView card = activity.findViewById(R.id.cardProgressPriority);
        TextView summary = activity.findViewById(R.id.textProgressPriority);
        if (card == null) {
            return;
        }
        updateProgressSummary(summary, prefs.getInt("progress_source_priority", 0));
        card.setOnClickListener(v -> {
            String[] options = {
                activity.getString(R.string.progress_priority_download),
                activity.getString(R.string.progress_priority_music)
            };
            int current = prefs.getInt("progress_source_priority", 0);
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.progress_priority_title)
                    .setSingleChoiceItems(options, current, (dialog, which) -> {
                        prefs.edit().putInt("progress_source_priority", which).apply();
                        updateProgressSummary(summary, which);
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
    }

    private static void updateProgressSummary(TextView summary, int which) {
        if (summary == null) {
            return;
        }
        summary.setText(which == 1 ? R.string.progress_priority_music : R.string.progress_priority_download);
    }

    private static void bindCredits(AppCompatActivity activity) {
        TextView footer = activity.findViewById(R.id.textCredits);
        if (footer != null) {
            footer.setOnClickListener(v -> CreditsDialog.show(activity));
        }
    }

    private static void bindCrashLog(AppCompatActivity activity) {
        MaterialCardView card = activity.findViewById(R.id.cardCrashLog);
        if (card == null) {
            return;
        }
        card.setOnClickListener(v -> {
            java.io.File extDir = activity.getExternalFilesDir(null);
            if (extDir == null) {
                extDir = activity.getFilesDir();
            }
            java.io.File log = new java.io.File(extDir, "crash_log.txt");
            if (!log.exists()) {
                Toast.makeText(activity, R.string.crash_log_none, Toast.LENGTH_SHORT).show();
                return;
            }
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(log));
            share.putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.crash_log_share_subject));
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(share, activity.getString(R.string.crash_log_share)));
        });
    }
}
