package org.aspends.nglyphs.services;

import android.app.ActivityManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.audiofx.Visualizer;
import android.os.IBinder;
import java.util.Arrays;
import java.util.List;
import org.aspends.nglyphs.R;
import org.aspends.nglyphs.core.AnimationManager;
import org.aspends.nglyphs.core.GlyphManagerV2;

public class AssistantInteractionService extends Service {
    private static final String TAG = "GlyphAssistantService";

    private static final List<String> ASSISTANT_PACKAGES = Arrays.asList(
            "com.google.android.googlequicksearchbox", "com.google.android.apps.gemini");

    private volatile boolean running;
    private volatile boolean animating;
    private boolean isInteractionActive;
    private Thread pollThread;
    private SharedPreferences prefs;

    private Visualizer mVisualizer;
    private final float[] smoothed = new float[9]; // DOT + 8 LINE LEDs
    private static final float ATTACK = 0.7f;
    private static final float DECAY = 0.75f;

    // Frequency boundaries for 9 zones mapped to DOT + LINE strip
    private static final int[] FREQ_BOUNDARIES = {
            200, 400, 800, 1200, 2000, 3000, 5000, 8000, 14000};

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(getString(R.string.pref_file), MODE_PRIVATE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!prefs.getBoolean("master_allow", false)
                || !prefs.getBoolean("assistant_animations_enabled", false)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (pollThread == null || !pollThread.isAlive()) {
            running = true;
            pollThread = new Thread(this::pollLoop);
            pollThread.start();
        }

        return START_STICKY;
    }

    private void pollLoop() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        while (running) {
            try {
                String fg = getForegroundPackage(am);
                boolean assistantForeground = fg != null && ASSISTANT_PACKAGES.contains(fg);

                if (assistantForeground && !isInteractionActive) {
                    startSpeechVisualizer();
                } else if (!assistantForeground && isInteractionActive) {
                    stopSpeechVisualizer();
                }

                Thread.sleep(300);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private String getForegroundPackage(ActivityManager am) {
        if (am == null)
            return null;
        List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
        if (tasks != null && !tasks.isEmpty()) {
            ComponentName topActivity = tasks.get(0).topActivity;
            if (topActivity != null) {
                return topActivity.getPackageName();
            }
        }
        return null;
    }

    private void startSpeechVisualizer() {
        if (!AnimationManager.requestLockExternal(AnimationManager.PRIORITY_ASSISTANT))
            return;

        isInteractionActive = true;
        animating = true;
        AnimationManager.setAnimationRunning(true);
        Arrays.fill(smoothed, 0f);

        try {
            mVisualizer = new Visualizer(0);
            int captureSize = Visualizer.getCaptureSizeRange()[1];
            mVisualizer.setCaptureSize(captureSize);

            mVisualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(
                        Visualizer visualizer, byte[] waveform, int samplingRate) {}

                @Override
                public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
                    if (animating) {
                        processSpeechFFT(fft, samplingRate);
                    }
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true);

            mVisualizer.setEnabled(true);
        } catch (Exception e) {
            // Fallback: if visualizer fails, stop cleanly
            stopSpeechVisualizer();
        }
    }

    private void processSpeechFFT(byte[] fft, int samplingRate) {
        int captureSize = mVisualizer.getCaptureSize();
        int halfSize = captureSize / 2;
        double hzPerBin = (samplingRate / 2000.0) / halfSize;

        double[] magnitudes = new double[halfSize];
        magnitudes[0] = Math.abs(fft[0]);
        for (int i = 1; i < halfSize; i++) {
            double re = fft[2 * i];
            double im = fft[2 * i + 1];
            magnitudes[i] = Math.sqrt(re * re + im * im);
        }

        int[] frame = new int[15];
        int prevBin = 0;
        int threshold = (int) (GlyphManagerV2.MAX_BRIGHTNESS * 0.06f);

        for (int z = 0; z < 9; z++) {
            int endBin = Math.min((int) (FREQ_BOUNDARIES[z] / hzPerBin), halfSize);
            if (endBin <= prevBin)
                endBin = prevBin + 1;
            if (endBin > halfSize)
                endBin = halfSize;

            double sum = 0;
            int count = 0;
            for (int i = prevBin; i < endBin; i++) {
                sum += magnitudes[i];
                count++;
            }
            float avg = count > 0 ? (float) (sum / count) : 0;
            float normalized = Math.min(1.0f, avg / 40.0f);

            if (normalized > smoothed[z]) {
                smoothed[z] = smoothed[z] + (normalized - smoothed[z]) * ATTACK;
            } else {
                smoothed[z] *= DECAY;
            }

            int brightness = (int) (smoothed[z] * GlyphManagerV2.MAX_BRIGHTNESS);
            if (brightness < threshold)
                brightness = 0;

            if (z == 0) {
                frame[6] = brightness; // DOT
            } else {
                frame[6 + z] = brightness; // LINE indices 7-14
            }
            prevBin = endBin;
        }

        GlyphManagerV2.getInstance().setFrame(frame);
    }

    private void stopSpeechVisualizer() {
        if (!isInteractionActive)
            return;
        isInteractionActive = false;
        animating = false;

        if (mVisualizer != null) {
            try {
                mVisualizer.setEnabled(false);
                mVisualizer.release();
            } catch (Exception ignored) {
            }
            mVisualizer = null;
        }

        GlyphManagerV2.getInstance().setFrame(new int[15]);
        AnimationManager.setAnimationRunning(false);
        AnimationManager.releaseLockExternal(AnimationManager.PRIORITY_ASSISTANT);
    }

    @Override
    public void onDestroy() {
        running = false;
        if (pollThread != null) {
            pollThread.interrupt();
        }
        stopSpeechVisualizer();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
