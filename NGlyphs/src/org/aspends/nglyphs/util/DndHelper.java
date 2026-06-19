package org.aspends.nglyphs.util;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

public final class DndHelper {
    private DndHelper() {}

    public static boolean isDndActive(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int filter = nm.getCurrentInterruptionFilter();
            return filter != NotificationManager.INTERRUPTION_FILTER_ALL
                    && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN;
        }
        return false;
    }
}
