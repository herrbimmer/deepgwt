package com.havalh6.viewer;

import android.media.audiofx.Visualizer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import java.util.Locale;

final class MediaAudioVisualizer {
    static final int BARS = 24;
    private static final float FFT_GAIN = 1.6f;
    private static final float FFT_GAMMA = 0.62f;
    private static final float FFT_USEFUL_MEAN = 0.06f;
    private static final float FFT_USEFUL_SPREAD = 0.04f;
    private static final float LEVEL_CAP = 0.78f;
    private static final long SILENT_GRACE_MS = 1500;
    private static final float SILENT_MAG = 0.02f;
    private static final long STATUS_LOG_MS = 2000;
    static final String TAG = "H6Viz";
    private static final float WAVE_GAIN = 1.35f;
    private static final float WAVE_GAMMA = 0.7f;
    private int captureFrames;
    private boolean enabled;
    private float lastFftMean;
    private float lastFftPeak;
    private float lastFftSpread;
    private long lastNonSilentAt;
    private long lastStatusAt;
    private boolean lastUsedFft;
    private float lastWaveRms;
    private Runnable onUpdate;
    private Visualizer visualizer;
    private boolean wanted;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final float[] levels = new float[24];
    private final float[] fftBars = new float[24];
    private final float[] waveBars = new float[24];
    private final Object lock = new Object();
    private String lastStatus = "";

    MediaAudioVisualizer() {
    }

    private static float cap(float f) {
        if (f < 0.0f) {
            return 0.0f;
        }
        if (f > 0.78f) {
            return 0.78f;
        }
        return f;
    }

    private void onFft(byte[] bArr) {
        int length;
        float f;
        if (bArr == null || bArr.length < 4 || (length = (bArr.length / 2) - 1) < 24) {
            return;
        }
        float f2 = 0.0f;
        float f3 = 0.0f;
        float f4 = 0.0f;
        float f5 = 1.0f;
        float f6 = 0.0f;
        int i = 0;
        while (i < 24) {
            int iPow = ((int) (Math.pow(i / 24, 1.55d) * ((double) (length - 1)))) + 1;
            float f7 = f4;
            float f8 = f5;
            int iPow2 = ((int) (Math.pow((i + 1) / 24, 1.55d) * ((double) (length - 1)))) + 1;
            if (iPow2 <= iPow) {
                iPow2 = iPow + 1;
            }
            if (iPow2 > length) {
                iPow2 = length;
            }
            float f9 = 0.0f;
            float f10 = 0.0f;
            int i2 = 0;
            int i3 = length;
            int i4 = iPow;
            while (true) {
                if (i4 >= iPow2) {
                    f = f2;
                    break;
                }
                int i5 = i4 * 2;
                int i6 = i4;
                f = f2;
                if (i5 + 1 >= bArr.length) {
                    break;
                }
                int i7 = iPow2;
                float f11 = f9;
                float f12 = f6;
                int i8 = i;
                float fHypot = ((float) Math.hypot(bArr[i5], bArr[i5 + 1])) / 128.0f;
                f10 += fHypot;
                f9 = fHypot > f11 ? fHypot : f11;
                i2++;
                i4 = i6 + 1;
                f2 = f;
                iPow2 = i7;
                f6 = f12;
                i = i8;
            }
            float f13 = i2 > 0 ? f10 / i2 : 0.0f;
            float fCap = cap(shape(1.6f * ((0.72f * f13) + (f9 * 0.28f)), 0.62f));
            this.fftBars[i] = fCap;
            float f14 = f7 + fCap;
            float f15 = f + f13;
            if (f9 > f3) {
                f3 = f9;
            }
            if (fCap < f8) {
                f8 = fCap;
            }
            if (fCap > f6) {
                f6 = fCap;
            }
            i++;
            f4 = f14;
            f2 = f15;
            length = i3;
            f5 = f8;
        }
        this.lastFftMean = f4 / 24;
        this.lastFftPeak = f3;
        this.lastFftSpread = f6 - f5;
        if (f3 >= 0.02f) {
            this.lastNonSilentAt = SystemClock.uptimeMillis();
        }
        publish();
    }

    private void onWaveform(byte[] bArr) {
        if (bArr == null || bArr.length < 24) {
            return;
        }
        int iMax = Math.max(1, bArr.length / 24);
        float f = 0.0f;
        for (int i = 0; i < 24; i++) {
            int i2 = i * iMax;
            int iMin = Math.min(bArr.length, i2 + iMax);
            float f2 = 0.0f;
            int i3 = 0;
            for (int i4 = i2; i4 < iMin; i4++) {
                float fAbs = Math.abs((bArr[i4] & 255) - 128) / 128.0f;
                f2 += fAbs * fAbs;
                i3++;
            }
            f += f2;
            this.waveBars[i] = cap(shape(1.35f * (i3 > 0 ? (float) Math.sqrt(f2 / i3) : 0.0f), 0.7f) * (((1.0f - (i / Math.max(1, 24 - 1))) * 0.72f) + 0.28f));
        }
        this.lastWaveRms = (float) Math.sqrt(f / Math.max(1, bArr.length));
        if (this.lastWaveRms >= 0.02f) {
            this.lastNonSilentAt = SystemClock.uptimeMillis();
        }
        publish();
    }

