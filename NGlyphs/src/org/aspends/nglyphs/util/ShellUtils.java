package org.aspends.nglyphs.util;

import android.util.Log;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages low-latency hardware control via sysfs.
 */
public class ShellUtils {
    private static final String TAG = "ShellUtils";
    private static final java.util.concurrent.LinkedBlockingQueue<Runnable> queue =
            new java.util.concurrent.LinkedBlockingQueue<>();
    private static final ExecutorService shellExecutor =
            new java.util.concurrent.ThreadPoolExecutor(
                    1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS, queue);

    private static final java.util.regex.Pattern SAFE_VALUE =
            java.util.regex.Pattern.compile("^[0-9a-fA-Fx ]+$");

    private static boolean isSafeSysfsPath(String path) {
        return path != null && path.startsWith("/sys/") && !path.contains("..");
    }

    public static void fastWrite(String path, String value) {
        shellExecutor.execute(() -> {
            if (!tryDirectWrite(path, value)) {
                if (!isSafeSysfsPath(path) || !SAFE_VALUE.matcher(value).matches()) {
                    Log.e(TAG, "Rejected unsafe shell write: " + path);
                    return;
                }
                executeCommandInternal("echo " + value + " > " + path);
            }
        });
    }

    public static void fastWriteBatch(java.util.Map<String, String> updates) {
        if (updates == null || updates.isEmpty())
            return;
        shellExecutor.execute(() -> {
            boolean allDirectOk = true;
            for (java.util.Map.Entry<String, String> entry : updates.entrySet()) {
                if (!tryDirectWrite(entry.getKey(), entry.getValue())) {
                    allDirectOk = false;
                }
            }
            if (allDirectOk)
                return;

            StringBuilder sb = new StringBuilder();
            for (java.util.Map.Entry<String, String> entry : updates.entrySet()) {
                if (!isSafeSysfsPath(entry.getKey())
                        || !SAFE_VALUE.matcher(entry.getValue()).matches()) {
                    Log.e(TAG, "Rejected unsafe shell write: " + entry.getKey());
                    continue;
                }
                sb.append("echo ")
                        .append(entry.getValue())
                        .append(" > ")
                        .append(entry.getKey())
                        .append("; ");
            }
            if (sb.length() > 0)
                executeCommandInternal(sb.toString());
        });
    }

    private static boolean tryDirectWrite(String path, String value) {
        java.io.File file = new java.io.File(path);
        // Even if canWrite() returns false, we might have permission as a system app
        // so we try writing anyway.

        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
            fos.write(value.getBytes());
            fos.flush();
            return true;
        } catch (Exception e) {
            // Log.v(TAG, "Direct write failed for " + path + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Executes a raw command via sh.
     */
    public static synchronized void executeCommand(String command) {
        if (command == null || command.isEmpty())
            return;
        shellExecutor.execute(() -> executeCommandInternal(command));
    }

    private static void executeCommandInternal(String command) {
        try {
            Runtime.getRuntime().exec(new String[] {"sh", "-c", command}).waitFor();
        } catch (Exception e) {
            Log.e(TAG, "Execution failed: " + command, e);
        }
    }

    /**
     * Clears all pending commands in the queue.
     */
    public static void clearQueue() { queue.clear(); }

    public static void closeShell() {
        // No persistent shell to close
    }
}
