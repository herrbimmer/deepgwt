package com.havalh6.viewer;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import java.lang.ref.WeakReference;
import java.util.Objects;

public final class DesktopSwitcherHitService extends AccessibilityService {
    private static final String TAG = "H6HitProxy";
    private static volatile WeakReference<MainActivity> host = new WeakReference<>(null);
    private static volatile DesktopSwitcherHitService instance;
    private static int pendingH;
    private static int pendingW;
    private static boolean pendingWanted;
    private static int pendingX;
    private static int pendingY;
    private boolean attached;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager.LayoutParams lp;
    private View proxy;

    static void applyFromViewer(MainActivity mainActivity, int i, int i2, int i3, int i4, boolean z) {
        host = new WeakReference<>(mainActivity);
        pendingX = i;
        pendingY = i2;
        pendingW = i3;
        pendingH = i4;
        pendingWanted = z;
        DesktopSwitcherHitService desktopSwitcherHitService = instance;
        if (desktopSwitcherHitService != null) {
            Handler handler = desktopSwitcherHitService.handler;
            Objects.requireNonNull(desktopSwitcherHitService);
            handler.post(() -> desktopSwitcherHitService.syncProxy());
        }
    }

    private void dismissProxy() {
        if (!this.attached || this.proxy == null) {
            this.attached = false;
            return;
        }
        try {
            WindowManager windowManager = (WindowManager) getSystemService("window");
            if (windowManager != null) {
                windowManager.removeView(this.proxy);
            }
        } catch (Exception e) {
        }
        this.attached = false;
    }

    private void syncProxy() {
        if (!pendingWanted) {
            dismissProxy();
            return;
        }
        WindowManager windowManager = (WindowManager) getSystemService("window");
        if (windowManager == null) {
            return;
        }
        if (this.proxy == null) {
            View view = new View(this);
            view.setBackgroundColor(0);
            view.setOnTouchListener((view3, motionEvent) -> {
                MainActivity mainActivity = host.get();
                return mainActivity != null && mainActivity.dispatchDesktopSwitcherHit(motionEvent);
            });
            this.proxy = view;
        }
        if (this.lp == null) {
            this.lp = new WindowManager.LayoutParams(pendingW, pendingH, 2032, 16778024, -3);
            this.lp.gravity = 8388659;
        }
        this.lp.x = pendingX;
        this.lp.y = pendingY;
        this.lp.width = pendingW;
        this.lp.height = pendingH;
        try {
            boolean z = this.attached;
            View view2 = this.proxy;
            if (z) {
                windowManager.updateViewLayout(view2, this.lp);
            } else {
                windowManager.addView(view2, this.lp);
                this.attached = true;
                Log.w("H6HitProxy", "Hit proxy overlay " + pendingX + "," + pendingY + " " + pendingW + "x" + pendingH);
            }
        } catch (Exception e) {
            Log.w("H6HitProxy", "Hit proxy overlay failed", e);
            this.attached = false;
        }
    }

    @Override // android.accessibilityservice.AccessibilityService
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
    }

    @Override // android.app.Service
    public void onDestroy() {
        if (instance == this) {
            instance = null;
        }
        this.handler.removeCallbacksAndMessages(null);
        dismissProxy();
        super.onDestroy();
    }

    @Override // android.accessibilityservice.AccessibilityService
    public void onInterrupt() {
    }

    @Override // android.accessibilityservice.AccessibilityService
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        this.handler.post(() -> this.syncProxy());
        Log.w("H6HitProxy", "Desktop switcher hit service connected");
    }
}