    private void publish() {
        this.captureFrames++;
        boolean z = this.lastFftMean >= 0.06f && this.lastFftSpread >= 0.04f;
        this.lastUsedFft = z;
        synchronized (this.lock) {
            try {
                System.arraycopy(z ? this.fftBars : this.waveBars, 0, this.levels, 0, 24);
            } catch (Throwable th) {
                throw th;
            }
        }
        if (SystemClock.uptimeMillis() - this.lastStatusAt >= 2000) {
            if (hasLiveAudio()) {
                status(String.format(Locale.US, "live src=%s frames=%d fftMean=%.3f spread=%.3f waveRms=%.3f", z ? "fft" : "wave", Integer.valueOf(this.captureFrames), Float.valueOf(this.lastFftMean), Float.valueOf(this.lastFftSpread), Float.valueOf(this.lastWaveRms)));
            } else {
                status(String.format(Locale.US, "silent frames=%d fftMean=%.3f waveRms=%.3f (session 0 empty?)", Integer.valueOf(this.captureFrames), Float.valueOf(this.lastFftMean), Float.valueOf(this.lastWaveRms)));
            }
        }
        Runnable runnable = this.onUpdate;
        if (runnable != null) {
            this.main.post(runnable);
        }
    }

    private void releaseViz() {
        this.enabled = false;
        Visualizer visualizer = this.visualizer;
        this.visualizer = null;
        if (visualizer == null) {
            return;
        }
        try {
            visualizer.setEnabled(false);
        } catch (Throwable th) {
        }
        try {
            visualizer.setDataCaptureListener(null, 0, false, false);
        } catch (Throwable th2) {
        }
        try {
            visualizer.release();
        } catch (Throwable th3) {
        }
    }

    private static float shape(float f, float f2) {
        if (f <= 0.0f) {
            return 0.0f;
        }
        if (f > 1.0f) {
            f = 1.0f;
        }
        return (float) Math.pow(f, f2);
    }

    private void status(String lastStatus) {
        this.lastStatus = lastStatus;
        this.lastStatusAt = SystemClock.uptimeMillis();
        Log.w("H6Viz", lastStatus);
    }

    boolean copyLiveLevels(float[] fArr) {
        if (fArr == null || fArr.length < 24 || !hasLiveAudio()) {
            return false;
        }
        synchronized (this.lock) {
            System.arraycopy(this.levels, 0, fArr, 0, 24);
        }
        return true;
    }

    boolean hasLiveAudio() {
        if (this.enabled) {
            return this.lastNonSilentAt > 0 && SystemClock.uptimeMillis() - this.lastNonSilentAt < 1500;
        }
        return false;
    }

    void release() {
        this.wanted = false;
        this.onUpdate = null;
        releaseViz();
    }

    void setOnUpdate(Runnable onUpdate) {
        this.onUpdate = onUpdate;
    }

    void setWanted(boolean wanted) {
        if (this.wanted == wanted) {
            return;
        }
        this.wanted = wanted;
        if (wanted) {
            start();
        } else {
            stop();
        }
    }

    void start() {
        this.wanted = true;
        if (this.enabled) {
            return;
        }
        try {
            Visualizer visualizer = new Visualizer(0);
            int[] captureSizeRange = Visualizer.getCaptureSizeRange();
            int i = captureSizeRange[1];
            if (i > 1024 && captureSizeRange[0] <= 1024) {
                i = 1024;
            }
            visualizer.setCaptureSize(i);
            int iMin = Math.min(Visualizer.getMaxCaptureRate(), 15000);
            int dataCaptureListener = visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override // android.media.audiofx.Visualizer.OnDataCaptureListener
                public void onFftDataCapture(Visualizer visualizer, byte[] bArr, int i) {
                    MediaAudioVisualizer.this.onFft(bArr);
                }

                @Override // android.media.audiofx.Visualizer.OnDataCaptureListener
                public void onWaveFormDataCapture(Visualizer visualizer, byte[] bArr, int i) {
                    MediaAudioVisualizer.this.onWaveform(bArr);
                }
            }, iMin, true, true);
            if (dataCaptureListener != 0) {
                visualizer.release();
                status("listener-failed code=" + dataCaptureListener);
                return;
            }
            visualizer.setEnabled(true);
            this.visualizer = visualizer;
            this.enabled = true;
            this.captureFrames = 0;
            this.lastNonSilentAt = 0L;
            status("started session=0 size=" + i + " rateHz=" + (iMin / 1000));
        } catch (Throwable th) {
            this.enabled = false;
            this.visualizer = null;
            status("create-failed " + th.getClass().getSimpleName() + ": " + th.getMessage());
        }
    }

    void stop() {
        this.wanted = false;
        releaseViz();
        if ("stopped".equals(this.lastStatus)) {
            return;
        }
        status("stopped");
    }
}
