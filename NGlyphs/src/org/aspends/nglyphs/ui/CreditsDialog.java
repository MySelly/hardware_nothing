package org.aspends.nglyphs.ui;

import android.content.Intent;
import android.net.Uri;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.aspends.nglyphs.R;

public final class CreditsDialog {
    private static final String REPO_URL =
            "https://github.com/crdroidandroid/android_hardware_nothing/tree/16.0/NGlyphs";

    private CreditsDialog() {}

    public static void show(AppCompatActivity activity) {
        String message = activity.getString(R.string.credits_thanks)
                + "\n\n"
                + activity.getString(R.string.credits_main_contributors)
                + "\n• MySelly — "
                + activity.getString(R.string.credits_contributor_myselly)
                + "\n• Jis G Jacob — "
                + activity.getString(R.string.credits_contributor_jis)
                + "\n• Aspends — "
                + activity.getString(R.string.credits_contributor_aspends)
                + "\n\n"
                + activity.getString(R.string.credits_github_repo);

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.credits_title)
                .setMessage(message)
                .setPositiveButton(R.string.credits_view_github,
                        (d, w) -> activity.startActivity(
                                new Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL))))
                .setNegativeButton(R.string.close, null)
                .show();
    }
}
