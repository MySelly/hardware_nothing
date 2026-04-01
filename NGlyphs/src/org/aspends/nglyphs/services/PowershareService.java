package org.aspends.nglyphs.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.FileObserver;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import org.aspends.nglyphs.R;
import org.aspends.nglyphs.core.AnimationManager;
import org.aspends.nglyphs.core.GlyphManagerV2;

public class PowershareService extends Service {
    private static final String TAG = "GlyphPowershareService";

    private static final String POWERSHARE_ACTIVE = "/sys/class/qcom-battery/wls_reverse_status";
    private static final String POWERSHARE_ENABLED = "/sys/class/qcom-battery/wireless_boost_en";

    private SharedPreferences prefs;
    private PowerManager.WakeLock wakeLock;
    private PowershareActiveObserver activeObserver;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(getString(R.string.pref_file), MODE_PRIVATE);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        activeObserver = new PowershareActiveObserver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!prefs.getBoolean("master_allow", false)
                || !prefs.getBoolean("powershare_glyph_enabled", false)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        enabledObserver.startWatching();
        activeObserver.startWatching();

        return START_STICKY;
    }

    private void onPowershareEnabled() { activeObserver.continueWatching(); }

    private void onPowershareDisabled() {
        activeObserver.pauseWatching();
        stopBreathing();
    }

    private final FileObserver enabledObserver =
            new FileObserver(POWERSHARE_ENABLED, FileObserver.MODIFY) {
                @Override
                public void onEvent(int event, String file) {
                    checkEnabled();
                }

                @Override
                public void startWatching() {
                    checkEnabled();
                    super.startWatching();
                }

                private void checkEnabled() {
                    if (readSysfsInt(POWERSHARE_ENABLED) == 1) {
                        onPowershareEnabled();
                    } else {
                        onPowershareDisabled();
                    }
                }
            };

    private volatile boolean animating;
    private volatile Thread breathThread;

    private void startBreathing() {
        if (animating)
            return;

        animating = true;

        breathThread = new Thread(() -> {
            try {
                wakeLock.acquire(3000);
                while (animating && readSysfsInt(POWERSHARE_ACTIVE) == 1) {
                    for (int b = 0; b <= GlyphManagerV2.MAX_BRIGHTNESS && animating; b += 80) {
                        GlyphManagerV2.getInstance().setBrightness(GlyphManagerV2.Glyph.MAIN, b);
                        Thread.sleep(16);
                    }
                    for (int b = GlyphManagerV2.MAX_BRIGHTNESS; b >= 0 && animating; b -= 80) {
                        GlyphManagerV2.getInstance().setBrightness(GlyphManagerV2.Glyph.MAIN, b);
                        Thread.sleep(16);
                    }
                }
            } catch (InterruptedException ignored) {
            } finally {
                GlyphManagerV2.getInstance().setBrightness(GlyphManagerV2.Glyph.MAIN, 0);
                animating = false;
                if (wakeLock != null && wakeLock.isHeld()) {
                    try {
                        wakeLock.release();
                    } catch (Exception ignored) {
                    }
                }
            }
        });
        breathThread.start();
    }

    private void stopBreathing() {
        animating = false;
        if (breathThread != null) {
            breathThread.interrupt();
            breathThread = null;
        }
        GlyphManagerV2.getInstance().setBrightness(GlyphManagerV2.Glyph.MAIN, 0);
    }

    private class PowershareActiveObserver extends Thread {
        private boolean lastState = false;
        private volatile boolean pause = true;
        private volatile boolean ended = false;
        private final Object lock = new Object();

        public void startWatching() {
            if (isAlive())
                return;
            start();
        }

        public void continueWatching() {
            if (!pause)
                return;
            pause = false;
            synchronized (lock) {
                lock.notify();
            }
        }

        public void pauseWatching() {
            if (pause)
                return;
            lastState = false;
            pause = true;
        }

        public void stopWatching() {
            if (pause)
                continueWatching();
            ended = true;
        }

        @Override
        public void run() {
            while (!ended) {
                synchronized (lock) {
                    if (pause) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }
                if (readSysfsInt(POWERSHARE_ACTIVE) == 1) {
                    if (!lastState) {
                        lastState = true;
                        startBreathing();
                    }
                } else {
                    if (lastState) {
                        lastState = false;
                        stopBreathing();
                    }
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    private static int readSysfsInt(String path) {
        try (java.io.BufferedReader reader =
                        new java.io.BufferedReader(new java.io.FileReader(path))) {
            String line = reader.readLine();
            if (line != null) {
                line = line.trim().replace("0x", "");
                return Integer.parseInt(line);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to read " + path + ": " + e.getMessage());
        }
        return 0;
    }

    @Override
    public void onDestroy() {
        enabledObserver.stopWatching();
        if (activeObserver != null) {
            activeObserver.stopWatching();
        }
        stopBreathing();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
