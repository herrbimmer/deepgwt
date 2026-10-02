package com.havalh6.viewer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Property;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import br.com.opengw.R;

public final class MainActivity extends Activity {
    private static final String ACTION_CAR_DATA_UPDATE = "br.com.opengw.CAR_DATA_UPDATE";
    private static final String ACTION_REQUEST_SNAPSHOT = "com.haval.vehicle.REQUEST_SNAPSHOT";
    private static final String ACTION_RESOLVE_TASK = "br.com.opengw.ACTION_RESOLVE_TASK";
    private static final String ACTION_SET_TASK_BOUNDS = "br.com.opengw.ACTION_SET_TASK_BOUNDS";
    private static final String ACTION_TASK_RESOLVED = "br.com.opengw.ACTION_TASK_RESOLVED";
    private static final String ACTION_UPDATE_CAR_DATA = "br.com.opengw.ACTION_UPDATE_CAR_DATA";
    private static final String ACTION_VEHICLE_COMMAND = "br.com.opengw.ACTION_VEHICLE_COMMAND";
    private static final String ACTION_VEHICLE_EVENT_CHANGED = "com.haval.vehicle.EVENT_CHANGED";
    private static final String ANDROID_SETTINGS_ACTIVITY = "com.android.settings.Settings";
    private static final String ANDROID_SETTINGS_PACKAGE = "com.android.settings";
    private static final String ASSET_HOST = "appassets.androidplatform.net";
    private static final String ASSET_PREFIX = "/assets/";
    private static final int BOOT_PROGRESS_LINE_TOP_Y = 655;
    private static final String CAR_SETTINGS_PACKAGE = "com.android.car.settings";
    private static final int DEFAULT_SHELL_BG = -16777216;
    private static final String DOCK_SURFACE_CARDS = "cards";
    private static final String DOCK_SURFACE_LAUNCHER = "launcher";
    private static final String DRIVING_SET_PREFIX = "drivingSet:";
    private static final String EXTRA_LAUNCH_FREEFORM = "launch_freeform";
    private static final String EXTRA_RAISE_TASK_ID = "raise_task_id";
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final String KEY_LAUNCH_BOUNDS = "android:activity.launchBounds";
    private static final String KEY_LAUNCH_WINDOWING_MODE = "android.activity.windowingMode";
    private static final float LAUNCHER_STRIP_HEIGHT_DP = 148.0f;
    private static final int MEDIA_ART_GLYPH_PAD_DP = 32;
    private static final int MEDIA_BTN_DP = 40;
    private static final int MEDIA_BTN_PRIMARY_DP = 48;
    private static final int MEDIA_CARD_H_DP = 124;
    private static final float MEDIA_CARD_RADIUS_DP = 16.0f;
    private static final int MEDIA_CARD_W_DP = 380;
    private static final float NAV_RAIL_FALLBACK_DP = 128.0f;
    private static final String PERF_TAG = "H6Perf";
    private static final String PREFS_SHELL = "opengw_lancador";
    private static final String PREF_DOCK_SURFACE = "dockSurfaceMode";
    private static final long RAISE_DELAY_MS = 60;
    private static final long RAISE_ECHO_COOLDOWN_MS = 350;
    private static final int REQ_PLACE_LOCATION = 7102;
    private static final String SHELL_APPS = "appsOnly";
    private static final String SHELL_APP_CAR = "appCar";
    private static final float SLOT_DOCK_GAP_DP = 16.0f;
    private static final float SOURCE_CHIP_ICON_DP = 30.0f;
    private static final String STRIP_APPS = "apps";
    private static final String STRIP_CONFIG = "config";
    private static final String STRIP_LAYOUT = "layout";
    private static final String TAG = "H6Viewer";
    private static final String UI_MODE_FLOATING = "floating";
    private static final String UI_MODE_FULL = "full";
    private static final String UI_MODE_INSET = "inset";
    private static final String VIEWER_ASSET_REVISION = "vehicle-console-v47-aa-tbt-gmaps";
    private static final String VIEWER_URL = "https://appassets.androidplatform.net/assets/www/index.html?android&assets=vehicle-console-v47-aa-tbt-gmaps";
    private static final int WINDOWING_MODE_FREEFORM = 5;
    private static final int WINDOWING_MODE_FULLSCREEN = 1;
    private int activeDesktopIndex;
    private TextView activeDesktopMeta;
    private TextView activeDesktopTitle;
    private ComponentName activeMediaComponent;
    private ComponentName activePopupComponent;
    private PendingIntent apiCallerToken;
    private TextView appsFabButton;
    private int appsFabDownLpX;
    private int appsFabDownLpY;
    private float appsFabDownRawX;
    private float appsFabDownRawY;
    private boolean appsFabDragging;
    private WindowManager.LayoutParams appsFabLp;
    private LinearLayout appsFabMenu;
    private boolean appsFabMenuOpen;
    private boolean appsFabOverlayAttached;
    private View appsFabRoot;
    private View appsFabScrim;
    private boolean appsFabScrimAttached;
    private WindowManager.LayoutParams appsFabScrimLp;
    private boolean appsFabUsesSystemOverlay;
    private boolean appsOnlyRestoreDone;
    private View appsScrollView;
    private View bootCenterLoader;
    private boolean bootHudCenterMode;
    private View bootProgressFill;
    private View bootProgressTrack;
    private int bottomCardLimit;
    private boolean bottomCardsConfigured;
    private View cardsScrollView;
    private boolean chromeOnTop;
    private Runnable chromeOnTopTimeout;
    private View configContentScroll;
    private View contentHost;
    private int desktopHitH;
    private int desktopHitW;
    private boolean desktopHitWanted;
    private int desktopHitX;
    private int desktopHitY;
    private boolean desktopStudioOpen;
    private int dockCellPx;
    private PopupWindow dockCustomizeSheet;
    private PopupWindow dockEditMenu;
    private int dockIconPx;
    private int dockIconRowTopPadPx;
    private int dockToolGlyphPx;
    private Bitmap dockXrayGlyphSrc;
    private boolean drawerExpanded;
    private Boolean emulatorDevice;
    private MotionTrailLayout gwmHubItem;
    private PopupWindow gwmHubMenu;
    private View reserveBand;
    private long lastLeftTaskRequestMs;
    private JSONObject lastMediaPayload;
    private long lastOverlayRaiseMs;
    private long lastRaiseCompletedMs;
    private long lastRightTaskRequestMs;
    private long lastSlotRestartMs;
    private View launchAnchor;
    private boolean launcherHiddenByPage;
    private boolean launcherRevealed;
    private View layoutAddWidgetChip;
    private View layoutCardsSurfaceChip;
    private View layoutCenterFillChip;
    private ImageView layoutCenterFillGear;
    private ImageView layoutCenterFillIcon;
    private TextView layoutCenterFillLabel;
    private View layoutContentRow;
    private View layoutLauncherSurfaceChip;
    private View layoutThemeChip;
    private TextView layoutThemeLabel;
    private TextView layoutTipLabel;
    private MediaAudioVisualizer mediaAudioViz;
    private View mediaLaunchAnchor;
    private boolean mediaVizPermissionLogged;
    private View modeAppsBtn;
    private View modeCollapsedBtn;
    private ImageView modeCollapsedIcon;
    private View modeConfigBtn;
    private LinearLayout modeDrawer;
    private View modeLayoutBtn;
    private View modeSurfaceBtn;
    private long overlayLaunchUntilMs;
    private long overlayRaiseGraceUntilMs;
    private ValueCallback<Uri[]> pendingFiles;
    private ProjectionPresence.Kind pendingProjectionKind;
    private Runnable pendingProjectionTimeout;
    private Runnable pinBoundsRunnable;
    private Runnable pinMediaBoundsRunnable;
    private PlaceGlance placeGlance;
    private boolean placeLocationAsked;
    private MotionTrailLayout projectionItem;
    private ProjectionPresence projectionPresence;
    private LinearLayout quickCardsRow;
    private TextView quickClimateValue;
    private Bitmap quickClockFaceBitmap;
    private TextView quickConsumptionValue;
    private ImageView quickMediaAppIcon;
    private View quickMediaAppRow;
    private ImageView quickMediaArt;
    private TextView quickMediaArtist;
    private boolean quickMediaAvailable;
    private QuickMediaBarsView quickMediaBars;
    private boolean quickMediaCanLaunch;
    private SourceChip quickMediaChip;
    private boolean quickMediaHasArt;
    private ImageView quickMediaPlayPause;
    private boolean quickMediaPlaying;
    private TextView quickMediaTitle;
    private PopupWindow quickMenuWindow;
    private boolean railEditMode;
    private Runnable raiseOverlayRunnable;
    private FrameLayout rootLayout;
    private MotionTrailLayout sideBySideItem;
    private boolean splashDropScheduled;
    private boolean splashFadeStarted;
    private ImageView splashFadeView;
    private Bitmap splashHoldFrame;
    private boolean splashOverlayEnded;
    private View splashSkipBtn;
    private View stripContainer;
    private View stripRow;
    private Boolean systemActivityServiceAvailable;
    private Boolean systemTaskListAvailable;
    private Object taskStackListener;
    private TripRecorder tripRecorder;
    private PopupWindow unhidePicker;
    private WebView webView;
    /** O processo de render do WebView morreu e esta Activity está sendo recriada (Alpha 8.9). */
    private boolean rendererPerdido;
    private static final String ESTADO_RENDERER_PERDIDO = "h6_renderer_perdido";
    /** Quedas recentes do renderizador, para não recriar em laço se ele cair logo de novo. */
    private static final long[] QUEDAS_DO_RENDERER_MS = new long[3];
    private static int proximaQuedaDoRenderer;
    private static final long JANELA_DAS_QUEDAS_MS = 5 * 60_000L;
    private static final long ESPERA_APOS_QUEDAS_SEGUIDAS_MS = 30_000L;
    private int widestNavInsetPx;
    private int widestTopInsetPx;
    private View workspaceActionsFace;
    private TextView workspaceHeading;
    private View workspaceLayoutFace;
    private boolean workspaceLayoutMode;
    private static final long[] SNAPSHOT_RETRY_DELAYS_MS = {4000, 15000};
    private static final Set<String> BOTTOM_CARD_ACTIONS = new HashSet(Arrays.asList("ac", "addWidget", "openDesktopStudio", "openClockSettings", "openClockCardSettings", "previousDesktop", "nextDesktop", "toggleDockMode", "showLauncher", "showCards", "cycleWidgetTheme", "toggleCenterFill", "configureWallpaper", "closePanel", "openClimate", "openConsumption", "openNavigation", "openPower", "openRange", "openTires", "openVehicleStatus", "openDriving", "openDrivingOnePedal", "openMedia", "openMediaApp", "cycleDriveMode", "cyclePowerMode", "cycleRegenMode", "openRoofControls"));
    private static final Set<String> VEHICLE_COMMANDS_WITHOUT_VALUE = new HashSet(Arrays.asList("toggle_doors_all", "toggle_door_fl", "toggle_door_fr", "toggle_door_rl", "toggle_door_rr", "toggle_windows", "toggle_window_fl", "toggle_window_fr", "toggle_window_rl", "toggle_window_rr", "toggle_trunk", "toggle_sunroof", "toggle_curtain", "open_windows", "close_windows", "open_sunroof", "close_sunroof", "open_curtain", "close_curtain"));
    private static final Set<String> VEHICLE_LEVEL_COMMANDS = new HashSet(Arrays.asList("set_sunroof_level", "set_curtain_level"));
    private static final Set<String> WRITABLE_CAR_KEYS = new HashSet(Arrays.asList("car.drive_setting.drive_mode", "car.drive_setting.steering_wheel_assist_mode", "car.drive_setting.esp_enable", "car.ev_setting.power_model_config", "car.ev_setting.power_reserve_config", "car.ev_setting.charge_soc_target_config", "car.ev_setting.energy_recovery_level", "car.ev.setting.pedal_control_enable", "car.hvac.power_mode", "car.hvac.fan_speed", "car.hvac.driver_temperature", "car.hvac.cycle_mode", "car.hvac.auto_enable", "car.hvac.anion_enable"));
    private static final String[] PINNED_PACKAGES = {"com.beantechs.vehiclecenter", "com.beantechs.settings", "com.beantechs.energyassistant", "com.beantechs.launcher"};
    private static final String[][] EMULATOR_EXTRA_GWM_STUBS = {new String[]{"com.beantechs.fake.mediacenter", "Media Center"}, new String[]{"com.beantechs.fake.navigation", "Navegação GWM"}};
    private static final Set<String> IGNORED_PACKAGES = new HashSet(Arrays.asList("com.beantechs.hvac", "com.beantechs.btphone", "com.beantechs.drivinganalysisservice", "com.beantechs.personalcenter", "com.beantechs.operatorcenter", "com.beantechs.account", "com.beantechs.applist", "com.beantechs.guidance", "com.beantechs.fotaui", "com.beantechs.PKIMaintain", "com.beantechs.adaptertool.client", "com.beantechs.sshost.client", "com.android.car.media", "com.android.car.radio", "com.android.car.settings", "com.beantechs.mediacenter", "com.beantechs.mediacenter.h5.ui", "com.beantechs.mediacenter.h5.core", "com.android.support.car.lenspicker", "com.autolink.enginmode", "com.apical.cj1005", "com.google.android.car.kitchensink", "com.nextdoordeveloper.miperf.miperf", "com.android.systemui", "com.android.keyguard", "com.android.webview", "com.google.android.inputmethod.latin", "com.android.inputmethod.latin", "com.gwm.hvac", "com.gwm.vehicle", "com.gwm.car", "com.android.vending", "app.revanced.android.gms", "com.google.android.gms", "moe.shizuku.privileged.api", "com.ts.androidauto.app", "com.ts.androidauto", "com.ts.androidauto.projectionservice", "com.ts.carplay.app", "com.ts.carplay", "com.google.android.projection.gearhead"));
    private static final Rect LEFT_POPUP_BOUNDS = new Rect(48, 100, 740, 530);
    private static final Rect RIGHT_APP_BOUNDS = new Rect(1180, 100, 1872, 530);
    private static final Rect RIGHT_IDLE_BOUNDS = RIGHT_APP_BOUNDS;
    private static final Rect FULLSCREEN_BOUNDS = new Rect(0, 0, 1920, 720);
    private static final Rect SLOT_REFERENCE = new Rect(0, 0, 1920, 720);
    private static final Set<String> DRIVING_CARD_IDS = new HashSet(Arrays.asList("driveMode", "powerMode", "regen"));
    private final ConcurrentHashMap<String, String> telemetryCache = new ConcurrentHashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final BroadcastReceiver taskResolvedReceiver = new BroadcastReceiver() {
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !"br.com.opengw.ACTION_TASK_RESOLVED".equals(intent.getAction())) {
                return;
            }
            String stringExtra = intent.getStringExtra("package");
            int intExtra = intent.getIntExtra("taskId", -1);
            if (stringExtra == null || stringExtra.isEmpty() || intExtra < 0) {
                return;
            }
            if (MainActivity.this.pendingProjectionKind != null && MainActivity.this.pendingProjectionPackages.contains(stringExtra)) {
                Log.w("H6Viewer", "Resolved projection " + stringExtra + " -> task " + intExtra);
                MainActivity.this.mainHandler.post(() -> {
                    MainActivity.this.completeProjectionRaise(intExtra, stringExtra);
                });
                return;
            }
            boolean zEquals = stringExtra.equals(MainActivity.this.activePopupPackage);
            MainActivity mainActivity = MainActivity.this;
            if (zEquals) {
                mainActivity.activePopupTaskId = intExtra;
            } else if (!stringExtra.equals(mainActivity.activeMediaPackage)) {
                return;
            } else {
                MainActivity.this.activeMediaTaskId = intExtra;
            }
            Log.w("H6Viewer", "Resolved " + stringExtra + " -> task " + intExtra);
        }
    };
    private final BroadcastReceiver telemetryReceiver = new BroadcastReceiver() {
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            String action = intent.getAction();
            if ("com.haval.vehicle.EVENT_CHANGED".equals(action) || "br.com.opengw.CAR_DATA_UPDATE".equals(action)) {
                String stringExtra = intent.getStringExtra("key");
                String stringExtra2 = intent.getStringExtra("value");
                if (MainActivity.this.isDebuggableBuild()) {
                    Log.w("H6Viewer", "CarSignal " + action + " key=" + stringExtra + " value=" + stringExtra2);
                }
                if (stringExtra == null || stringExtra2 == null) {
                    return;
                }
                MainActivity.this.telemetryCache.put(stringExtra, stringExtra2);
                if (MainActivity.this.webView != null) {
                    MainActivity.this.webView.post(() -> {
                        MainActivity.this.webView.evaluateJavascript(String.format("if(window.onCarDataUpdate){window.onCarDataUpdate('%s','%s');}", stringExtra.replace("'", "\\'"), stringExtra2.replace("'", "\\'")), null);
                    });
                }
            }
        }
    };
    private final BroadcastReceiver clockEnvironmentReceiver = new BroadcastReceiver() {
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            String action = intent.getAction();
            if ("android.intent.action.TIME_TICK".equals(action) || "android.intent.action.TIME_SET".equals(action) || "android.intent.action.TIMEZONE_CHANGED".equals(action) || "android.intent.action.LOCALE_CHANGED".equals(action)) {
                MainActivity.this.mainHandler.post(() -> {
                    Iterator it = MainActivity.this.quickClockCards.values().iterator();
                    while (it.hasNext()) {
                        ((QuickClockCardView) it.next()).refreshNow();
                    }
                    if (MainActivity.this.webView != null) {
                        MainActivity.this.webView.evaluateJavascript("window.dispatchEvent(new Event('h6-clock-environment'))", null);
                    }
                });
            }
        }
    };
    private String activePopupPackage = "";
    private int activePopupTaskId = -1;
    private String activeMediaPackage = "";
    private int activeMediaTaskId = -1;
    private boolean taskAlwaysOnTopSupported = true;
    private final Runnable overlayWatchdog = new Runnable() {
        @Override // java.lang.Runnable
        public void run() {
            MainActivity.this.syncOverlaySlots(false);
            if (MainActivity.this.hasOverlayWindow()) {
                MainActivity.this.mainHandler.postDelayed(this, 1000L);
            }
        }
    };
    private final List<View> quickCardViews = new ArrayList();
    private final List<BottomCardDescriptor> bottomCards = new ArrayList();
    private final Map<String, TextView> quickCardValues = new HashMap();
    private final Map<String, TextView> quickCardDetails = new HashMap();
    private final Map<String, QuickCardGraphicView> quickCardGraphics = new HashMap();
    private final Map<String, QuickClockCardView> quickClockCards = new HashMap();
    private String quickClockFaceRevision = "";
    private int quickClockFaceSequence = -1;
    private final Map<String, TextView> quickCardDemoBadges = new HashMap();
    private final Map<String, View> quickCardHosts = new HashMap();
    private final Map<String, Integer> lastDrivingWash = new HashMap();
    private final List<ImageView> quickMediaButtons = new ArrayList();
    private final Map<String, SourceChip> quickCardSourceChips = new HashMap();
    private String quickMediaPackage = "";
    private String dockSurfaceMode = "launcher";
    private String activeDesktopName = "Desktop";
    private int desktopCount = 1;
    private int dockAccentColor = -13981769;
    private String centerFillMode = "car";
    private final Map<String, ImageView> dockToolIcons = new HashMap();
    private String dockModelLabel = "PHEV34";
    private String dockPerfLabel = "OFF";
    private String dockPaintHex = "#F4F5F7";
    private String dockTimeMode = "auto";
    private String dockWidgetThemeMode = "dark";
    private String dockWidgetThemeEffective = "dark";
    private boolean dockUiLight = false;
    private boolean dockFpsOn = false;
    private boolean dockXrayOn = false;
    private final Runnable mediaVizPoll = new Runnable() {
        @Override // java.lang.Runnable
        public void run() {
            MainActivity.this.syncMediaVisualizerWanted(MainActivity.this.mediaAudioWanted());
            MainActivity.this.mainHandler.postDelayed(this, 1000L);
        }
    };
    private String stripMode = "apps";
    private final List<MotionTrailLayout> launcherItems = new ArrayList();
    private final Map<String, Bitmap> packageIconBitmaps = new ConcurrentHashMap();
    private final Set<String> packageIconMiss = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingProjectionPackages = new HashSet();
    private String railFocusId = "";
    private String studioScreen = "";
    private final List<TextView> workspaceLayoutCells = new ArrayList();
    private final Map<View, Animator> railJiggles = new HashMap();
    private final boolean[] pinnedBound = new boolean[PINNED_PACKAGES.length];
    private DockAppOverrides dockAppOverrides = new DockAppOverrides(null);
    private final MotionTrailLayout[] recentItems = new MotionTrailLayout[3];
    private final List<String> recentPackages = new ArrayList();
    private final Set<String> hiddenPackages = new HashSet();
    private final Set<String> emulatorStubPackages = new HashSet();
    private final Set<String> windowPackages = new HashSet();
    private final Map<String, MotionTrailLayout> dockItemsByPackage = new LinkedHashMap();
    private final BroadcastReceiver packageRemovedReceiver = new BroadcastReceiver() {
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String schemeSpecificPart;
            if (intent == null || intent.getData() == null || (schemeSpecificPart = intent.getData().getSchemeSpecificPart()) == null || schemeSpecificPart.isEmpty()) {
                return;
            }
            MainActivity.this.mainHandler.post(() -> {
                MainActivity.this.removeDockPackage(schemeSpecificPart, false);
            });
        }
    };
    private final Runnable launcherRevealFallback = new Runnable() {
        @Override // java.lang.Runnable
        public void run() {
            if (MainActivity.this.launcherRevealed) {
                return;
            }
            Log.w("H6Viewer", "Viewer never signalled splash hand-off — revealing launcher anyway");
            MainActivity.this.revealLauncherStrip();
        }
    };
    private String shellMode = "appCar";
    private String launchSidePref = "auto";
    private boolean nextLaunchLeft = true;
    private String lastLaunchSide = "";
    private String leftSlotUse = "app";
    private String rightSlotUse = "app";
    private String splitRatio = "1:1";
    private String pendingRestoreLeft = "";
    private String pendingRestoreRight = "";
    private int appsFabPosX = Integer.MIN_VALUE;
    private int appsFabPosY = Integer.MIN_VALUE;
    private int appsFabBtnAnchorY = Integer.MIN_VALUE;
    private String lastNonAppsShellMode = "appCar";
    private final MediaNowPlaying mediaNowPlaying = new MediaNowPlaying();
    private final Runnable forceDropSplash = new Runnable() {
        @Override // java.lang.Runnable
        public void run() {
            MainActivity.this.dropSplashFromShell();
        }
    };
    private int lastRaisedTaskId = -1;
    private int loggedNavReservePx = -1;
    private int loggedOverscanBottomPx = -1;
    private String quickMenuThemeSig = "";
    private String uiMode = "floating";

    public class AppLauncherBridge {
        public AppLauncherBridge() {
        }

        @JavascriptInterface
        public void beginSplashFade(int i) {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            MainActivity.this.runOnUiThread(() -> {
                try {
                    MainActivity.this.beginSplashFade(i);
                } finally {
                    countDownLatch.countDown();
                }
            });
            try {
                countDownLatch.await(400L, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @JavascriptInterface
        public void captureDesktopSnapshot(String str, int i, int i2) {
            MainActivity.this.mainHandler.post(() -> {
                MainActivity.this.captureDesktopSnapshotNow(str, i, i2);
            });
        }

        @JavascriptInterface
        public void closeApp(String str) {
            MainActivity.this.closePopupApp(str);
        }

        @JavascriptInterface
        public void dismissOverlays() {
            MainActivity.this.mainHandler.post(() -> {
                MainActivity.this.dismissAllOverlays();
            });
        }

        @JavascriptInterface
        public void endSplashOverlay() {
            MainActivity.this.runOnUiThread(() -> MainActivity.this.endSplashOverlay());
        }

        @JavascriptInterface
        public String fetchJson(String str) {
            if (str == null) {
                return "";
            }
            String strTrim = str.trim();
            if (!strTrim.startsWith("https://www.bing.com/") && !strTrim.startsWith("https://bing.com/")) {
                return "";
            }
            HttpURLConnection httpURLConnection = null;
            try {
                httpURLConnection = (HttpURLConnection) new URL(strTrim).openConnection();
                httpURLConnection.setConnectTimeout(12000);
                httpURLConnection.setReadTimeout(12000);
                httpURLConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; HavalH6Viewer/1.0)");
                if (httpURLConnection.getResponseCode() != 200) {
                    return "";
                }
                InputStream inputStream = httpURLConnection.getInputStream();
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                byte[] bArr = new byte[4096];
                while (true) {
                    int i = inputStream.read(bArr);
                    if (i < 0) {
                        break;
                    }
                    byteArrayOutputStream.write(bArr, 0, i);
                }
                inputStream.close();
                return byteArrayOutputStream.toString(StandardCharsets.UTF_8.name());
            } catch (Exception e) {
                Log.w("H6Viewer", "fetchJson failed", e);
                return "";
            } finally {
                if (httpURLConnection != null) {
                    httpURLConnection.disconnect();
                }
            }
        }

        @JavascriptInterface
        public String getAppIcon(String str) {
            Bitmap bitmapIconBitmapForPackage = MainActivity.this.iconBitmapForPackage(str);
            if (bitmapIconBitmapForPackage == null) {
                return "";
            }
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmapIconBitmapForPackage.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
            return "data:image/png;base64," + Base64.encodeToString(byteArrayOutputStream.toByteArray(), 2);
        }

        @JavascriptInterface
        public String getClockEnvironment() {
            JSONObject jSONObject = new JSONObject();
            try {
                Locale locale = MainActivity.this.getResources().getConfiguration().locale;
                jSONObject.put("locale", locale == null ? "en-US" : locale.toLanguageTag());
                jSONObject.put("timeZone", TimeZone.getDefault().getID());
                jSONObject.put("system24", DateFormat.is24HourFormat(MainActivity.this));
            } catch (Exception e) {
                Log.w("H6Viewer", "getClockEnvironment failed", e);
            }
            return jSONObject.toString();
        }

        @JavascriptInterface
        public String getInstalledApps() {
            JSONArray jSONArray = new JSONArray();
            try {
                PackageManager packageManager = MainActivity.this.getPackageManager();
                Intent intent = new Intent("android.intent.action.MAIN", (Uri) null);
                intent.addCategory("android.intent.category.LAUNCHER");
                List<ResolveInfo> listQueryIntentActivities = packageManager.queryIntentActivities(intent, 0);
                HashSet hashSet = new HashSet();
                if (listQueryIntentActivities != null) {
                    for (ResolveInfo resolveInfo : listQueryIntentActivities) {
                        if (resolveInfo != null && resolveInfo.activityInfo != null) {
                            String str = resolveInfo.activityInfo.packageName;
                            if (!MainActivity.this.skipInAppsCatalog(str) && hashSet.add(str)) {
                                jSONArray.put(MainActivity.this.appsCatalogEntry(packageManager, resolveInfo, str));
                            }
                        }
                    }
                }
                if (MainActivity.this.isEmulatorDevice()) {
                    for (String[] strArr : MainActivity.EMULATOR_EXTRA_GWM_STUBS) {
                        String str2 = strArr[0];
                        if (!MainActivity.this.skipInAppsCatalog(str2) && hashSet.add(str2)) {
                            jSONArray.put(MainActivity.this.appsCatalogStubEntry(str2, strArr[1]));
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("H6Viewer", "Error getting installed apps", e);
            }
            return jSONArray.toString();
        }

        @JavascriptInterface
        public String getShellLayout() {
            JSONObject jSONObject = new JSONObject();
            try {
                jSONObject.put("mode", MainActivity.this.shellMode);
                jSONObject.put("splitRatio", MainActivity.this.splitRatio);
                jSONObject.put("launchSide", MainActivity.this.launchSidePref);
            } catch (JSONException e) {
                Log.w("H6Viewer", "getShellLayout failed", e);
            }
            return jSONObject.toString();
        }

        @JavascriptInterface
        public void hideBootProgress() {
            MainActivity.this.runOnUiThread(() -> {
                MainActivity.this.hideBootHud();
            });
        }

        @JavascriptInterface
        public boolean isMusicActive() {
            try {
                AudioManager audioManager = (AudioManager) MainActivity.this.getSystemService("audio");
                return audioManager != null && audioManager.isMusicActive();
            } catch (Throwable th) {
                Log.w("H6Viewer", "isMusicActive failed", th);
                return false;
            }
        }

        @JavascriptInterface
        public void launchAppInPopup(String str) {
            MainActivity.this.launchAppForPackage(str, "");
        }

        @JavascriptInterface
        public void launchAppInSlot(String str, String str2) {
            MainActivity.this.mainHandler.post(() -> {
                MainActivity.this.launchAppInSlotNative(str, str2);
            });
        }

        @JavascriptInterface
        public void launchProjection(String str) {
            MainActivity.this.mainHandler.post(() -> {
                if ("AA".equalsIgnoreCase(str) || "ANDROID_AUTO".equalsIgnoreCase(str)) {
                    MainActivity.this.launchProjection(ProjectionPresence.Kind.ANDROID_AUTO);
                } else if ("CP".equalsIgnoreCase(str) || "CARPLAY".equalsIgnoreCase(str)) {
                    MainActivity.this.launchProjection(ProjectionPresence.Kind.CARPLAY);
                }
            });
        }

        @JavascriptInterface
        public String loadWidgets() {
            return MainActivity.this.getSharedPreferences("opengw_lancador", 0).getString("widgets", "");
        }

        @JavascriptInterface
        public void maximizeApp(String str) {
            MainActivity.this.maximizePopupApp(str);
        }

        @JavascriptInterface
        public void placeBootProgress(String str) {
            MainActivity.this.runOnUiThread(() -> {
                MainActivity.this.layoutBootHud(str);
            });
        }

        @JavascriptInterface
        public void reportDesktopSwitcherHit(float f, float f2, float f3, float f4, boolean z) {
            MainActivity.this.mainHandler.post(() -> {
                MainActivity.this.updateDesktopSwitcherHitProxy(f, f2, f3, f4, z);
            });
        }

        @JavascriptInterface
        public void revealLauncher() {
            MainActivity.this.runOnUiThread(() -> MainActivity.this.revealLauncherStrip());
        }

        @JavascriptInterface
        public void saveAppsOnlyDefault() {
            MainActivity.this.mainHandler.post(() -> MainActivity.this.saveAppsOnlyDefaultNow());
        }

        @JavascriptInterface
        public void saveShellBackground(String str) {
            MainActivity.this.getSharedPreferences("opengw_lancador", 0).edit().putInt("shellBg", MainActivity.parseCssColor(str, -16777216)).apply();
        }

        @JavascriptInterface
        public void saveWidgets(String str) {
            MainActivity.this.getSharedPreferences("opengw_lancador", 0).edit().putString("widgets", str != null ? str : "").apply();
        }

        @JavascriptInterface
        public void setBootProgress(int i, String str, String str2) {
            final int int2 = Math.max(0, Math.min(100, i));
            MainActivity.this.runOnUiThread(() -> {
                MainActivity.this.showBootHud(int2);
            });
        }

        @JavascriptInterface
        public void setChromeOnTop(boolean z) {
            MainActivity.this.mainHandler.post(() -> {
                MainActivity.this.applyChromeOnTop(z);
            });
        }

        @JavascriptInterface
        public void setLaunchSide(String str) {
            MainActivity.this.mainHandler.post(() -> {
                MainActivity.this.applyLaunchSide(str);
            });
        }

        @JavascriptInterface
        public void setLauncherHidden(boolean z) {
            MainActivity.this.mainHandler.post(() -> MainActivity.this.applyLauncherHidden(z));
        }

        @JavascriptInterface
        public void setShellMode(String str) {
            MainActivity.this.mainHandler.post(() -> {
                MainActivity.this.applyShellMode(str);
            });
        }

        @JavascriptInterface
        public void setSlotUse(String str, String str2) {
            MainActivity.this.mainHandler.post(() -> {
                MainActivity.this.applySlotUse(str, str2);
            });
        }

        @JavascriptInterface
        public void setSplitRatio(String str) {
            MainActivity.this.mainHandler.post(() -> {
                MainActivity.this.applySplitRatio(str);
            });
        }

        @JavascriptInterface
        public void swapApps() {
            MainActivity.this.mainHandler.post(() -> MainActivity.this.swapSlotApps());
        }

        @JavascriptInterface
        public void updateCenterFill(String str) {
            MainActivity.this.runOnUiThread(() -> {
                MainActivity.this.applyCenterFillIndicator(str);
            });
        }

        @JavascriptInterface
        public void updateClockCardFace(String str, int i, String str2) {
            Bitmap bitmapDecodeDataUrlBitmap;
            if (str2 == null || str2.length() > 1200000 || (bitmapDecodeDataUrlBitmap = MainActivity.decodeDataUrlBitmap(str2)) == null) {
                return;
            }
            final String script = str == null ? "" : str;
            MainActivity.this.mainHandler.post(() -> {
                MainActivity.this.applyQuickClockFace(script, i, bitmapDecodeDataUrlBitmap);
            });
        }

        @JavascriptInterface
        public void updateDockIndicators(String str) {
            MainActivity.this.runOnUiThread(() -> {
                MainActivity.this.applyDockIndicators(str);
            });
        }
    }

    private static final class BottomCardDescriptor {
        final String action;
        String clockFace;
        String clockFormat;
        final String clockHourFormat;
        final int curtainLevel;
        final String dateSpineFormat;
        final String dateWording;
        final boolean demo;
        final String dialMarks;
        final String glyph;
        final String glyphText;
        final String iconAction;
        final String id;
        final String longAction;
        final List<QuickMenuRow> menu;
        final String metricA;
        final String metricB;
        final String[] openingStates;
        final double powerSoc;
        final String powerVariant;
        final String primary;
        final int progress;
        final String[] seatBeltStates;
        final String secondary;
        final boolean socKnown;
        final String splitPlates;
        final String state;
        final int sunroofLevel;
        final String title;
        final String value;
        final String[] wheelStates;
        String tirePressures = "";
        String tireTemperatures = "";
        String tirePressureUnit = "";
        int[] energyBars = new int[0];
        String appPackage = "";
        String navRemaining = "";
        String navDuration = "";
        String navEta = "";
        String navIdleCity = "";
        String navIdleAction = "";
        List<QuickMenuRow> editMenu = Collections.emptyList();

        BottomCardDescriptor(String id, String title, String value, String action, String primary, String secondary, String metricA, String metricB, int i, String state, String str10, boolean socKnown, double powerSoc, String[] wheelStates, String[] openingStates, String[] seatBeltStates, int i2, int i3, boolean demo, String str11, String str12, String str13, String str14, String str15, String str16, String str17, String str18, String str19, String str20, List<QuickMenuRow> list) {
            this.clockFace = "panorama";
            this.clockFormat = "24h";
            this.id = id;
            this.title = title;
            this.value = value;
            this.action = action;
            this.primary = primary;
            this.secondary = secondary;
            this.metricA = metricA;
            this.metricB = metricB;
            this.progress = Math.max(0, Math.min(100, i));
            this.state = state;
            this.powerVariant = str10 == null ? "phev19" : str10;
            this.socKnown = socKnown;
            this.powerSoc = powerSoc;
            this.wheelStates = wheelStates;
            this.openingStates = openingStates;
            this.seatBeltStates = seatBeltStates;
            this.sunroofLevel = Math.max(0, Math.min(100, i2));
            this.curtainLevel = Math.max(0, Math.min(100, i3));
            this.demo = demo;
            this.iconAction = str11 == null ? "" : str11;
            this.longAction = str12 == null ? "" : str12;
            this.glyph = str13 == null ? "" : str13;
            this.glyphText = str14 == null ? "" : str14;
            this.clockFace = str15 == null ? "panorama" : str15;
            this.clockFormat = "12".equals(str16) ? "12h" : "24".equals(str16) ? "24h" : "system";
            this.clockHourFormat = str16 == null ? "system" : str16;
            this.dialMarks = str17 == null ? "index" : str17;
            this.splitPlates = str18 == null ? "frost" : str18;
            this.dateSpineFormat = str19 == null ? "month-name" : str19;
            this.dateWording = str20 == null ? "short" : str20;
            this.menu = list == null ? Collections.emptyList() : list;
        }
    }

    private static class BounceHorizontalScrollView extends HorizontalScrollView {
        private static final float DAMP = 0.45f;
        private static final float MAX_PULL_DP = 56.0f;
        private float lastX;
        private final boolean maskLeftAlways;
        private final float maxPullPx;
        private float pullPx;
        private boolean pulling;
        private ValueAnimator spring;

        BounceHorizontalScrollView(Context context) {
            this(context, true);
        }

        BounceHorizontalScrollView(Context context, boolean maskLeftAlways) {
            super(context);
            this.lastX = Float.NaN;
            this.maskLeftAlways = maskLeftAlways;
            float f = context.getResources().getDisplayMetrics().density;
            this.maxPullPx = (f <= 0.0f ? 1.0f : f) * 56.0f;
            setOverScrollMode(0);
            setFillViewport(false);
            setHorizontalFadingEdgeEnabled(true);
        }

        private void applyPull(float f) {
            this.pullPx = Math.max(-this.maxPullPx, Math.min(this.maxPullPx, f));
            View viewContent = content();
            if (viewContent != null) {
                viewContent.setTranslationX(this.pullPx);
            }
        }

        private View content() {
            if (getChildCount() > 0) {
                return getChildAt(0);
            }
            return null;
        }

        private int maxScrollX() {
            View viewContent = content();
            if (viewContent == null) {
                return 0;
            }
            return Math.max(0, (viewContent.getWidth() - getWidth()) + getPaddingLeft() + getPaddingRight());
        }

        private void springBack() {
            if (this.spring != null) {
                this.spring.cancel();
            }
            if (Math.abs(this.pullPx) < 0.5f) {
                applyPull(0.0f);
                return;
            }
            this.spring = ValueAnimator.ofFloat(this.pullPx, 0.0f);
            this.spring.setDuration(280L);
            this.spring.setInterpolator(new DecelerateInterpolator(1.6f));
            this.spring.addUpdateListener(valueAnimator -> {
                applyPull(((Float) valueAnimator.getAnimatedValue()).floatValue());
            });
            this.spring.start();
        }

        @Override // android.widget.HorizontalScrollView, android.view.View
        protected float getLeftFadingEdgeStrength() {
            return (!this.maskLeftAlways && getScrollX() <= 0) ? 0.0f : 1.0f;
        }

        @Override // android.widget.HorizontalScrollView, android.view.View
        protected float getRightFadingEdgeStrength() {
            return 1.0f;
        }

        @Override // android.widget.HorizontalScrollView, android.view.ViewGroup
        public boolean onInterceptTouchEvent(MotionEvent motionEvent) {
            if (motionEvent.getActionMasked() == 0) {
                this.lastX = motionEvent.getX();
                this.pulling = false;
                if (this.spring != null) {
                    this.spring.cancel();
                }
            }
            return super.onInterceptTouchEvent(motionEvent);
        }

        @Override // android.widget.HorizontalScrollView, android.view.View
        public boolean onTouchEvent(MotionEvent motionEvent) {
            int actionMasked = motionEvent.getActionMasked();
            if (actionMasked == 0) {
                this.lastX = motionEvent.getX();
                this.pulling = false;
                if (this.spring != null) {
                    this.spring.cancel();
                }
                return super.onTouchEvent(motionEvent);
            }
            if (actionMasked != 2) {
                if (actionMasked == 1 || actionMasked == 3) {
                    this.lastX = Float.NaN;
                    if (this.pulling || Math.abs(this.pullPx) > 0.5f) {
                        this.pulling = false;
                        springBack();
                        super.onTouchEvent(motionEvent);
                        return true;
                    }
                    this.pulling = false;
                }
                return super.onTouchEvent(motionEvent);
            }
            float x = motionEvent.getX();
            if (Float.isNaN(this.lastX)) {
                this.lastX = x;
            }
            float f = x - this.lastX;
            this.lastX = x;
            int scrollX = getScrollX();
            int iMaxScrollX = maxScrollX();
            boolean z = scrollX <= 0;
            boolean z2 = scrollX >= iMaxScrollX;
            if ((z && f > 0.0f) || ((z2 && f < 0.0f) || this.pulling)) {
                this.pulling = true;
                applyPull(this.pullPx + (0.45f * f));
                return true;
            }
            if (this.pullPx != 0.0f && ((z && f < 0.0f) || (z2 && f > 0.0f))) {
                applyPull(0.0f);
                this.pulling = false;
            }
            return super.onTouchEvent(motionEvent);
        }
    }

    private static final class ClockSnapshot {
        final String dateShort;
        final String day;
        final String hour;
        final float hourAngle;
        final String minute;
        final float minuteAngle;
        final String period;
        final String spineTop;
        final String time;
        final String weekday;

        ClockSnapshot(String time, String hour, String minute, String period, String weekday, String dateShort, String day, String spineTop, float minuteAngle, float hourAngle) {
            this.time = time;
            this.hour = hour;
            this.minute = minute;
            this.period = period;
            this.weekday = weekday;
            this.dateShort = dateShort;
            this.day = day;
            this.spineTop = spineTop;
            this.minuteAngle = minuteAngle;
            this.hourAngle = hourAngle;
        }

        static ClockSnapshot from(BottomCardDescriptor bottomCardDescriptor, Context context) {
            Calendar calendar = Calendar.getInstance();
            Locale locale = context.getResources().getConfiguration().locale;
            int i = 12;
            int i2 = calendar.get(12);
            int i3 = calendar.get(11);
            boolean z = "24".equals(bottomCardDescriptor.clockHourFormat) || ("system".equals(bottomCardDescriptor.clockHourFormat) && DateFormat.is24HourFormat(context));
            if (z) {
                i = i3;
            } else if (i3 % 12 != 0) {
                i = i3 % 12;
            }
            String str = String.format(Locale.US, "%02d", Integer.valueOf(i));
            String str2 = String.format(Locale.US, "%02d", Integer.valueOf(i2));
            return new ClockSnapshot(str + ":" + str2, str, str2, z ? "" : new SimpleDateFormat("a", locale).format(calendar.getTime()), new SimpleDateFormat("EEE", locale).format(calendar.getTime()), new SimpleDateFormat("dd MMM", locale).format(calendar.getTime()), String.format(Locale.US, "%02d", Integer.valueOf(calendar.get(5))), "numeric".equals(bottomCardDescriptor.dateSpineFormat) ? new SimpleDateFormat("MM", locale).format(calendar.getTime()) : new SimpleDateFormat("MMM", locale).format(calendar.getTime()).toUpperCase(locale), i2 * 6.0f, ((i3 % 12) * 30.0f) + (i2 * 0.5f));
        }
    }

    public class MediaBridge {
        public MediaBridge() {
        }

        @JavascriptInterface
        public void dismissSlot() {
            MainActivity.this.mainHandler.post(() -> {
                try {
                    if (MainActivity.this.activeMediaPackage == null || MainActivity.this.activeMediaPackage.isEmpty()) {
                        return;
                    }
                    MainActivity.this.dismissMediaPopup(MainActivity.this.activeMediaPackage);
                } catch (Throwable th) {
                    Log.w("H6Viewer", "dismissSlot failed", th);
                }
            });
        }

        @JavascriptInterface
        public void next() {
            Log.i("H6Viewer", "MediaBridge.next");
            Handler handler = MainActivity.this.mainHandler;
            MediaNowPlaying mediaNowPlaying = MainActivity.this.mediaNowPlaying;
            Objects.requireNonNull(mediaNowPlaying);
            handler.post(() -> mediaNowPlaying.next());
        }

        @JavascriptInterface
        public void onViewerReady() {
            MainActivity.this.mainHandler.post(() -> {
                try {
                    MainActivity.this.mediaNowPlaying.start();
                    MainActivity.this.mediaNowPlaying.pushNow();
                } catch (Throwable th) {
                    Log.w("H6Viewer", "mediaNowPlaying.start failed", th);
                }
                MainActivity.this.ensurePlaceLocationPermission();
            });
        }

        @JavascriptInterface
        public void playPause() {
            Log.i("H6Viewer", "MediaBridge.playPause");
            Handler handler = MainActivity.this.mainHandler;
            MediaNowPlaying mediaNowPlaying = MainActivity.this.mediaNowPlaying;
            Objects.requireNonNull(mediaNowPlaying);
            handler.post(() -> mediaNowPlaying.playPause());
        }

        @JavascriptInterface
        public void prev() {
            Log.i("H6Viewer", "MediaBridge.prev");
            Handler handler = MainActivity.this.mainHandler;
            MediaNowPlaying mediaNowPlaying = MainActivity.this.mediaNowPlaying;
            Objects.requireNonNull(mediaNowPlaying);
            handler.post(() -> mediaNowPlaying.prev());
        }
    }

    private static class MotionTrailLayout extends LinearLayout {
        private static final int SAMPLES = 6;
        private float trailPx;

        MotionTrailLayout(Context context) {
            super(context);
            setClipChildren(false);
            setClipToPadding(false);
        }

        @Override // android.view.ViewGroup, android.view.View
        protected void dispatchDraw(Canvas canvas) {
            Canvas canvas2;
            if (this.trailPx <= 0.5f) {
                super.dispatchDraw(canvas);
                return;
            }
            int i = 6;
            while (i >= 1) {
                float f = i / 6.0f;
                int i2 = (int) ((1.0f - f) * 150.0f);
                if (i2 < 3) {
                    canvas2 = canvas;
                } else {
                    canvas2 = canvas;
                    int iSaveLayerAlpha = canvas2.saveLayerAlpha(-this.trailPx, 0.0f, getWidth(), getHeight(), i2);
                    canvas2.translate((-this.trailPx) * f, 0.0f);
                    super.dispatchDraw(canvas2);
                    canvas2.restoreToCount(iSaveLayerAlpha);
                }
                i--;
                canvas = canvas2;
            }
            super.dispatchDraw(canvas);
        }

        void setTrailPx(float trailPx) {
            if (trailPx == this.trailPx) {
                return;
            }
            this.trailPx = trailPx;
            invalidate();
        }
    }

    private final class QuickCardGraphicView extends View {
        private BottomCardDescriptor descriptor;
        private final RectF oval;
        private final Paint paint;
        private final Map<String, Bitmap> powerGhostBitmaps;
        private final Rect powerVisibleRect;
        private float statusLift;
        private long statusLiftLastMs;
        private final Map<String, Bitmap> statusVehicleBitmaps;
        private Bitmap tiresTopViewBitmap;
        private boolean tiresTopViewDecodeAttempted;

        QuickCardGraphicView(Context context, BottomCardDescriptor bottomCardDescriptor) {
            super(context);
            this.paint = new Paint(7);
            this.oval = new RectF();
            this.powerGhostBitmaps = new HashMap();
            this.statusVehicleBitmaps = new HashMap();
            this.powerVisibleRect = new Rect();
            this.descriptor = bottomCardDescriptor;
            setWillNotDraw(false);
            setImportantForAccessibility(2);
        }

        private void drawClimate(Canvas canvas, float f, float f2, int i, int i2) {
            float f3 = f * 0.48f;
            float f4 = f2 * 0.52f;
            stroke(i2, Math.max(2.0f, f * 0.035f));
            canvas.drawCircle(f3, f4, 0.12f * f, this.paint);
            for (int i3 = 0; i3 < 8; i3++) {
                float radians = (float) Math.toRadians(i3 * 45.0f);
                float f5 = f * 0.21f;
                float f6 = f * 0.31f;
                canvas.drawLine((((float) Math.cos(radians)) * f5) + f3, (((float) Math.sin(radians)) * f5) + f4, (((float) Math.cos(radians)) * f6) + f3, (((float) Math.sin(radians)) * f6) + f4, this.paint);
            }
            fill(MainActivity.withAlpha(i, 68));
            canvas.drawCircle(f3, f4, 0.2f * f, this.paint);
            fill(i);
            canvas.drawCircle(f3, f4, 0.09f * f, this.paint);
            for (int i4 = 0; i4 < 3; i4++) {
                float radians2 = (float) Math.toRadians((i4 * 120.0f) - 90.0f);
                this.oval.set((((((float) Math.cos(radians2)) * f) * 0.1f) + f3) - (f * 0.07f), (((((float) Math.sin(radians2)) * f) * 0.1f) + f4) - (f2 * 0.035f), (((float) Math.cos(radians2)) * f * 0.1f) + f3 + (0.07f * f), (((float) Math.sin(radians2)) * f * 0.1f) + f4 + (f2 * 0.035f));
                canvas.drawOval(this.oval, this.paint);
            }
        }

        private void drawClock(Canvas canvas, float f, float f2, int i, int i2, int i3) {
            int i4;
            String str;
            int i5;
            String str2;
            Calendar calendar = Calendar.getInstance();
            int i6 = calendar.get(12);
            int i7 = calendar.get(11);
            boolean z = "12h".equals(this.descriptor.clockFormat) || ("system".equals(this.descriptor.clockFormat) && !DateFormat.is24HourFormat(MainActivity.this));
            if (z) {
                i4 = i7 % 12 == 0 ? 12 : i7 % 12;
            } else {
                i4 = i7;
            }
            String str3 = String.format(Locale.US, "%02d", Integer.valueOf(i4));
            String str4 = String.format(Locale.US, "%02d", Integer.valueOf(i6));
            String str5 = String.format(Locale.US, "%02d", Integer.valueOf(calendar.get(5)));
            String upperCase = new DateFormatSymbols().getShortMonths()[calendar.get(2)].toUpperCase(Locale.US);
            String str6 = str5 + " " + upperCase;
            String str7 = this.descriptor.clockFace == null ? "panorama" : this.descriptor.clockFace;
            String str8 = str5;
            this.paint.setTypeface(Typeface.create("sans-serif-medium", 0));
            if ("meridian".equals(str7)) {
                float f3 = 0.33f * f;
                float f4 = f2 * 0.5f;
                float fMin = Math.min(f, f2) * 0.38f;
                String str9 = upperCase;
                String str10 = str6;
                stroke(i2, Math.max(2.0f, f * 0.018f));
                canvas.drawCircle(f3, f4, fMin, this.paint);
                int i8 = 0;
                while (i8 < 12) {
                    float f5 = f3;
                    double radians = Math.toRadians((i8 * 30) - 90);
                    canvas.drawLine(f5 + (((float) Math.cos(radians)) * fMin * 0.84f), (((float) Math.sin(radians)) * fMin * 0.84f) + f4, f5 + (((float) Math.cos(radians)) * fMin * 0.96f), (((float) Math.sin(radians)) * fMin * 0.96f) + f4, this.paint);
                    i8++;
                    f3 = f5;
                }
                float f6 = f3;
                float f7 = f4;
                float f8 = fMin;
                float radians2 = (float) Math.toRadians((i6 * 6) - 90);
                float radians3 = (float) Math.toRadians((((i7 % 12) + (i6 / 60.0f)) * 30.0f) - 90.0f);
                stroke(i, Math.max(2.0f, 0.028f * f));
                canvas.drawLine(f6, f7, f6 + (((float) Math.cos(radians2)) * f8 * 0.78f), f7 + (((float) Math.sin(radians2)) * f8 * 0.78f), this.paint);
                stroke(i3, Math.max(3.0f, 0.038f * f));
                canvas.drawLine(f6, f7, (((float) Math.cos(radians3)) * f8 * 0.55f) + f6, f7 + (((float) Math.sin(radians3)) * f8 * 0.55f), this.paint);
                fill(i);
                canvas.drawCircle(f6, f7, Math.max(3.0f, 0.035f * f), this.paint);
                this.paint.setTextAlign(Paint.Align.LEFT);
                this.paint.setTextSize(Math.max(18.0f, f2 * 0.22f));
                fill(i3);
                canvas.drawText(str3, f * 0.57f, f2 * 0.43f, this.paint);
                this.paint.setTextSize(Math.max(18.0f, f2 * 0.22f));
                fill(i);
                canvas.drawText(str4, f * 0.57f, 0.68f * f2, this.paint);
                this.paint.setTextSize(Math.max(7.0f, f2 * 0.08f));
                fill(i2);
                canvas.drawText(str10, f * 0.57f, 0.9f * f2, this.paint);
            } else {
                String str11 = str7;
                if ("split".equals(str11)) {
                    float f9 = f * 0.045f;
                    float f10 = (f - f9) * 0.5f;
                    fill(MainActivity.withAlpha(i2, 34));
                    canvas.drawRoundRect(0.0f, f2 * 0.12f, f10, f2 * 0.84f, 10.0f, 10.0f, this.paint);
                    canvas.drawRoundRect(f10 + f9, f2 * 0.12f, f, f2 * 0.84f, 10.0f, 10.0f, this.paint);
                    this.paint.setTextAlign(Paint.Align.CENTER);
                    this.paint.setTypeface(Typeface.create("sans-serif", 0));
                    this.paint.setTextSize(Math.max(28.0f, 0.48f * f2));
                    fill(i3);
                    canvas.drawText(str3, f10 * 0.5f, f2 * 0.62f, this.paint);
                    canvas.drawText(str4, f10 + f9 + (f10 * 0.5f), f2 * 0.62f, this.paint);
                    this.paint.setTextSize(Math.max(7.0f, f2 * 0.08f));
                    fill(i2);
                    canvas.drawText(str6, f * 0.5f, 0.94f * f2, this.paint);
                } else if ("date-spine".equals(str11)) {
                    float f11 = f * 0.24f;
                    fill(MainActivity.withAlpha(i, 40));
                    canvas.drawRoundRect(0.0f, 0.0f, f11, f2, 9.0f, 9.0f, this.paint);
                    this.paint.setTextAlign(Paint.Align.CENTER);
                    this.paint.setTextSize(Math.max(10.0f, 0.13f * f2));
                    fill(i);
                    canvas.drawText(str8, f11 * 0.5f, 0.45f * f2, this.paint);
                    this.paint.setTextSize(Math.max(7.0f, f2 * 0.08f));
                    fill(i2);
                    canvas.drawText(upperCase, f11 * 0.5f, 0.64f * f2, this.paint);
                    this.paint.setTextAlign(Paint.Align.LEFT);
                    this.paint.setTextSize(Math.max(26.0f, f2 * 0.43f));
                    fill(i3);
                    canvas.drawText(str3 + ":" + str4, (f * 0.07f) + f11, 0.6f * f2, this.paint);
                    this.paint.setTextSize(Math.max(7.0f, f2 * 0.08f));
                    fill(i2);
                    if (z) {
                        i5 = i7;
                        str2 = i5 >= 12 ? "PM" : "AM";
                    } else {
                        i5 = i7;
                        str2 = "LOCAL TIME";
                    }
                    canvas.drawText(str2, (0.07f * f) + f11, 0.83f * f2, this.paint);
                } else {
                    this.paint.setTextAlign(Paint.Align.LEFT);
                    this.paint.setTextSize(Math.max(34.0f, 0.58f * f2));
                    fill(i3);
                    canvas.drawText(str3 + ":" + str4, f * 0.02f, f2 * 0.62f, this.paint);
                    this.paint.setTextSize(Math.max(8.0f, 0.1f * f2));
                    fill(i);
                    StringBuilder sbAppend = new StringBuilder().append(str6);
                    if (z) {
                        str = i7 >= 12 ? "  PM" : "  AM";
                    } else {
                        str = "  LOCAL";
                    }
                    canvas.drawText(sbAppend.append(str).toString(), 0.03f * f, 0.9f * f2, this.paint);
                    stroke(i, Math.max(2.0f, 0.012f * f));
                    canvas.drawLine(f * 0.02f, 0.72f * f2, 0.98f * f, 0.72f * f2, this.paint);
                }
            }
            this.paint.setTextAlign(Paint.Align.LEFT);
            this.paint.setTypeface(Typeface.DEFAULT);
        }

        private void drawCodeBadge(Canvas canvas, String str, float f, float f2, float f3, float f4, int i, boolean z) {
            this.oval.set(f - (f3 * 0.5f), f2 - (f4 * 0.5f), (f3 * 0.5f) + f, (f4 * 0.5f) + f2);
            fill(MainActivity.withAlpha(i, z ? 34 : 20));
            canvas.drawRoundRect(this.oval, f4 * 0.3f, f4 * 0.3f, this.paint);
            stroke(i, Math.max(1.5f, 0.042f * f4));
            canvas.drawRoundRect(this.oval, f4 * 0.3f, 0.3f * f4, this.paint);
            this.paint.setStyle(Paint.Style.FILL);
            this.paint.setColor(i);
            this.paint.setTypeface(Typeface.create("sans-serif-medium", 0));
            this.paint.setTextAlign(Paint.Align.CENTER);
            this.paint.setLetterSpacing(0.06f);
            float f5 = 0.62f * f4;
            this.paint.setTextSize(f5);
            float f6 = 0.78f * f3;
            float fMeasureText = this.paint.measureText(str);
            if (fMeasureText > f6) {
                this.paint.setTextSize((f5 * f6) / fMeasureText);
            }
            Paint.FontMetrics fontMetrics = this.paint.getFontMetrics();
            canvas.drawText(str, f, f2 - ((fontMetrics.ascent + fontMetrics.descent) * 0.5f), this.paint);
            this.paint.setLetterSpacing(0.0f);
            this.paint.setTextAlign(Paint.Align.LEFT);
        }

        private void drawDesktops(Canvas canvas, float f, float f2, int i, int i2) {
            float f3 = 0.07f * f;
            float f4 = 0.3f * f;
            float f5 = 0.27f * f2;
            float f6 = ((f - (f4 * 2.0f)) - f3) * 0.5f;
            float f7 = ((f2 - (2.0f * f5)) - f3) * 0.5f;
            int i3 = 0;
            while (i3 < 2) {
                int i4 = 0;
                while (i4 < 2) {
                    fill((i3 == 0 && i4 == 0) ? MainActivity.withAlpha(i, 204) : i2);
                    float f8 = f6 + (i4 * (f4 + f3));
                    float f9 = f7 + (i3 * (f5 + f3));
                    canvas.drawRoundRect(f8, f9, f8 + f4, f9 + f5, f * 0.04f, f * 0.04f, this.paint);
                    i4++;
                }
                i3++;
            }
        }

        private void drawDriveMode(Canvas canvas, float f, float f2, int i, int i2, int i3) {
            float fMin = Math.min(f, f2) * 0.68f;
            String str = this.descriptor.state == null ? "" : this.descriptor.state;
            boolean z = ("unknown".equals(str) || str.isEmpty()) ? false : true;
            boolean zIsEmpty = this.descriptor.glyphText.isEmpty();
            BottomCardDescriptor bottomCardDescriptor = this.descriptor;
            if (zIsEmpty) {
                drawGlyphPath(canvas, bottomCardDescriptor.glyph, 0.5f * f, 0.44f * f2, fMin, z ? i : i2, Math.max(2.0f, 0.085f * fMin));
            } else {
                drawCodeBadge(canvas, bottomCardDescriptor.glyphText, 0.5f * f, 0.44f * f2, f * 0.6f, f2 * 0.4f, z ? i : i2, z);
            }
            drawStepDots(canvas, f, f2, 3, this.descriptor.progress, i, i2);
        }

        private void drawEnergy(Canvas canvas, float f, float f2, int i, int i2, int i3) {
            int iWithAlpha;
            int[] iArr = this.descriptor.energyBars;
            int length = iArr == null ? 0 : iArr.length;
            float f3 = f * 0.1f;
            float f4 = f * 0.9f;
            float f5 = 0.14f * f2;
            float f6 = f2 * 0.74f;
            float f7 = 1.0f;
            stroke(i2, Math.max(1.0f, 0.012f * f));
            canvas.drawLine(f3, f6, f4, f6, this.paint);
            if (length > 0) {
                float fMax = Math.max(1.5f, 0.03f * f);
                float fMax2 = Math.max(1.0f, ((f4 - f3) - ((length - 1) * fMax)) / length);
                int i4 = 0;
                while (i4 < length) {
                    float fMax3 = Math.max(0.0f, Math.min(f7, iArr[i4] / 100.0f));
                    float f8 = (i4 * (fMax2 + fMax)) + f3;
                    float fMax4 = f6 - Math.max(0.025f * f2, (f6 - f5) * fMax3);
                    if (i4 == length - 1) {
                        iWithAlpha = i;
                    } else {
                        iWithAlpha = MainActivity.withAlpha(i3, fMax3 > 0.0f ? 102 : 34);
                    }
                    fill(iWithAlpha);
                    canvas.drawRoundRect(f8, fMax4, f8 + fMax2, f6, fMax2 * 0.3f, 0.3f * fMax2, this.paint);
                    i4++;
                    f7 = 1.0f;
                }
            }
            float f9 = f2 * 0.88f;
            float fMax5 = Math.max(2.0f, 0.035f * f2);
            stroke(MainActivity.withAlpha(i3, 42), fMax5);
            canvas.drawLine(f3, f9, f4, f9, this.paint);
            if (this.descriptor.progress > 0) {
                stroke(i, fMax5);
                canvas.drawLine(f3, f9, (((f4 - f3) * this.descriptor.progress) / 100.0f) + f3, f9, this.paint);
            }
        }

        private void drawGauge(Canvas canvas, float f, float f2, int i, int i2, int i3) {
            float f3 = 0.13f * f;
            this.oval.set(f3, 0.25f * f2, f - f3, 0.93f * f2);
            stroke(i2, Math.max(3.0f, f * 0.065f));
            canvas.drawArc(this.oval, 190.0f, 160.0f, false, this.paint);
            stroke(i, Math.max(3.0f, 0.065f * f));
            canvas.drawArc(this.oval, 190.0f, (this.descriptor.progress * 160.0f) / 100.0f, false, this.paint);
            float radians = (float) Math.toRadians(((this.descriptor.progress * 160.0f) / 100.0f) + 190.0f);
            float f4 = f * 0.5f;
            float f5 = f2 * 0.6f;
            float f6 = 0.24f * f;
            stroke(i3, Math.max(2.0f, 0.035f * f));
            canvas.drawLine(f4, f5, f4 + (((float) Math.cos(radians)) * f6), f5 + (((float) Math.sin(radians)) * f6), this.paint);
            fill(i);
            canvas.drawCircle(f4, f5, Math.max(3.0f, 0.055f * f), this.paint);
        }

        private void drawGlyphPath(Canvas canvas, String str, float f, float f2, float f3, int i, float f4) {
            if (str != null && !str.isEmpty()) {
                Path path = new Path();
                String[] strArrSplit = str.split(" ");
                float[] fArr = new float[6];
                int i2 = 0;
                while (i2 < strArrSplit.length) {
                    try {
                        int i3 = i2 + 1;
                        try {
                            String str2 = strArrSplit[i2];
                            if (!str2.isEmpty()) {
                                char cCharAt = str2.charAt(0);
                                if (cCharAt == 'Z') {
                                    path.close();
                                } else {
                                    int i4 = cCharAt == 'C' ? 6 : 2;
                                    if (i3 + i4 > strArrSplit.length) {
                                        return;
                                    }
                                    int i5 = 0;
                                    int i6 = i3;
                                    while (i5 < i4) {
                                        int i7 = i6 + 1;
                                        fArr[i5] = Float.parseFloat(strArrSplit[i6]);
                                        i5++;
                                        i6 = i7;
                                    }
                                    if (cCharAt == 'M') {
                                        try {
                                            path.moveTo(fArr[0], fArr[1]);
                                        } catch (NumberFormatException e) {
                                            return;
                                        }
                                    } else if (cCharAt == 'L') {
                                        path.lineTo(fArr[0], fArr[1]);
                                    } else if (cCharAt != 'C') {
                                        return;
                                    } else {
                                        path.cubicTo(fArr[0], fArr[1], fArr[2], fArr[3], fArr[4], fArr[5]);
                                    }
                                    i2 = i6;
                                }
                            }
                            i2 = i3;
                        } catch (NumberFormatException e2) {
                            return;
                        }
                    } catch (NumberFormatException e3) {
                        return;
                    }
                }
                Matrix matrix = new Matrix();
                float f5 = f3 / 24.0f;
                matrix.setScale(f5, f5);
                matrix.postTranslate(f - (f3 * 0.5f), f2 - (0.5f * f3));
                path.transform(matrix);
                stroke(i, f4);
                canvas.drawPath(path, this.paint);
            }
        }

        private void drawNavMetricRow(Canvas canvas, float f, float f2, float f3, float f4, String str, String str2, String str3, int i, int i2, int i3) {
            ArrayList arrayList = new ArrayList();
            if (str != null && !str.isEmpty()) {
                arrayList.add(new String[]{str, "REMAINING"});
            }
            if (str2 != null && !str2.isEmpty()) {
                arrayList.add(new String[]{str2, "TIME"});
            }
            if (str3 != null && !str3.isEmpty()) {
                arrayList.add(new String[]{str3, "ETA"});
            }
            if (!arrayList.isEmpty() && f3 > 0.0f) {
                Typeface typefaceCreate = Typeface.create("sans-serif-medium", 0);
                float fMax = Math.max(15.0f, 0.28f * f4);
                float fMax2 = Math.max(9.0f, 0.13f * f4);
                float fMax3 = Math.max(10.0f, f4 * 0.08f);
                this.paint.setTextAlign(Paint.Align.LEFT);
                this.paint.setTypeface(typefaceCreate);
                float[] fArrMeasureNavMetricWidths = measureNavMetricWidths(arrayList, fMax, fMax2);
                float fSumNavMetricWidths = sumNavMetricWidths(fArrMeasureNavMetricWidths, fMax3);
                if (fSumNavMetricWidths > f3) {
                    for (int size = arrayList.size() - 1; size >= 0; size--) {
                        if ("TIME".equals(((String[]) arrayList.get(size))[1])) {
                            arrayList.remove(size);
                            break;
                        }
                    }
                    fArrMeasureNavMetricWidths = measureNavMetricWidths(arrayList, fMax, fMax2);
                    fSumNavMetricWidths = sumNavMetricWidths(fArrMeasureNavMetricWidths, fMax3);
                }
                while (fSumNavMetricWidths > f3 && fMax > 11.0f) {
                    fMax -= 1.0f;
                    fArrMeasureNavMetricWidths = measureNavMetricWidths(arrayList, fMax, fMax2);
                    fSumNavMetricWidths = sumNavMetricWidths(fArrMeasureNavMetricWidths, fMax3);
                }
                float f5 = f2 + (0.22f * f4);
                float f6 = f2 + (0.4f * f4);
                float fMax4 = Math.max(8.0f, f4 * 0.08f);
                if (fSumNavMetricWidths + fMax4 > f3) {
                    fMax4 = Math.max(0.0f, f3 - fSumNavMetricWidths);
                }
                float f7 = f;
                float f8 = fMax4;
                for (int i4 = 0; i4 < arrayList.size(); i4++) {
                    float f9 = fArrMeasureNavMetricWidths[i4];
                    boolean zEquals = "ETA".equals(((String[]) arrayList.get(i4))[1]);
                    if (zEquals && i4 > 0) {
                        f7 += f8;
                    }
                    this.paint.setTypeface(typefaceCreate);
                    this.paint.setTextSize(fMax);
                    fill(zEquals ? i : i3);
                    canvas.drawText(ellipsizeNav(((String[]) arrayList.get(i4))[0], f9), f7, f5, this.paint);
                    this.paint.setTextSize(fMax2);
                    fill(i2);
                    canvas.drawText(((String[]) arrayList.get(i4))[1], f7, f6, this.paint);
                    f7 += f9 + fMax3;
                }
            }
        }

        private void drawNavigation(Canvas canvas, float f, float f2, int i, int i2, int i3) {
            Canvas canvas2;
            String str = "";
            String str2 = this.descriptor.state == null ? "" : this.descriptor.state;
            boolean z = true;
            if (("idle".equals(str2) || "unavailable".equals(str2) || str2.isEmpty()) || (!"destination".equals(str2) && !"exit".equals(str2) && tbtIconRes(str2) == 0 && (this.descriptor.glyph == null || this.descriptor.glyph.isEmpty()))) {
                z = false;
            }
            boolean z2 = z;
            String str3 = this.descriptor.primary == null ? "" : this.descriptor.primary;
            String str4 = this.descriptor.metricA == null ? "" : this.descriptor.metricA;
            String str5 = this.descriptor.navRemaining == null ? "" : this.descriptor.navRemaining;
            String str6 = this.descriptor.navDuration == null ? "" : this.descriptor.navDuration;
            String str7 = this.descriptor.navEta == null ? "" : this.descriptor.navEta;
            float fMin = Math.min(0.3f * f, 0.95f * f2);
            String str8 = "sans-serif-medium";
            if (z2) {
                float fMin2 = Math.min(0.78f * fMin, 0.58f * f2);
                if (drawTbtIcon(canvas, str2, fMin * 0.5f, f2 * 0.36f, fMin2, i) || this.descriptor.glyph == null || this.descriptor.glyph.isEmpty()) {
                    canvas2 = canvas;
                } else {
                    drawGlyphPath(canvas, this.descriptor.glyph, fMin * 0.5f, f2 * 0.36f, fMin2, i, Math.max(2.4f, 0.08f * fMin2));
                    canvas2 = canvas;
                }
                if (!str4.isEmpty()) {
                    this.paint.setTextAlign(Paint.Align.CENTER);
                    this.paint.setTypeface(Typeface.create(str8, 0));
                    this.paint.setTextSize(Math.max(13.0f, 0.2f * f2));
                    fill(i3);
                    canvas2.drawText(str4, fMin * 0.5f, f2 * 0.88f, this.paint);
                }
            } else {
                str = "";
                canvas2 = canvas;
                str8 = "sans-serif-medium";
                int i4 = "unavailable".equals(str2) ? i2 : i;
                float f3 = fMin * 0.92f;
                float f4 = f2 * 0.78f;
                float f5 = 0.04f * fMin;
                float f6 = f2 * 0.12f;
                Path path = new Path();
                path.moveTo(f5 + (f3 * 0.12f), f6 + (f4 * 0.88f));
                path.cubicTo(f5 + (0.18f * f3), f6 + (f4 * 0.48f), f5 + (0.48f * f3), f6 + (0.66f * f4), f5 + (0.52f * f3), f6 + (f4 * 0.32f));
                path.cubicTo(f5 + (f3 * 0.55f), f6 + (f4 * 0.12f), f5 + (f3 * 0.78f), f6 + (f4 * 0.14f), f5 + (f3 * 0.92f), f6 + (0.14f * f4));
                stroke(i2, Math.max(3.5f, 0.018f * f));
                canvas2.drawPath(path, this.paint);
                stroke(i4, Math.max(1.8f, 0.01f * f));
                canvas2.drawPath(path, this.paint);
                fill(i4);
                canvas2.drawCircle((f3 * 0.12f) + f5, f6 + (0.88f * f4), Math.max(3.0f, f * 0.012f), this.paint);
            }
            float f7 = fMin + (0.035f * f);
            float f8 = (f - f7) - (0.03f * f);
            if (f8 > 8.0f) {
                this.paint.setTextAlign(Paint.Align.LEFT);
                this.paint.setTypeface(Typeface.create(str8, 0));
                if (!str3.isEmpty()) {
                    this.paint.setTextSize(Math.max(16.0f, 0.26f * f2));
                    fill(i3);
                    canvas2.drawText(ellipsizeNav(str3, f8), f7, f2 * 0.32f, this.paint);
                }
                if (z2) {
                    drawNavMetricRow(canvas2, f7, 0.46f * f2, f8, f2, str5, str6, str7, i, i2, i3);
                } else {
                    Canvas canvas3 = canvas2;
                    String str9 = this.descriptor.navIdleCity == null ? str : this.descriptor.navIdleCity;
                    String str10 = this.descriptor.navIdleAction == null ? str : this.descriptor.navIdleAction;
                    if (!str9.isEmpty()) {
                        this.paint.setTextSize(Math.max(12.0f, 0.17f * f2));
                        fill(i2);
                        canvas3.drawText(ellipsizeNav(str9, f8), f7, 0.54f * f2, this.paint);
                    }
                    if (!str10.isEmpty()) {
                        this.paint.setTextSize(Math.max(10.0f, 0.13f * f2));
                        fill(i2);
                        canvas3.drawText(ellipsizeNav(str10, f8), f7, 0.76f * f2, this.paint);
                    }
                }
            }
            this.paint.setTextAlign(Paint.Align.LEFT);
            this.paint.setTypeface(Typeface.DEFAULT);
        }

        private void drawPower(Canvas canvas, float f, float f2, int i, int i2, int i3) {
            int i4;
            float f3;
            float f4;
            float f5;
            float f6;
            float f7;
            int i5;
            float[][] fArr;
            String strSanitizePowerVariant = MainActivity.this.sanitizePowerVariant(this.descriptor.powerVariant);
            Bitmap powerChassisBitmap = getPowerChassisBitmap(strSanitizePowerVariant);
            String strSanitizePowerState = MainActivity.this.sanitizePowerState(this.descriptor.state);
            String strSanitizePowerDirections = MainActivity.this.sanitizePowerDirections(this.descriptor.glyphText);
            boolean hasDirections = !("stale".equals(strSanitizePowerState) || "unavailable".equals(strSanitizePowerState) || strSanitizePowerDirections.isEmpty());
            int i6 = 0;
            int i7 = 0;
            int i8 = 0;
            if (hasDirections) {
                String[] strArrSplit = strSanitizePowerDirections.split(",");
                i6 = Integer.parseInt(strArrSplit[0]);
                i7 = Integer.parseInt(strArrSplit[1]);
                i8 = Integer.parseInt(strArrSplit[2]);
            }
            boolean zEquals = "phev34".equals(strSanitizePowerVariant);
            boolean zEquals2 = "hev2".equals(strSanitizePowerVariant);
            if (!zEquals) {
                i7 = 0;
            }
            if (zEquals) {
                i4 = 398;
            } else {
                i4 = zEquals2 ? 774 : 20;
            }
            float fMin = 0.96f * Math.min(f / 770.0f, f2 / 350.0f);
            int iSave = canvas.save();
            canvas.translate(f * 0.5f, f2 * 0.5f);
            canvas.scale(fMin, fMin);
            canvas.rotate(90.0f);
            canvas.translate(-175.0f, -385.0f);
            if (powerChassisBitmap != null) {
                this.oval.set(0.0f, 0.0f, 350.0f, 770.0f);
                fill(-1);
                this.paint.setFilterBitmap(true);
                canvas.drawBitmap(powerChassisBitmap, new Rect(i4, 158, i4 + 350, 928), this.oval, this.paint);
                this.paint.setFilterBitmap(false);
            }
            boolean z2 = i6 < 0 || i7 < 0;
            boolean z3 = i6 > 0 || i7 > 0;
            boolean z4 = hasDirections && !((!"charge".equals(strSanitizePowerState) || zEquals2) && (!z2 || z3));
            boolean z5 = hasDirections && z3 && !z2 && ("ev".equals(strSanitizePowerState) || "hybrid".equals(strSanitizePowerState));
            if (zEquals) {
                f3 = 118.0f;
            } else {
                f3 = zEquals2 ? 127.0f : 121.0f;
            }
            if (zEquals) {
                f4 = 371.0f;
            } else {
                f4 = zEquals2 ? 538.0f : 375.0f;
            }
            float f8 = zEquals2 ? 91.0f : 115.0f;
            if (zEquals) {
                f5 = 134.0f;
            } else {
                f5 = zEquals2 ? 90.0f : 130.0f;
            }
            float f9 = f5;
            if (zEquals) {
                f6 = 123.0f;
            } else {
                f6 = zEquals2 ? 132.0f : 126.0f;
            }
            float f10 = f6;
            float f11 = zEquals2 ? 567.0f : 409.0f;
            float f12 = zEquals2 ? 81.0f : 105.0f;
            if (zEquals) {
                f7 = 49.0f;
            } else {
                f7 = zEquals2 ? 22.0f : 45.0f;
            }
            float f13 = f7;
            fill(-10327183);
            int i9 = i8;
            int i10 = i7;
            float f14 = f4;
            int i11 = i6;
            canvas.drawRoundRect(f3, f14, f3 + f8, f14 + f9, 4.0f, 4.0f, this.paint);
            stroke(-6643291, 1.0f);
            canvas.drawRoundRect(f3, f14, f3 + f8, f14 + f9, 4.0f, 4.0f, this.paint);
            Canvas canvas2 = canvas;
            boolean zDrawPowerBatteryCells = drawPowerBatteryCells(canvas2, f10, f11, f12, f13, this.descriptor.socKnown, this.descriptor.powerSoc, z4, z5);
            if (zEquals) {
                fArr = new float[3][];
                fArr[0] = new float[]{177.0f, 248.0f};
                i5 = 1;
                fArr[1] = new float[]{177.0f, 221.0f};
                fArr[2] = new float[]{177.0f, 173.0f};
            } else {
                i5 = 1;
                if (zEquals2) {
                    fArr = new float[5][];
                    fArr[0] = new float[]{172.0f, 534.0f};
                    fArr[1] = new float[]{111.0f, 514.0f};
                    fArr[2] = new float[]{111.0f, 246.0f};
                    fArr[3] = new float[]{177.0f, 216.0f};
                    fArr[4] = new float[]{177.0f, 173.0f};
                } else {
                    fArr = new float[3][];
                    fArr[0] = new float[]{178.0f, 340.0f};
                    fArr[1] = new float[]{178.0f, 252.0f};
                    fArr[2] = new float[]{178.0f, 173.0f};
                }
            }
            drawPowerTopRoute(canvas2, fArr, i11, i);
            float[][] fArr2 = new float[2][];
            fArr2[0] = new float[]{177.0f, 173.0f};
            fArr2[i5] = new float[]{57.0f, 144.0f};
            drawPowerTopRoute(canvas2, fArr2, i11, i);
            float[][] fArr3 = new float[2][];
            fArr3[0] = new float[]{177.0f, 173.0f};
            fArr3[i5] = new float[]{289.0f, 144.0f};
            drawPowerTopRoute(canvas2, fArr3, i11, i);
            if (zEquals) {
                float[][] fArr4 = new float[3][];
                fArr4[0] = new float[]{177.0f, 567.0f};
                fArr4[i5] = new float[]{177.0f, 594.0f};
                fArr4[2] = new float[]{177.0f, 640.0f};
                drawPowerTopRoute(canvas2, fArr4, i10, i);
                float[][] fArr5 = new float[2][];
                fArr5[0] = new float[]{177.0f, 640.0f};
                fArr5[i5] = new float[]{57.0f, 654.0f};
                drawPowerTopRoute(canvas2, fArr5, i10, i);
                float[][] fArr6 = new float[2][];
                fArr6[0] = new float[]{177.0f, 640.0f};
                fArr6[i5] = new float[]{289.0f, 654.0f};
                drawPowerTopRoute(canvas2, fArr6, i10, i);
            }
            if (i9 == i5) {
                fill(687847004);
                canvas.drawRoundRect(137.0f, 61.0f, 217.0f, 131.0f, 4.0f, 4.0f, this.paint);
                canvas2 = canvas;
            }
            fill(-854537);
            this.paint.setTextAlign(Paint.Align.CENTER);
            this.paint.setTypeface(Typeface.DEFAULT_BOLD);
            this.paint.setTextSize(zEquals2 ? 19.0f : 23.0f);
            canvas2.drawText(this.descriptor.socKnown ? Math.round(this.descriptor.powerSoc) + "%" : "—", f10 + (f12 / 2.0f), f11 - 9.0f, this.paint);
            this.paint.setTypeface(Typeface.DEFAULT);
            this.paint.setTextSize(9.0f);
            canvas2.drawText("HIGH VOLTAGE", f10 + (f12 / 2.0f), f11 + f13 + 15.0f, this.paint);
            canvas2.drawText("BATTERY", f10 + (f12 / 2.0f), f11 + f13 + 27.0f, this.paint);
            this.paint.setTextAlign(Paint.Align.LEFT);
            canvas2.restoreToCount(iSave);
            if (zDrawPowerBatteryCells && powerMotionEnabled() && getGlobalVisibleRect(this.powerVisibleRect)) {
                postInvalidateDelayed(33L);
            }
        }

        private boolean drawPowerBatteryCells(Canvas canvas, float f, float f2, float f3, float f4, boolean z, double d, boolean z2, boolean z3) {
            double dMax = z ? Math.max(0.0d, Math.min(100.0d, d)) / 10.0d : 0.0d;
            int iCeil = ((int) Math.ceil(dMax)) - 1;
            boolean z4 = iCeil >= 0 && (z3 || (z2 && d < 100.0d));
            boolean z5 = z4 && powerMotionEnabled();
            double dUptimeMillis = SystemClock.uptimeMillis() / 1000.0d;
            float f5 = (f3 - (9.0f * 2.5f)) / 10.0f;
            for (int i = 0; i < 10; i++) {
                float f6 = f + (i * (f5 + 2.5f));
                float fMax = (float) Math.max(0.0d, Math.min(1.0d, dMax - ((double) i)));
                fill(-15459038);
                canvas.drawRoundRect(f6, f2, f6 + f5, f2 + f4, 4.0f, 4.0f, this.paint);
                stroke(-7169377, 1.3f);
                canvas.drawRoundRect(f6, f2, f6 + f5, f2 + f4, 4.0f, 4.0f, this.paint);
                if (z5 && fMax > 0.0f && (z3 || i == iCeil)) {
                    fMax *= (float) (1.0d - Math.abs((2.0d * (((((dUptimeMillis - (z3 ? ((double) (iCeil - i)) * 0.14d : 0.0d)) % 1.8d) + 1.8d) % 1.8d) / 1.8d)) - 1.0d));
                }
                fill(MainActivity.withAlpha(z3 ? -14497281 : -10097579, Math.round(255.0f * fMax)));
                canvas.drawRoundRect(f6 + 1.0f, f2 + 1.5f, (f6 + f5) - 1.0f, (f2 + f4) - 1.5f, 4.0f, 4.0f, this.paint);
            }
            return z4;
        }

        private void drawPowerChevron(Canvas canvas, float f, float f2, float f3, float f4, int i) {
            float fHypot = (float) Math.hypot(f3, f4);
            if (fHypot < 1.0f) {
                return;
            }
            float f5 = f3 / fHypot;
            float f6 = f4 / fHypot;
            float f7 = f - (f5 * 13.0f);
            float f8 = f2 - (13.0f * f6);
            stroke(i, 7.0f);
            canvas.drawLine(f7 - (f6 * 10.0f), f8 + (f5 * 10.0f), f, f2, this.paint);
            canvas.drawLine(f7 + (f6 * 10.0f), f8 - (10.0f * f5), f, f2, this.paint);
        }

        private void drawPowerMode(Canvas canvas, float f, float f2, int i, int i2, int i3) {
            String str = this.descriptor.state == null ? "" : this.descriptor.state;
            boolean z = "hev".equals(str) || "evp".equals(str) || "ev".equals(str);
            drawCodeBadge(canvas, z ? str.toUpperCase(Locale.US) : "--", f * 0.5f, f2 * 0.46f, 0.74f * f, f2 * 0.46f, z ? i : i2, z);
            drawStepDots(canvas, f, f2, 3, this.descriptor.progress, i, i2);
        }

        private void drawPowerTopRoute(Canvas canvas, float[][] fArr, int i, int i2) {
            QuickCardGraphicView quickCardGraphicView = this;
            Canvas canvas2 = canvas;
            if (i == 0 || fArr == null || fArr.length < 2) {
                return;
            }
            int i3 = i > 0 ? -14497281 : -11276911;
            Path path = new Path();
            int length = i > 0 ? 0 : fArr.length - 1;
            int i4 = i > 0 ? 1 : -1;
            path.moveTo(fArr[length][0], fArr[length][1]);
            for (int i5 = length + i4; i5 >= 0 && i5 < fArr.length; i5 += i4) {
                path.lineTo(fArr[i5][0], fArr[i5][1]);
            }
            quickCardGraphicView.stroke(MainActivity.withAlpha(i3, 40), 48.0f);
            canvas2.drawPath(path, quickCardGraphicView.paint);
            quickCardGraphicView.stroke(MainActivity.withAlpha(i3, 120), 34.0f);
            canvas2.drawPath(path, quickCardGraphicView.paint);
            quickCardGraphicView.stroke(i3, 7.0f);
            canvas2.drawPath(path, quickCardGraphicView.paint);
            int i6 = length;
            while (i6 + i4 >= 0 && i6 + i4 < fArr.length) {
                float[] fArr2 = fArr[i6];
                float[] fArr3 = fArr[i6 + i4];
                quickCardGraphicView.drawPowerChevron(canvas2, (fArr2[0] + fArr3[0]) / 2.0f, (fArr2[1] + fArr3[1]) / 2.0f, fArr3[0] - fArr2[0], fArr3[1] - fArr2[1], i3);
                i6 += i4;
                quickCardGraphicView = this;
                canvas2 = canvas;
            }
        }

        private void drawRegen(Canvas canvas, float f, float f2, int i, int i2, int i3) {
            int i4;
            int i5;
            String str = this.descriptor.state == null ? "" : this.descriptor.state;
            boolean zEquals = "onepedal".equals(str);
            if (zEquals) {
                i4 = 3;
            } else if ("level1".equals(str)) {
                i4 = 1;
            } else if ("level2".equals(str)) {
                i4 = 2;
            } else {
                i4 = "level3".equals(str) ? 3 : 0;
            }
            int i6 = i4;
            float f3 = f * 0.17f;
            float f4 = f * 0.1f;
            float f5 = f2 * 0.7f;
            float f6 = (f * 0.5f) - (((f3 * 3.0f) + (f4 * 2.0f)) * 0.5f);
            float f7 = f2 * 0.46f;
            int i7 = 0;
            while (i7 < 3) {
                float f8 = ((i7 * 0.3f) + 0.4f) * f7;
                float f9 = f6 + (i7 * (f3 + f4));
                fill(i7 < i6 ? i : i2);
                canvas.drawRoundRect(f9, f5 - f8, f9 + f3, f5, f3 * 0.32f, f3 * 0.32f, this.paint);
                i7++;
            }
            if (zEquals) {
                i5 = i;
                stroke(i5, Math.max(1.5f, 0.026f * f));
                canvas.drawLine(f6, f5 + (f2 * 0.1f), (3.0f * f3) + f6 + (2.0f * f4), f5 + (0.1f * f2), this.paint);
            } else {
                i5 = i;
            }
            drawStepDots(canvas, f, f2, 3, this.descriptor.progress, i5, i2);
        }

        private void drawRing(Canvas canvas, float f, float f2, int i, int i2, boolean z) {
            float fMin = Math.min(f, f2) * 0.72f;
            float f3 = (f - fMin) * 0.5f;
            float f4 = (f2 - fMin) * 0.5f;
            this.oval.set(f3, f4, f3 + fMin, f4 + fMin);
            stroke(i2, Math.max(3.0f, fMin * 0.105f));
            canvas.drawArc(this.oval, -90.0f, 360.0f, false, this.paint);
            stroke(i, Math.max(3.0f, 0.105f * fMin));
            canvas.drawArc(this.oval, -90.0f, (this.descriptor.progress * 360.0f) / 100.0f, false, this.paint);
            if (z) {
                Path path = new Path();
                path.moveTo(f * 0.53f, 0.25f * f2);
                path.lineTo(0.37f * f, f2 * 0.53f);
                path.lineTo(0.5f * f, 0.53f * f2);
                path.lineTo(f * 0.43f, 0.76f * f2);
                path.lineTo(0.65f * f, f2 * 0.43f);
                path.lineTo(0.51f * f, 0.43f * f2);
                path.close();
                fill(i);
                canvas.drawPath(path, this.paint);
            }
        }

        private void drawRoof(Canvas canvas, float f, float f2, int i, int i2, int i3) {
            Path path = new Path();
            path.moveTo(0.42f * f, f2 * 0.07f);
            path.quadTo(f * 0.5f, 0.02f * f2, 0.58f * f, f2 * 0.07f);
            path.lineTo(0.68f * f, f2 * 0.22f);
            path.lineTo(0.66f * f, f2 * 0.82f);
            path.quadTo(0.62f * f, f2 * 0.94f, 0.5f * f, 0.96f * f2);
            path.quadTo(0.38f * f, 0.94f * f2, 0.34f * f, 0.82f * f2);
            path.lineTo(0.32f * f, 0.22f * f2);
            path.close();
            fill(MainActivity.withAlpha(i2, 29));
            canvas.drawPath(path, this.paint);
            stroke(i3, Math.max(2.0f, f * 0.025f));
            canvas.drawPath(path, this.paint);
            float f3 = 0.39f * f;
            float f4 = f * 0.61f;
            float f5 = 0.2f * f2;
            float f6 = 0.76f * f2;
            fill(MainActivity.withAlpha(i, 56));
            canvas.drawRoundRect(f3, f5, f4, f6, f * 0.07f, f * 0.07f, this.paint);
            stroke(i, Math.max(2.0f, 0.025f * f));
            canvas.drawRoundRect(f3, f5, f4, f6, f * 0.07f, f * 0.07f, this.paint);
            stroke(i2, Math.max(1.5f, f * 0.018f));
            canvas.drawLine(f3, f2 * 0.47f, f4, f2 * 0.47f, this.paint);
            canvas.drawLine(f * 0.37f, f2 * 0.81f, 0.63f * f, f2 * 0.81f, this.paint);
            float fMax = Math.max(0.0f, Math.min(1.0f, this.descriptor.progress / 100.0f));
            fill(MainActivity.withAlpha(i3, 54));
            canvas.drawRoundRect((f * 0.018f) + f3, (f2 * 0.018f) + f5, f4 - (0.018f * f), Math.max((f2 * 0.04f) + f5, f6 - ((f6 - f5) * fMax)), f * 0.05f, 0.05f * f, this.paint);
            stroke(i, Math.max(3.0f, 0.045f * f));
            canvas.drawLine(f * 0.29f, f2 * 0.26f, f * 0.29f, f2 * 0.74f, this.paint);
            canvas.drawLine(f * 0.71f, f2 * 0.26f, f * 0.71f, f2 * 0.74f, this.paint);
            fill(i);
            canvas.drawCircle(0.29f * f, (0.74f - (fMax * 0.48f)) * f2, Math.max(3.0f, f * 0.04f), this.paint);
            canvas.drawCircle(f * 0.71f, f2 * (0.74f - (0.48f * fMax)), Math.max(3.0f, f * 0.04f), this.paint);
        }

        private void drawStatusRoof(Canvas canvas, RectF rectF) {
            float fWidth = rectF.left + (rectF.width() * 0.405f);
            float fWidth2 = rectF.left + (rectF.width() * 0.595f);
            float fHeight = rectF.top + (rectF.height() * 0.405f);
            float fHeight2 = rectF.top + (rectF.height() * 0.665f);
            float fMax = Math.max(2.0f, rectF.width() * 0.035f);
            fill(-1458958058);
            canvas.drawRoundRect(fWidth, fHeight, fWidth2, fHeight2, fMax, fMax, this.paint);
            float f = 1.0f - (this.descriptor.curtainLevel / 100.0f);
            if (f > 0.01f) {
                fill(-492590420);
                canvas.drawRoundRect(fWidth + 1.0f, fHeight + 1.0f, fWidth2 - 1.0f, fHeight + ((fHeight2 - fHeight) * f), fMax, fMax, this.paint);
            }
            float f2 = (((fHeight2 - fHeight) * 0.43f) * this.descriptor.sunroofLevel) / 100.0f;
            fill(1795492108);
            canvas.drawRoundRect(fWidth + 1.0f, fHeight + 1.0f + f2, fWidth2 - 1.0f, ((fHeight2 - fHeight) * 0.51f) + fHeight + f2, fMax, fMax, this.paint);
            stroke(-872085494, Math.max(1.0f, rectF.width() * 0.012f));
            canvas.drawRoundRect(fWidth, fHeight, fWidth2, fHeight2, fMax, fMax, this.paint);
            canvas.drawLine(fWidth, fHeight + ((fHeight2 - fHeight) * 0.52f), fWidth2, ((fHeight2 - fHeight) * 0.52f) + fHeight, this.paint);
        }

        private void drawStatusSeatBelts(Canvas canvas, RectF rectF) {
            String[] strArr = this.descriptor.seatBeltStates == null ? new String[0] : this.descriptor.seatBeltStates;
            float[] fArr = {0.425f, 0.575f, 0.405f, 0.5f, 0.595f};
            float[] fArr2 = {0.49f, 0.49f, 0.6f, 0.6f, 0.6f};
            for (int i = 0; i < 5 && i < strArr.length; i++) {
                if ("unfastened".equals(strArr[i])) {
                    float fWidth = rectF.left + (rectF.width() * fArr[i]);
                    float fHeight = rectF.top + (rectF.height() * fArr2[i]);
                    float fMax = Math.max(2.7f, rectF.width() * 0.04f);
                    fill(-1761483);
                    canvas.drawCircle(fWidth, fHeight, fMax, this.paint);
                    stroke(-1, Math.max(1.0f, 0.3f * fMax));
                    canvas.drawLine(fWidth - (fMax * 0.35f), fHeight - (fMax * 0.45f), (0.35f * fMax) + fWidth, fHeight + (0.45f * fMax), this.paint);
                }
            }
        }

        private void drawStatusVehicleFallback(Canvas canvas, float f, float f2, int i, int i2) {
            Path path = new Path();
            path.moveTo(f * 0.45f, f2 * 0.08f);
            path.quadTo(0.5f * f, 0.03f * f2, f * 0.55f, 0.08f * f2);
            path.lineTo(f * 0.63f, f2 * 0.25f);
            path.lineTo(0.63f * f, f2 * 0.76f);
            path.quadTo(0.6f * f, f2 * 0.86f, 0.55f * f, f2 * 0.88f);
            path.lineTo(0.45f * f, 0.88f * f2);
            path.quadTo(0.4f * f, 0.86f * f2, f * 0.37f, 0.76f * f2);
            path.lineTo(0.37f * f, 0.25f * f2);
            path.close();
            fill(MainActivity.withAlpha(i, 48));
            canvas.drawPath(path, this.paint);
            stroke(i2, Math.max(1.5f, 0.018f * f));
            canvas.drawPath(path, this.paint);
        }

        private void drawStepDots(Canvas canvas, float f, float f2, int i, int i2, int i3, int i4) {
            if (i <= 1) {
                return;
            }
            int iRound = i2 <= 0 ? -1 : Math.round((i * i2) / 100.0f) - 1;
            float fMax = Math.max(1.5f, 0.022f * f);
            float f3 = 3.1f * fMax;
            float f4 = 0.9f * f2;
            float f5 = (f * 0.5f) - (((i - 1) * f3) * 0.5f);
            int i5 = 0;
            while (i5 < i) {
                fill(i5 == iRound ? i3 : i4);
                canvas.drawCircle((i5 * f3) + f5, f4, i5 == iRound ? 1.35f * fMax : fMax, this.paint);
                i5++;
            }
        }

        private boolean drawTbtIcon(Canvas canvas, String str, float f, float f2, float f3, int i) {
            Drawable drawable;
            int iTbtIconRes = tbtIconRes(str);
            if (iTbtIconRes == 0 || (drawable = getContext().getDrawable(iTbtIconRes)) == null) {
                return false;
            }
            Drawable drawableMutate = drawable.mutate();
            drawableMutate.setColorFilter(new PorterDuffColorFilter(i, PorterDuff.Mode.SRC_IN));
            int iMax = Math.max(12, Math.round(f3));
            int iRound = Math.round(f - (iMax / 2.0f));
            int iRound2 = Math.round(f2 - (iMax / 2.0f));
            drawableMutate.setBounds(iRound, iRound2, iRound + iMax, iRound2 + iMax);
            drawableMutate.draw(canvas);
            drawableMutate.setColorFilter(null);
            return true;
        }

        private void drawTireReadouts(Canvas canvas, float f, float f2, int i, int i2) {
            String[] strArrTireReadings = MainActivity.this.tireReadings(this.descriptor);
            int i3 = 1;
            int i4 = 2;
            float[] fArr = {f * 0.29f, f * 0.71f, 0.29f * f, 0.71f * f};
            float[] fArr2 = {f2 * 0.31f, 0.31f * f2, f2 * 0.79f, 0.79f * f2};
            int i5 = 0;
            while (i5 < 4) {
                int iTireSignalColor = tireSignalColor((this.descriptor.wheelStates == null || i5 >= this.descriptor.wheelStates.length) ? "unavailable" : this.descriptor.wheelStates[i5], i2);
                this.paint.setTextAlign(((i5 == 0 || i5 == i4) ? i3 : 0) != 0 ? Paint.Align.RIGHT : Paint.Align.LEFT);
                this.paint.setTypeface(Typeface.create("sans-serif-medium", i3));
                this.paint.setTextSize(Math.max(15.0f, Math.min(f, f2) * 0.205f));
                fill(iTireSignalColor);
                canvas.drawText(strArrTireReadings[i5], fArr[i5], fArr2[i5], this.paint);
                i5++;
                i3 = 1;
                i4 = 2;
            }
            this.paint.setTextAlign(Paint.Align.CENTER);
            this.paint.setTypeface(Typeface.create("sans-serif-medium", 0));
            this.paint.setTextSize(Math.max(7.0f, Math.min(f, f2) * 0.085f));
            fill(MainActivity.withAlpha(i2, 200));
            canvas.drawText(this.descriptor.state.toUpperCase(Locale.US) + (this.descriptor.metricB.isEmpty() ? "" : " · " + this.descriptor.metricB), 0.5f * f, 0.98f * f2, this.paint);
            this.paint.setTextAlign(Paint.Align.LEFT);
            this.paint.setTypeface(Typeface.DEFAULT);
        }

        private void drawTires(Canvas canvas, float f, float f2, int i, int i2, int i3) {
            Bitmap tiresTopViewBitmap = getTiresTopViewBitmap();
            if (tiresTopViewBitmap != null) {
                drawTiresRaster(canvas, f, f2, tiresTopViewBitmap, i, i2, i3);
            } else {
                drawTiresFallback(canvas, f, f2, i, i2, i3);
            }
        }

        private void drawTiresFallback(Canvas canvas, float f, float f2, int i, int i2, int i3) {
            Path path = new Path();
            path.moveTo(f * 0.43f, f2 * 0.08f);
            path.quadTo(0.5f * f, 0.03f * f2, 0.57f * f, 0.08f * f2);
            path.lineTo(f * 0.66f, f2 * 0.2f);
            path.lineTo(0.69f * f, f2 * 0.76f);
            path.quadTo(0.66f * f, f2 * 0.91f, 0.56f * f, f2 * 0.95f);
            path.lineTo(0.44f * f, 0.95f * f2);
            path.quadTo(f * 0.34f, 0.91f * f2, 0.31f * f, f2 * 0.76f);
            path.lineTo(0.34f * f, 0.2f * f2);
            path.close();
            fill(MainActivity.withAlpha(i2, 32));
            canvas.drawPath(path, this.paint);
            stroke(i3, Math.max(2.0f, 0.026f * f));
            canvas.drawPath(path, this.paint);
            fill(MainActivity.withAlpha(i, 40));
            canvas.drawRoundRect(f * 0.39f, f2 * 0.22f, f * 0.61f, f2 * 0.67f, f * 0.075f, f * 0.075f, this.paint);
            stroke(i2, Math.max(1.5f, 0.018f * f));
            canvas.drawRoundRect(f * 0.39f, f2 * 0.22f, f * 0.61f, f2 * 0.67f, f * 0.075f, f * 0.075f, this.paint);
            canvas.drawLine(f * 0.39f, f2 * 0.43f, f * 0.61f, f2 * 0.43f, this.paint);
            canvas.drawLine(f * 0.38f, f2 * 0.76f, f * 0.62f, f2 * 0.76f, this.paint);
            drawTireReadouts(canvas, f, f2, i, i2);
        }

        private void drawTiresRaster(Canvas canvas, float f, float f2, Bitmap bitmap, int i, int i2, int i3) {
            float f3 = f2 * 0.88f;
            float fMin = Math.min((bitmap.getWidth() * f3) / Math.max(1.0f, bitmap.getHeight()), 0.3f * f);
            float f4 = (f - fMin) * 0.5f;
            float f5 = (f2 - f3) * 0.5f;
            RectF rectF = new RectF(f4, f5, f4 + fMin, f5 + f3);
            this.paint.setAlpha(MainActivity.this.dockUiLight ? 238 : 255);
            canvas.drawBitmap(bitmap, (Rect) null, rectF, this.paint);
            this.paint.setAlpha(255);
            drawTireReadouts(canvas, f, f2, i, i2);
        }

        private void drawVehicleStatus(Canvas canvas, float f, float f2, int i, int i2, int i3) {
            int i4;
            float f3;
            boolean z;
            String str;
            boolean z2;
            String str2;
            float fMeasureText;
            float fMeasureText2;
            float f4;
            float f5;
            float f6;
            String strTrim;
            Paint.Align align;
            Bitmap statusVehicleBitmap;
            Bitmap statusVehicleBitmap2;
            int i5;
            Bitmap statusVehicleBitmap3 = getStatusVehicleBitmap("base.png");
            float f7 = f2 * 1.34f;
            float fMin = Math.min(statusVehicleBitmap3 == null ? 0.25f * f : ((statusVehicleBitmap3.getWidth() * f7) / Math.max(1.0f, statusVehicleBitmap3.getHeight())) * 1.08f, 0.42f * f);
            float f8 = (f - fMin) * 0.5f;
            float f9 = (-f2) * 0.019f;
            String[] strArr = this.descriptor.openingStates == null ? new String[0] : this.descriptor.openingStates;
            float f10 = (strArr.length <= 4 || !"open".equals(strArr[4])) ? 0.0f : 1.0f;
            String[] strArr2 = strArr;
            long jUptimeMillis = SystemClock.uptimeMillis();
            String str3 = "open";
            float fMin2 = this.statusLiftLastMs == 0 ? 0.0f : Math.min(64.0f, jUptimeMillis - this.statusLiftLastMs);
            this.statusLiftLastMs = jUptimeMillis;
            float f11 = f10 - this.statusLift;
            if (Math.abs(f11) > 0.001f) {
                this.statusLift += Math.signum(f11) * Math.min(Math.abs(f11), fMin2 / 320.0f);
                postInvalidateOnAnimation();
            } else {
                this.statusLift = f10;
                this.statusLiftLastMs = 0L;
            }
            float f12 = f9 - ((f7 - (1.02f * f2)) * ((3.0f - (this.statusLift * 2.0f)) * (this.statusLift * this.statusLift)));
            RectF rectF = new RectF(f8, f12, f8 + fMin, f12 + f7);
            if (statusVehicleBitmap3 != null) {
                this.paint.setAlpha(MainActivity.this.dockUiLight ? 238 : 255);
                canvas.drawBitmap(statusVehicleBitmap3, (Rect) null, rectF, this.paint);
                this.paint.setAlpha(255);
                i4 = i2;
            } else {
                canvas.save();
                canvas.translate(f8 - (0.37f * f), 0.0f);
                i4 = i2;
                drawStatusVehicleFallback(canvas, f, f2, i4, i3);
                canvas.restore();
            }
            String[] strArr3 = {"door-fl-cavity-v1.png", "door-fr-cavity-v1.png", "door-rl-cavity-v1.png", "door-rr-cavity-v1.png"};
            int i6 = 0;
            while (true) {
                if (i6 >= strArr3.length) {
                    break;
                }
                String[] strArr4 = strArr2;
                String[] strArr5 = strArr3;
                String str4 = str3;
                if (str4.equals(i6 < strArr4.length ? strArr4[i6] : "unknown")) {
                    i5 = i6;
                    Bitmap statusVehicleBitmap4 = getStatusVehicleBitmap(strArr5[i5]);
                    if (statusVehicleBitmap4 != null) {
                        canvas.drawBitmap(statusVehicleBitmap4, (Rect) null, rectF, this.paint);
                    }
                } else {
                    i5 = i6;
                }
                i6 = i5 + 1;
                str3 = str4;
                strArr3 = strArr5;
                strArr2 = strArr4;
            }
            String[] strArr6 = strArr2;
            String str5 = str3;
            int i7 = 0;
            for (String[] strArr7 = {"door-fl-closed-v4.png", "door-fr-closed-v4.png", "door-rl-closed-v4.png", "door-rr-closed-v4.png"}; i7 < strArr7.length; strArr7 = strArr7) {
                if (!str5.equals(i7 < strArr6.length ? strArr6[i7] : "unknown") && (statusVehicleBitmap2 = getStatusVehicleBitmap(strArr7[i7])) != null) {
                    canvas.drawBitmap(statusVehicleBitmap2, (Rect) null, rectF, this.paint);
                }
                i7++;
            }
            String[] strArr8 = {"door-fl-open-v3.png", "door-fr-open-v3.png", "door-rl-open-v3.png", "door-rr-open-v3.png", "tailgate-open-v2.png"};
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(1.55f);
            this.paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
            int i8 = 0;
            while (i8 < 5) {
                if (str5.equals(i8 < strArr6.length ? strArr6[i8] : "unknown") && (statusVehicleBitmap = getStatusVehicleBitmap(strArr8[i8])) != null) {
                    canvas.drawBitmap(statusVehicleBitmap, (Rect) null, rectF, this.paint);
                }
                i8++;
            }
            this.paint.setColorFilter(null);
            drawStatusRoof(canvas, rectF);
            drawStatusSeatBelts(canvas, rectF);
            float fMax = Math.max(10.0f, 0.048f * f);
            float[] fArr = {rectF.left - fMax, rectF.right + fMax, rectF.left - fMax, rectF.right + fMax};
            int i9 = 2;
            float[] fArr2 = {f2 * 0.24f, 0.24f * f2, f2 * 0.7f, 0.7f * f2};
            String[] strArrTireReadings = MainActivity.this.tireReadings(this.descriptor);
            String[] strArrSplit = this.descriptor.tireTemperatures.split("\\s*/\\s*", -1);
            String str6 = this.descriptor.tirePressureUnit;
            Typeface typefaceCreate = Typeface.create("sans-serif-medium", 1);
            Typeface typefaceCreate2 = Typeface.create("sans-serif", 0);
            Typeface typefaceCreate3 = Typeface.create("sans-serif-light", 0);
            float fMax2 = Math.max(16.0f, Math.min(f, f2) * 0.245f);
            float fMax3 = Math.max(9.0f, fMax2 * 0.5f);
            int i10 = 0;
            while (true) {
                long j = jUptimeMillis;
                if (i10 >= 4) {
                    this.paint.setTextAlign(Paint.Align.LEFT);
                    this.paint.setTypeface(Typeface.DEFAULT);
                    return;
                }
                String str7 = (this.descriptor.wheelStates == null || i10 >= this.descriptor.wheelStates.length) ? "unavailable" : this.descriptor.wheelStates[i10];
                    z = true;
                if (i10 != 0) {
                    f3 = fMin2;
                    if (i10 != i9) {
                        z = false;
                    }
                    str = strArrTireReadings[i10];
                    if (!"unavailable".equals(str) || "—".equals(str)) {
                        z2 = false;
                    } else {
                        z2 = true;
                    }
                    if (z2 || str6.isEmpty()) {
                        str2 = "";
                    } else {
                        str2 = " " + str6;
                    }
                    this.paint.setTypeface(typefaceCreate2);
                    this.paint.setTextSize(fMax3);
                    fMeasureText = this.paint.measureText(str2);
                    String str8 = str6;
                    this.paint.setTypeface(typefaceCreate);
                    this.paint.setTextSize(fMax2);
                    fMeasureText2 = this.paint.measureText(str);
                    if (z) {
                        f4 = (fArr[i10] - fMeasureText2) - fMeasureText;
                    } else {
                        f4 = fArr[i10];
                    }
                    f5 = f4;
                    f6 = fArr2[i10] + (0.34f * fMax2);
                    float f13 = fMax2;
                    Typeface typeface = typefaceCreate;
                    this.paint.setTextAlign(Paint.Align.LEFT);
                    fill(tireSignalColor(str7, i4));
                    canvas.drawText(str, f5, f6, this.paint);
                    if (!str2.isEmpty()) {
                        this.paint.setTypeface(typefaceCreate2);
                        this.paint.setTextSize(fMax3);
                        fill(MainActivity.withAlpha(i4, 200));
                        canvas.drawText(str2, f5 + fMeasureText2, f6, this.paint);
                    }
                    strTrim = i10 < strArrSplit.length ? strArrSplit[i10].trim() : "";
                    if (!strTrim.isEmpty()) {
                        this.paint.setTypeface(typefaceCreate3);
                        this.paint.setTextSize(fMax3 * 1.1f);
                        Paint paint = this.paint;
                        if (z) {
                            align = Paint.Align.RIGHT;
                        } else {
                            align = Paint.Align.LEFT;
                        }
                        paint.setTextAlign(align);
                        fill(-7367525);
                        canvas.drawText(strTrim, fArr[i10], (1.35f * fMax3) + f6, this.paint);
                    }
                    i10++;
                    i4 = i2;
                    fMin2 = f3;
                    jUptimeMillis = j;
                    str6 = str8;
                    fMax2 = f13;
                    typefaceCreate = typeface;
                    i9 = 2;
                    continue;
                } else {
                    f3 = fMin2;
                }
                z = true;
                str = strArrTireReadings[i10];
                if ("unavailable".equals(str)) {
                    z2 = false;
                } else {
                    z2 = false;
                }
                if (z2) {
                    str2 = "";
                } else {
                    str2 = "";
                }
                this.paint.setTypeface(typefaceCreate2);
                this.paint.setTextSize(fMax3);
                fMeasureText = this.paint.measureText(str2);
                String str9 = str6;
                this.paint.setTypeface(typefaceCreate);
                this.paint.setTextSize(fMax2);
                fMeasureText2 = this.paint.measureText(str);
                if (z) {
                    f4 = (fArr[i10] - fMeasureText2) - fMeasureText;
                } else {
                    f4 = fArr[i10];
                }
                f5 = f4;
                f6 = fArr2[i10] + (0.34f * fMax2);
                float f14 = fMax2;
                Typeface typeface2 = typefaceCreate;
                this.paint.setTextAlign(Paint.Align.LEFT);
                fill(tireSignalColor(str7, i4));
                canvas.drawText(str, f5, f6, this.paint);
                if (!str2.isEmpty()) {
                    this.paint.setTypeface(typefaceCreate2);
                    this.paint.setTextSize(fMax3);
                    fill(MainActivity.withAlpha(i4, 200));
                    canvas.drawText(str2, f5 + fMeasureText2, f6, this.paint);
                }
                strTrim = i10 < strArrSplit.length ? strArrSplit[i10].trim() : "";
                if (!strTrim.isEmpty()) {
                    this.paint.setTypeface(typefaceCreate3);
                    this.paint.setTextSize(fMax3 * 1.1f);
                    Paint paint2 = this.paint;
                    if (z) {
                        align = Paint.Align.RIGHT;
                    } else {
                        align = Paint.Align.LEFT;
                    }
                    paint2.setTextAlign(align);
                    fill(-7367525);
                    canvas.drawText(strTrim, fArr[i10], (1.35f * fMax3) + f6, this.paint);
                }
                i10++;
                i4 = i2;
                fMin2 = f3;
                jUptimeMillis = j;
                str6 = str9;
                fMax2 = f14;
                typefaceCreate = typeface2;
                i9 = 2;
            }
        }

        private String ellipsizeNav(String str, float f) {
            if (str == null || str.isEmpty() || f <= 0.0f) {
                return "";
            }
            if (this.paint.measureText(str) <= f) {
                return str;
            }
            int length = str.length();
            while (length > 0 && this.paint.measureText(str.substring(0, length) + "…") > f) {
                length--;
            }
            return length <= 0 ? "…" : str.substring(0, length) + "…";
        }

        private String ellipsizeStatusText(String str, float f) {
            if (str == null || str.isEmpty() || this.paint.measureText(str) <= f) {
                return str == null ? "" : str;
            }
            int length = str.length();
            while (length > 0 && this.paint.measureText(str.substring(0, length) + "…") > f) {
                length--;
            }
            return str.substring(0, length) + "…";
        }

        private void fill(int i) {
            this.paint.setStyle(Paint.Style.FILL);
            this.paint.setColor(i);
        }

        private Bitmap getPowerChassisBitmap(String str) {
            if (this.powerGhostBitmaps.containsKey("approved-atlas")) {
                return this.powerGhostBitmaps.get("approved-atlas");
            }
            Bitmap bitmapDecodeStream = null;
            try {
                InputStream inputStreamOpen = MainActivity.this.getAssets().open("www/assets/power/graphics/approved-chassis-atlas.png");
                try {
                    bitmapDecodeStream = BitmapFactory.decodeStream(inputStreamOpen);
                    if (inputStreamOpen != null) {
                        inputStreamOpen.close();
                    }
                } catch (Throwable th) {
                    if (inputStreamOpen != null) {
                        try {
                            inputStreamOpen.close();
                        } catch (Throwable th2) {
                            th.addSuppressed(th2);
                        }
                    }
                    throw th;
                }
            } catch (IOException | RuntimeException e) {
                Log.w("H6Viewer", "Optional Power top-down chassis asset unavailable", e);
            }
            this.powerGhostBitmaps.put("approved-atlas", bitmapDecodeStream);
            return bitmapDecodeStream;
        }

        private Bitmap getStatusVehicleBitmap(String str) {
            if (this.statusVehicleBitmaps.containsKey(str)) {
                return this.statusVehicleBitmaps.get(str);
            }
            Bitmap bitmapDecodeStream = null;
            try {
                InputStream inputStreamOpen = MainActivity.this.getAssets().open("www/assets/ui/vehicle-status/" + str);
                try {
                    bitmapDecodeStream = BitmapFactory.decodeStream(inputStreamOpen);
                    if (inputStreamOpen != null) {
                        inputStreamOpen.close();
                    }
                } catch (Throwable th) {
                    if (inputStreamOpen != null) {
                        try {
                            inputStreamOpen.close();
                        } catch (Throwable th2) {
                            th.addSuppressed(th2);
                        }
                    }
                    throw th;
                }
            } catch (IOException | RuntimeException e) {
                Log.w("H6Viewer", "Optional status vehicle asset unavailable: " + str, e);
            }
            this.statusVehicleBitmaps.put(str, bitmapDecodeStream);
            return bitmapDecodeStream;
        }

        private Bitmap getTiresTopViewBitmap() {
            if (this.tiresTopViewDecodeAttempted) {
                return this.tiresTopViewBitmap;
            }
            this.tiresTopViewDecodeAttempted = true;
            try {
                InputStream inputStreamOpen = MainActivity.this.getAssets().open("www/assets/ui/tires-top-view-v1.png");
                try {
                    this.tiresTopViewBitmap = BitmapFactory.decodeStream(inputStreamOpen);
                    if (inputStreamOpen != null) {
                        inputStreamOpen.close();
                    }
                } catch (Throwable th) {
                    if (inputStreamOpen != null) {
                        try {
                            inputStreamOpen.close();
                        } catch (Throwable th2) {
                            th.addSuppressed(th2);
                        }
                    }
                    throw th;
                }
            } catch (IOException | RuntimeException e) {
                Log.w("H6Viewer", "Optional Tires top-view asset unavailable; using vector fallback", e);
                this.tiresTopViewBitmap = null;
            }
            return this.tiresTopViewBitmap;
        }

        private boolean hasOpenStatusOpening(String[] strArr) {
            for (String str : strArr) {
                if ("open".equals(str)) {
                    return true;
                }
            }
            return false;
        }

        private float[] measureNavMetricWidths(List<String[]> list, float f, float f2) {
            float[] fArr = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                this.paint.setTextSize(f);
                float fMeasureText = this.paint.measureText(list.get(i)[0]);
                this.paint.setTextSize(f2);
                fArr[i] = Math.max(fMeasureText, this.paint.measureText(list.get(i)[1]));
            }
            return fArr;
        }

        private boolean powerMotionEnabled() {
            if (!isShown() || getWindowVisibility() != 0 || !hasWindowFocus()) {
                return false;
            }
            try {
                return Settings.Global.getFloat(MainActivity.this.getContentResolver(), "animator_duration_scale", 1.0f) > 0.0f;
            } catch (RuntimeException e) {
                return true;
            }
        }

        private void stroke(int i, float f) {
            this.paint.setStyle(Paint.Style.STROKE);
            this.paint.setStrokeWidth(f);
            this.paint.setStrokeCap(Paint.Cap.ROUND);
            this.paint.setStrokeJoin(Paint.Join.ROUND);
            this.paint.setColor(i);
        }

        private float sumNavMetricWidths(float[] fArr, float f) {
            float f2 = 0.0f;
            for (int i = 0; i < fArr.length; i++) {
                f2 += fArr[i];
                if (i > 0) {
                    f2 += f;
                }
            }
            return f2;
        }

        private int tbtIconRes(String str) {
            if (str == null) {
                return 0;
            }
            switch (str) {
                case "turn_right":
                    return R.drawable.ic_tbt_turn_right;
                case "turn_left":
                    return R.drawable.ic_tbt_turn_left;
                case "straight":
                    return R.drawable.ic_tbt_straight;
                case "uturn":
                    return R.drawable.ic_tbt_uturn;
                case "roundabout":
                    return R.drawable.ic_tbt_roundabout;
                case "fork":
                    return R.drawable.ic_tbt_fork;
                case "merge":
                    return R.drawable.ic_tbt_merge;
                default:
                    return 0;
            }
        }

        private int tireSignalColor(String str, int i) {
            if ("warning".equals(str)) {
                return -19646;
            }
            if ("unavailable".equals(str) || "unavailable".equals(this.descriptor.state)) {
                return MainActivity.withAlpha(i, 168);
            }
            if (!"demo".equals(this.descriptor.state) && "stale".equals(this.descriptor.state)) {
                return -1857982;
            }
            return MainActivity.this.dockLabelColor();
        }

        @Override // android.view.View
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float height = getHeight();
            if (width > 0.0f && height > 0.0f) {
                int i = MainActivity.this.dockAccentColor;
                int i2 = MainActivity.this.dockUiLight ? 1429355592 : 1728053247;
                int i3 = MainActivity.this.dockUiLight ? -869978053 : -419430401;
                switch (this.descriptor.id) {
                    case "range":
                        drawRing(canvas, width, height, i, i2, true);
                        break;
                    case "status":
                        drawVehicleStatus(canvas, width, height, i, i2, i3);
                        break;
                    case "climate":
                        drawClimate(canvas, width, height, i, i2);
                        break;
                    case "consumption":
                        drawEnergy(canvas, width, height, i, i2, i3);
                        break;
                    case "navigation":
                        drawNavigation(canvas, width, height, i, i2, i3);
                        break;
                    case "tires":
                        drawTires(canvas, width, height, i, i2, i3);
                        break;
                    case "power":
                        drawPower(canvas, width, height, i, i2, i3);
                        break;
                    case "clock":
                        drawClock(canvas, width, height, i, i2, i3);
                        break;
                    case "desktops":
                        drawDesktops(canvas, width, height, i, i2);
                        break;
                    case "driveMode":
                        drawDriveMode(canvas, width, height, i, i2, i3);
                        break;
                    case "powerMode":
                        drawPowerMode(canvas, width, height, i, i2, i3);
                        break;
                    case "regen":
                        drawRegen(canvas, width, height, i, i2, i3);
                        break;
                    case "roof":
                        drawRoof(canvas, width, height, i, i2, i3);
                        break;
                    default:
                        drawRing(canvas, width, height, i, i2, false);
                        break;
                }
            }
        }

        @Override // android.view.View
        public void onWindowFocusChanged(boolean z) {
            super.onWindowFocusChanged(z);
            if (z && this.descriptor != null && "power".equals(this.descriptor.id)) {
                invalidate();
            }
        }

        void setDescriptor(BottomCardDescriptor bottomCardDescriptor) {
            this.descriptor = bottomCardDescriptor;
            invalidate();
        }
    }

    private final class QuickClockCardView extends View {
        private BottomCardDescriptor descriptor;
        private final Runnable minuteTick;
        private final Paint paint;
        private Bitmap renderedFace;
        private ClockSnapshot snapshot;

        QuickClockCardView(Context context, BottomCardDescriptor bottomCardDescriptor) {
            super(context);
            this.paint = new Paint(3);
            this.minuteTick = new Runnable() {
                @Override // java.lang.Runnable
                public void run() {
                    QuickClockCardView.this.refreshNow();
                    QuickClockCardView.this.scheduleMinuteTick();
                }
            };
            this.descriptor = bottomCardDescriptor;
            setWillNotDraw(false);
            setImportantForAccessibility(2);
            refreshNow();
        }

        private void drawDateSpine(Canvas canvas, float f, float f2, int i, int i2, int i3) {
            float f3 = f * 0.29f;
            this.paint.setStyle(Paint.Style.FILL);
            this.paint.setColor(MainActivity.withAlpha(i3, MainActivity.this.dockUiLight ? 36 : 50));
            canvas.drawRoundRect(new RectF(0.0f, 0.0f, f3, f2), f2 * 0.1f, f2 * 0.1f, this.paint);
            type(i, 0.17f * f2, Paint.Align.CENTER, true);
            canvas.drawText(this.snapshot.spineTop, f3 * 0.5f, 0.33f * f2, this.paint);
            type(i, 0.29f * f2, Paint.Align.CENTER, true);
            canvas.drawText(this.snapshot.day, f3 * 0.5f, 0.64f * f2, this.paint);
            type(i2, f2 * 0.1f, Paint.Align.CENTER, false);
            canvas.drawText(this.snapshot.weekday, 0.5f * f3, 0.84f * f2, this.paint);
            this.paint.setColor(i3);
            canvas.drawRect(0.4f * f3, 0.94f * f2, f3 * 0.6f, 0.98f * f2, this.paint);
            type(i, fit(this.snapshot.time, 0.52f * f2, (f - f3) - (f * 0.12f)), Paint.Align.LEFT, true);
            canvas.drawText(this.snapshot.time, (0.06f * f) + f3, f2 * 0.6f, this.paint);
            if (this.snapshot.period.isEmpty()) {
                return;
            }
            type(i2, f2 * 0.12f, Paint.Align.LEFT, true);
            canvas.drawText(this.snapshot.period, (0.07f * f) + f3, 0.8f * f2, this.paint);
        }

        private void drawDial(Canvas canvas, float f, float f2, float f3, int i, int i2, int i3) {
            Canvas canvas2 = canvas;
            this.paint.setStyle(Paint.Style.STROKE);
            this.paint.setStrokeWidth(Math.max(1.5f, 0.04f * f3));
            this.paint.setStrokeCap(Paint.Cap.ROUND);
            this.paint.setColor(i2);
            canvas2.drawCircle(f, f2, f3, this.paint);
            if (!"plain".equals(this.descriptor.dialMarks)) {
                int i4 = 0;
                while (i4 < 12) {
                    double radians = Math.toRadians((((double) i4) * 30.0d) - 90.0d);
                    float f4 = f3 * 0.88f;
                    float f5 = f3 * (i4 % 3 == 0 ? 0.7f : 0.78f);
                    canvas2.drawLine((((float) Math.cos(radians)) * f5) + f, (((float) Math.sin(radians)) * f5) + f2, (((float) Math.cos(radians)) * f4) + f, (((float) Math.sin(radians)) * f4) + f2, this.paint);
                    i4++;
                    canvas2 = canvas;
                }
            }
            double radians2 = Math.toRadians(this.snapshot.minuteAngle - 90.0f);
            double radians3 = Math.toRadians(this.snapshot.hourAngle - 90.0f);
            this.paint.setColor(i3);
            this.paint.setStrokeWidth(Math.max(1.8f, 0.055f * f3));
            canvas.drawLine(f, f2, f + (((float) Math.cos(radians2)) * f3 * 0.7f), f2 + (((float) Math.sin(radians2)) * f3 * 0.7f), this.paint);
            this.paint.setColor(i);
            this.paint.setStrokeWidth(Math.max(2.4f, 0.075f * f3));
            canvas.drawLine(f, f2, f + (((float) Math.cos(radians3)) * f3 * 0.48f), f2 + (((float) Math.sin(radians3)) * f3 * 0.48f), this.paint);
            this.paint.setStyle(Paint.Style.FILL);
            this.paint.setColor(i3);
            canvas.drawCircle(f, f2, Math.max(2.0f, 0.08f * f3), this.paint);
        }

        private void drawMeridian(Canvas canvas, float f, float f2, int i, int i2, int i3) {
            float fMin = Math.min(f2 * 0.43f, 0.25f * f);
            drawDial(canvas, fMin + 8.0f, f2 * 0.5f, fMin, i, i2, i3);
            float fMax = Math.max((fMin * 2.0f) + 22.0f, f * 0.58f);
            float f3 = f - 8.0f;
            this.paint.setStyle(Paint.Style.FILL);
            this.paint.setColor(MainActivity.withAlpha(i2, 112));
            canvas.drawRect(fMax, (f2 * 0.5f) - 0.5f, f3, (f2 * 0.5f) + 0.5f, this.paint);
            float f4 = 0.5f * (fMax + f3);
            type(i, fit(this.snapshot.hour, f2 * 0.42f, f3 - fMax), Paint.Align.CENTER, true);
            canvas.drawText(this.snapshot.hour, f4, 0.43f * f2, this.paint);
            type(i, fit(this.snapshot.minute, 0.42f * f2, f3 - fMax), Paint.Align.CENTER, true);
            canvas.drawText(this.snapshot.minute, f4, 0.82f * f2, this.paint);
            this.paint.setStyle(Paint.Style.FILL);
            this.paint.setColor(MainActivity.this.dockUiLight ? -1972758 : -15854565);
            canvas.drawRect(f4 - (f2 * 0.1f), 0.46f * f2, (0.1f * f2) + f4, f2 * 0.58f, this.paint);
            type(i2, 0.2f * f2, Paint.Align.CENTER, false);
            canvas.drawText(":", f4, 0.58f * f2, this.paint);
            type(i2, 0.11f * f2, Paint.Align.RIGHT, false);
            if (this.snapshot.period.isEmpty()) {
                return;
            }
            canvas.drawText(this.snapshot.period, f3, 0.97f * f2, this.paint);
        }

        private void drawPanorama(Canvas canvas, float f, float f2, int i, int i2, int i3) {
            type(i, fit(this.snapshot.time, f2 * 0.48f, f * 0.78f), Paint.Align.LEFT, true);
            canvas.drawText(this.snapshot.time, 0.04f * f, 0.38f * f2, this.paint);
            if (!this.snapshot.period.isEmpty()) {
                type(i2, 0.16f * f2, Paint.Align.RIGHT, true);
                canvas.drawText(this.snapshot.period, f * 0.96f, 0.48f * f2, this.paint);
            }
            type(i2, 0.12f * f2, Paint.Align.LEFT, false);
            float fMin = Math.min(24.0f, 0.28f * f2);
            drawDial(canvas, fMin + 8.0f, (f2 - fMin) - 4.0f, fMin, i, i2, i3);
            canvas.drawText(this.snapshot.weekday, (fMin * 2.0f) + 20.0f, 0.78f * f2, this.paint);
            canvas.drawText(this.snapshot.dateShort, (2.0f * fMin) + 20.0f, 0.96f * f2, this.paint);
        }

        private void drawSplit(Canvas canvas, float f, float f2, int i, int i2, int i3) {
            int i4;
            float fMax = Math.max(18.0f, 0.075f * f);
            float f3 = ((0.82f * f) - fMax) / 2.0f;
            float f4 = 0.57f * f2;
            float f5 = f * 0.09f;
            float f6 = f2 * 0.12f;
            this.paint.setStyle(Paint.Style.FILL);
            Paint paint = this.paint;
            boolean zEquals = "flat".equals(this.descriptor.splitPlates);
            MainActivity mainActivity = MainActivity.this;
            if (zEquals) {
                i4 = mainActivity.dockUiLight ? 307454321 : 419430399;
            } else {
                i4 = mainActivity.dockUiLight ? 559112561 : 687865855;
            }
            paint.setColor(i4);
            canvas.drawRoundRect(new RectF(f5, f6, f5 + f3, f6 + f4), f2 * 0.09f, f2 * 0.09f, this.paint);
            canvas.drawRoundRect(new RectF(f5 + f3 + fMax, f6, (2.0f * f3) + f5 + fMax, f6 + f4), f2 * 0.09f, 0.09f * f2, this.paint);
            type(i, fit(this.snapshot.hour, 0.7f * f4, 0.78f * f3), Paint.Align.CENTER, true);
            canvas.drawText(this.snapshot.hour, (f3 * 0.5f) + f5, (f4 * 0.74f) + f6, this.paint);
            canvas.drawText(this.snapshot.minute, f5 + f3 + fMax + (f3 * 0.5f), (0.74f * f4) + f6, this.paint);
            type(i2, 0.34f * f4, Paint.Align.CENTER, false);
            canvas.drawText(":", f5 + f3 + (fMax * 0.5f), (0.63f * f4) + f6, this.paint);
            type(i2, 0.12f * f2, Paint.Align.CENTER, false);
            canvas.drawText(this.snapshot.weekday + " · " + this.snapshot.dateShort + (this.snapshot.period.isEmpty() ? "" : " · " + this.snapshot.period), 0.5f * f, 0.9f * f2, this.paint);
        }

        private float fit(String str, float f, float f2) {
            for (float f3 = f; f3 > 10.0f; f3 -= 1.0f) {
                this.paint.setTextSize(f3);
                if (this.paint.measureText(str) <= f2) {
                    return f3;
                }
            }
            return 10.0f;
        }

        private void scheduleMinuteTick() {
            MainActivity.this.mainHandler.removeCallbacks(this.minuteTick);
            MainActivity.this.mainHandler.postDelayed(this.minuteTick, (60000 - (System.currentTimeMillis() % 60000)) + 20);
        }

        private void type(int i, float f, Paint.Align align, boolean z) {
            this.paint.setStyle(Paint.Style.FILL);
            this.paint.setColor(i);
            this.paint.setTextSize(f);
            this.paint.setTextAlign(align);
            this.paint.setTypeface(Typeface.create(z ? "sans-serif-medium" : "sans-serif", 0));
        }

        @Override // android.view.View
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            refreshNow();
            scheduleMinuteTick();
        }

        @Override // android.view.View
        protected void onDetachedFromWindow() {
            MainActivity.this.mainHandler.removeCallbacks(this.minuteTick);
            super.onDetachedFromWindow();
        }

        @Override // android.view.View
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float height = getHeight();
            if (width > 0.0f && height > 0.0f) {
                if (this.renderedFace != null) {
                    canvas.drawBitmap(this.renderedFace, (Rect) null, new RectF(0.0f, 0.0f, width, height), this.paint);
                    return;
                }
                if (this.snapshot != null && this.descriptor != null) {
                    int iDockLabelColor = MainActivity.this.dockLabelColor();
                    int i = MainActivity.this.dockUiLight ? -1722588815 : -1195982130;
                    int i2 = MainActivity.this.dockAccentColor;
                    if ("meridian".equals(this.descriptor.clockFace)) {
                        drawMeridian(canvas, width, height, iDockLabelColor, i, i2);
                        return;
                    }
                    if ("split".equals(this.descriptor.clockFace)) {
                        drawSplit(canvas, width, height, iDockLabelColor, i, i2);
                    } else if ("date-spine".equals(this.descriptor.clockFace)) {
                        drawDateSpine(canvas, width, height, iDockLabelColor, i, i2);
                    } else {
                        drawPanorama(canvas, width, height, iDockLabelColor, i, i2);
                    }
                }
            }
        }

        void refreshNow() {
            this.snapshot = ClockSnapshot.from(this.descriptor, getContext());
            invalidate();
        }

        void setDescriptor(BottomCardDescriptor bottomCardDescriptor) {
            this.descriptor = bottomCardDescriptor;
            refreshNow();
        }

        void setRenderedFace(Bitmap renderedFace) {
            this.renderedFace = renderedFace;
            invalidate();
        }
    }

    private final class QuickMediaBarsView extends View {
        private static final int BARS = 24;
        private static final long FRAME_MS = 66;
        private final RectF bar;
        private final Runnable frame;
        private final float[] level;
        private final float[] liveTarget;
        private final Paint paint;
        private boolean playing;
        private long startedAt;
        private int tint;
        private boolean visible;

        QuickMediaBarsView(Context context) {
            super(context);
            this.paint = new Paint(1);
            this.bar = new RectF();
            this.level = new float[24];
            this.liveTarget = new float[24];
            this.visible = true;
            this.frame = new Runnable() {
                @Override // java.lang.Runnable
                public void run() {
                    if (QuickMediaBarsView.this.visible && QuickMediaBarsView.this.isAttachedToWindow()) {
                        boolean z = MainActivity.this.mediaAudioViz != null && MainActivity.this.mediaAudioViz.hasLiveAudio();
                        if (QuickMediaBarsView.this.playing || z) {
                            QuickMediaBarsView.this.invalidate();
                            QuickMediaBarsView.this.postDelayed(this, 66L);
                        }
                    }
                }
            };
            setWillNotDraw(false);
            setImportantForAccessibility(2);
            retint();
        }

        void ensureTicking() {
            if (this.visible && isAttachedToWindow()) {
                removeCallbacks(this.frame);
                post(this.frame);
            }
        }

        @Override // android.view.View
        protected void onDetachedFromWindow() {
            removeCallbacks(this.frame);
            super.onDetachedFromWindow();
        }

        @Override // android.view.View
        protected void onDraw(Canvas canvas) {
            float fSin;
            super.onDraw(canvas);
            float width = getWidth();
            float height = getHeight();
            float fUptimeMillis = 0.0f;
            if (width > 0.0f && height > 0.0f) {
                float f = getResources().getDisplayMetrics().density;
                float f2 = 3.0f * f;
                float f3 = 5.0f * f;
                float f4 = (width - (9.0f * f)) - ((24.0f * f2) + (f3 * 23.0f));
                if (f4 < 0.0f) {
                    return;
                }
                float f5 = height - (10.0f * f);
                float f6 = f5 - (0.24f * height);
                if (f6 <= 0.0f) {
                    return;
                }
                boolean z = MainActivity.this.mediaAudioViz != null && MainActivity.this.mediaAudioViz.copyLiveLevels(this.liveTarget);
                if (this.playing) {
                    fUptimeMillis = (SystemClock.uptimeMillis() - this.startedAt) / 1000.0f;
                }
                int i = 0;
                while (i < 24) {
                    if (z) {
                        fSin = this.liveTarget[i];
                    } else if (this.playing) {
                        fSin = (float) ((0.26d * Math.sin((((double) fUptimeMillis) * 2.7d) + (((double) i) * 0.9d))) + 0.46d + (0.18d * Math.sin((((double) fUptimeMillis) * 1.3d) + (((double) i) * 2.1d))) + (0.1d * Math.sin((((double) fUptimeMillis) * 4.1d) + (((double) i) * 0.4d))));
                    } else {
                        fSin = 0.1f;
                    }
                    float f7 = z ? 0.02f : 0.06f;
                    if (fSin < f7) {
                        fSin = f7;
                    }
                    if (fSin > 1.0f) {
                        fSin = 1.0f;
                    }
                    float f8 = z ? 0.38f : !this.playing ? 0.22f : 0.55f;
                    float[] fArr = this.level;
                    fArr[i] = fArr[i] + ((fSin - this.level[i]) * f8);
                    float f9 = (i * (f2 + f3)) + f4;
                    this.bar.set(f9, f5 - (this.level[i] * f6), f9 + f2, f5);
                    this.paint.setColor(MainActivity.withAlpha(this.tint, Math.round((42.0f * (i / 23.0f)) + 16.0f)));
                    canvas.drawRoundRect(this.bar, f2 * 0.5f, f2 * 0.5f, this.paint);
                    i++;
                }
                if (this.playing || z) {
                    return;
                }
                boolean z2 = true;
                for (int i2 = 0; i2 < 24; i2++) {
                    if (Math.abs(this.level[i2] - 0.1f) > 0.01f) {
                        z2 = false;
                        break;
                    }
                }
                if (z2) {
                    return;
                }
                postDelayed(this.frame, 66L);
            }
        }

        @Override // android.view.View
        public void onVisibilityAggregated(boolean visible) {
            super.onVisibilityAggregated(visible);
            if (this.visible == visible) {
                return;
            }
            this.visible = visible;
            removeCallbacks(this.frame);
            if (this.visible) {
                ensureTicking();
            }
        }

        void retint() {
            this.tint = MainActivity.this.dockAccentColor;
            invalidate();
        }

        void setPlaying(boolean playing) {
            if (this.playing != playing) {
                this.playing = playing;
                if (this.playing) {
                    this.startedAt = SystemClock.uptimeMillis();
                }
            }
            removeCallbacks(this.frame);
            MainActivity.this.syncMediaVisualizerWanted(MainActivity.this.mediaAudioWanted());
            if (this.visible) {
                ensureTicking();
            } else {
                invalidate();
            }
        }
    }

    private static final class QuickMenuRow {
        final String command;
        final String label;
        final boolean selected;

        QuickMenuRow(String label, String command, boolean selected) {
            this.label = label;
            this.command = command;
            this.selected = selected;
        }
    }

    private static final class RailBadge extends Drawable {
        private final boolean dots;
        private final Paint fill = new Paint(1);
        private final Paint stroke = new Paint(1);
        private final Paint dot = new Paint(1);

        RailBadge(float f, boolean dots) {
            this.dots = dots;
            this.fill.setColor(-264552120);
            this.stroke.setColor(-1);
            this.stroke.setStrokeWidth(2.0f * f);
            this.stroke.setStrokeCap(Paint.Cap.ROUND);
            this.dot.setColor(-1);
        }

        @Override // android.graphics.drawable.Drawable
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.isEmpty()) {
                return;
            }
            float fExactCenterX = bounds.exactCenterX();
            float fExactCenterY = bounds.exactCenterY();
            float fWidth = bounds.width() / 2.0f;
            canvas.drawCircle(fExactCenterX, fExactCenterY, fWidth, this.fill);
            if (!this.dots) {
                float f = fWidth * 0.36f;
                canvas.drawLine(fExactCenterX - f, fExactCenterY - f, fExactCenterX + f, fExactCenterY + f, this.stroke);
                canvas.drawLine(fExactCenterX + f, fExactCenterY - f, fExactCenterX - f, fExactCenterY + f, this.stroke);
            } else {
                for (int i = -1; i <= 1; i++) {
                    canvas.drawCircle((i * fWidth * 0.42f) + fExactCenterX, fExactCenterY, Math.max(1.5f, 0.12f * fWidth), this.dot);
                }
            }
        }

        @Override // android.graphics.drawable.Drawable
        public int getOpacity() {
            return -3;
        }

        @Override // android.graphics.drawable.Drawable
        public void setAlpha(int i) {
        }

        @Override // android.graphics.drawable.Drawable
        public void setColorFilter(ColorFilter colorFilter) {
        }
    }

    private final class RailEditTouch implements View.OnTouchListener {
        private boolean armed;
        private final RailBadge badge;
        private final View card;
        private final float density;
        private int downScrollX;
        private float downX;
        private boolean dragging;
        private final String id;
        private float lastRawX;
        private final RailBadge more;
        private final int slop;
        private float slotOffset;
        private final Runnable arm = () -> this.armDrag();
        private final Runnable autoScroll = new Runnable() {
            @Override // java.lang.Runnable
            public void run() {
                HorizontalScrollView horizontalScrollViewRailScroll;
                if (RailEditTouch.this.dragging && (horizontalScrollViewRailScroll = RailEditTouch.this.railScroll()) != null) {
                    int[] iArr = new int[2];
                    horizontalScrollViewRailScroll.getLocationOnScreen(iArr);
                    float f = RailEditTouch.this.density * 90.0f;
                    float f2 = RailEditTouch.this.lastRawX - iArr[0];
                    float width = (iArr[0] + horizontalScrollViewRailScroll.getWidth()) - RailEditTouch.this.lastRawX;
                    int iRound = 0;
                    if (f2 < f) {
                        iRound = -Math.round(Math.min(1.0f, (f - f2) / f) * 22.0f * RailEditTouch.this.density);
                    } else if (width < f) {
                        iRound = Math.round(Math.min(1.0f, (f - width) / f) * 22.0f * RailEditTouch.this.density);
                    }
                    if (iRound != 0) {
                        horizontalScrollViewRailScroll.scrollBy(iRound, 0);
                        RailEditTouch.this.follow();
                    }
                    RailEditTouch.this.card.postOnAnimation(this);
                }
            }
        };
        private int shownTo = -1;

        RailEditTouch(String id, View card, RailBadge railBadge, RailBadge railBadge2, float density) {
            this.id = id;
            this.card = card;
            this.badge = railBadge;
            this.more = railBadge2;
            this.density = density;
            this.slop = ViewConfiguration.get(MainActivity.this).getScaledTouchSlop();
        }

        private void armDrag() {
            this.armed = true;
            this.dragging = true;
            if (this.card.getParent() != null) {
                this.card.getParent().requestDisallowInterceptTouchEvent(true);
            }
            this.card.performHapticFeedback(0);
            Animator animator = (Animator) MainActivity.this.railJiggles.get(this.card);
            if (animator != null) {
                animator.pause();
            }
            this.card.setRotation(0.0f);
            this.card.setAlpha(0.92f);
            this.card.setScaleX(1.04f);
            this.card.setScaleY(1.04f);
            this.card.setTranslationZ(this.density * 8.0f);
            this.card.postOnAnimation(this.autoScroll);
        }

        private void follow() {
            this.card.setTranslationX((this.lastRawX - this.downX) + (scrollX() - this.downScrollX));
            shiftNeighbours(MainActivity.this.railDropIndex(this.card));
        }

        private int marginRight(View view) {
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
                return ((ViewGroup.MarginLayoutParams) layoutParams).rightMargin;
            }
            return 0;
        }

        private HorizontalScrollView railScroll() {
            if (MainActivity.this.cardsScrollView instanceof HorizontalScrollView) {
                return (HorizontalScrollView) MainActivity.this.cardsScrollView;
            }
            return null;
        }

        private int scrollX() {
            HorizontalScrollView horizontalScrollViewRailScroll = railScroll();
            if (horizontalScrollViewRailScroll == null) {
                return 0;
            }
            return horizontalScrollViewRailScroll.getScrollX();
        }

        private void settle() {
            settle(false);
        }

        private void settle(boolean z) {
            this.dragging = false;
            this.armed = false;
            this.card.removeCallbacks(this.autoScroll);
            if (!z) {
                for (View view : MainActivity.this.railCardViews()) {
                    if (view != this.card) {
                        view.animate().translationX(0.0f).setDuration(160L).start();
                    }
                }
            }
            float f = z ? this.slotOffset : 0.0f;
            this.shownTo = -1;
            this.card.animate().translationX(f).translationZ(0.0f).alpha(1.0f).scaleX(1.0f).scaleY(1.0f).setDuration(120L).start();
            Animator animator = (Animator) MainActivity.this.railJiggles.get(this.card);
            if (animator != null) {
                animator.resume();
            }
        }

        private void shiftNeighbours(int shownTo) {
            if (shownTo == this.shownTo) {
                return;
            }
            this.shownTo = shownTo;
            List listRailCardViews = MainActivity.this.railCardViews();
            int iIndexOf = listRailCardViews.indexOf(this.card);
            float width = this.card.getWidth() + marginRight(this.card);
            this.slotOffset = 0.0f;
            for (int i2 = 0; i2 < listRailCardViews.size(); i2++) {
                View view = (View) listRailCardViews.get(i2);
                if (view != this.card) {
                    float f = 0.0f;
                    if (iIndexOf < shownTo && i2 > iIndexOf && i2 <= shownTo) {
                        f = -width;
                        this.slotOffset += view.getWidth() + marginRight(view);
                    } else if (shownTo < iIndexOf && i2 >= shownTo && i2 < iIndexOf) {
                        f = width;
                        this.slotOffset -= view.getWidth() + marginRight(view);
                    }
                    view.animate().translationX(f).setDuration(160L).setInterpolator(new DecelerateInterpolator()).start();
                }
            }
        }

        @Override // android.view.View.OnTouchListener
        public boolean onTouch(View view, MotionEvent motionEvent) {
            boolean z = false;
            switch (motionEvent.getActionMasked()) {
                case 0:
                    float rawX = motionEvent.getRawX();
                    this.lastRawX = rawX;
                    this.downX = rawX;
                    this.downScrollX = scrollX();
                    this.dragging = false;
                    this.armed = false;
                    this.card.postDelayed(this.arm, ViewConfiguration.getLongPressTimeout());
                    return true;
                case 1:
                    this.card.removeCallbacks(this.arm);
                    if (this.dragging) {
                        int iRailDropIndex = MainActivity.this.railDropIndex(this.card);
                        int iIndexOf = MainActivity.this.railCardViews().indexOf(this.card);
                        shiftNeighbours(iRailDropIndex);
                        if (iRailDropIndex >= 0 && iRailDropIndex != iIndexOf) {
                            z = true;
                        }
                        settle(z);
                        if (iRailDropIndex >= 0 && iRailDropIndex != iIndexOf) {
                            MainActivity.this.callViewerDock("railMoveCard:" + this.id + ":" + iRailDropIndex);
                        }
                    } else {
                        int iRound = Math.round(motionEvent.getX());
                        int iRound2 = Math.round(motionEvent.getY());
                        int i = -Math.round(this.density * 10.0f);
                        Rect rect = new Rect(this.badge.getBounds());
                        rect.inset(i, i);
                        if (rect.contains(iRound, iRound2)) {
                            MainActivity.this.callViewerDock("railRemoveCard:" + this.id);
                        } else if (this.more != null) {
                            Rect rect2 = new Rect(this.more.getBounds());
                            rect2.inset(i, i);
                            BottomCardDescriptor bottomCardDescriptorBottomCardById = MainActivity.this.bottomCardById(this.id);
                            if (rect2.contains(iRound, iRound2) && bottomCardDescriptorBottomCardById != null) {
                                MainActivity.this.showQuickMenuRows(this.card, bottomCardDescriptorBottomCardById.editMenu, false);
                            }
                        }
                    }
                    return true;
                case 2:
                    this.lastRawX = motionEvent.getRawX();
                    if (this.armed) {
                        follow();
                        return true;
                    }
                    if (Math.abs(this.lastRawX - this.downX) > this.slop) {
                        this.card.removeCallbacks(this.arm);
                    }
                    return true;
                case 3:
                    this.card.removeCallbacks(this.arm);
                    if (this.dragging) {
                        settle();
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    private static final class SourceChip {
        final ImageView icon;
        final LinearLayout row;

        SourceChip(LinearLayout row, ImageView icon) {
            this.row = row;
            this.icon = icon;
        }
    }

    public class TelemetryBridge {
        public TelemetryBridge() {
        }

        @JavascriptInterface
        public String getCarData(String str) {
            String str2 = (String) MainActivity.this.telemetryCache.get(str);
            return str2 != null ? str2 : "";
        }

        @JavascriptInterface
        public void invokeVehicleCommand(String str, String str2) {
            String str3;
            if (str == null || !(MainActivity.VEHICLE_COMMANDS_WITHOUT_VALUE.contains(str) || MainActivity.VEHICLE_LEVEL_COMMANDS.contains(str))) {
                Log.w("H6Viewer", "Blocked unsupported vehicle command: " + str);
                return;
            }
            if (!MainActivity.VEHICLE_COMMANDS_WITHOUT_VALUE.contains(str)) {
                String strNormalizeVehicleLevel = MainActivity.normalizeVehicleLevel(str2);
                if (strNormalizeVehicleLevel == null) {
                    Log.w("H6Viewer", "Blocked vehicle command value for " + str + ": expected a base-10 integer from 0 through 100");
                    return;
                }
                str3 = strNormalizeVehicleLevel;
            } else {
                if (str2 != null && !str2.isEmpty()) {
                    Log.w("H6Viewer", "Blocked vehicle command value for " + str + ": command requires an empty value");
                    return;
                }
                str3 = "";
            }
            MainActivity.this.mainHandler.post(() -> {
                try {
                    Intent intent = new Intent("br.com.opengw.ACTION_VEHICLE_COMMAND");
                    intent.setPackage("br.com.opengw");
                    intent.putExtra("caller", MainActivity.this.apiCallerToken());
                    intent.putExtra("command", str);
                    if (!str3.isEmpty()) {
                        intent.putExtra("value", str3);
                    }
                    MainActivity.this.sendBroadcast(intent);
                } catch (Exception e) {
                    Log.w("H6Viewer", "invokeVehicleCommand failed for " + str + " (" + e.getClass().getSimpleName() + ")");
                }
            });
        }

        @JavascriptInterface
        public void reportPerf(String str) {
            if (MainActivity.this.isDebuggableBuild()) {
                Log.i("H6Perf", str);
            }
        }

        @JavascriptInterface
        public void setCarData(String str, String str2) {
            if (str == null || str.isEmpty() || !MainActivity.WRITABLE_CAR_KEYS.contains(str)) {
                Log.w("H6Viewer", "Blocked setCarData for " + str);
                return;
            }
            String str3 = str2 == null ? "" : str2;
            if (str3.length() > 64) {
                Log.w("H6Viewer", "Blocked oversized setCarData for " + str);
            } else {
                MainActivity.this.mainHandler.post(() -> {
                    try {
                        Intent intent = new Intent("br.com.opengw.ACTION_UPDATE_CAR_DATA");
                        intent.setPackage("br.com.opengw");
                        intent.putExtra("caller", MainActivity.this.apiCallerToken());
                        intent.putExtra("key", str);
                        intent.putExtra("value", str3);
                        MainActivity.this.sendBroadcast(intent);
                    } catch (Exception e) {
                        Log.w("H6Viewer", "setCarData broadcast failed for " + str + " (" + e.getClass().getSimpleName() + ")");
                    }
                });
            }
        }
    }

    private float adaptiveIconFillScale(Drawable drawable) {
        return Math.max(1.0f, Math.min(0.82f / Math.max(measureForegroundContentFraction(drawable), 0.35f), 1.6f));
    }

    private void addDockColorSwatches(LinearLayout linearLayout, int i, int i2, float f) {
        String[] strArr = DockAppOverrides.COLORS;
        for (int i3 = 0; i3 < strArr.length; i3++) {
            String str = strArr[i3];
            FrameLayout frameLayout = new FrameLayout(this);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(i, i);
            if (i3 < strArr.length - 1) {
                layoutParams.rightMargin = i2;
            }
            frameLayout.setLayoutParams(layoutParams);
            frameLayout.setTag(str);
            GradientDrawable gradientDrawable = new GradientDrawable();
            gradientDrawable.setShape(1);
            gradientDrawable.setColor(DockAppOverrides.parseColor(str, -1));
            gradientDrawable.setStroke(Math.round(f), 956301311);
            frameLayout.setBackground(gradientDrawable);
            linearLayout.addView(frameLayout);
        }
    }

    private void addDockIconGridCell(LinearLayout linearLayout, LinearLayout[] linearLayoutArr, int[] iArr, List<View> list, int i, int i2, int i3, View view) {
        if (linearLayoutArr[0] == null || iArr[0] >= i) {
            linearLayoutArr[0] = new LinearLayout(this);
            linearLayoutArr[0].setOrientation(0);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
            layoutParams.bottomMargin = i3;
            linearLayoutArr[0].setLayoutParams(layoutParams);
            linearLayout.addView(linearLayoutArr[0]);
            iArr[0] = 0;
        }
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(i2, i2);
        layoutParams2.rightMargin = i3;
        view.setLayoutParams(layoutParams2);
        linearLayoutArr[0].addView(view);
        iArr[0] = iArr[0] + 1;
        list.add(view);
    }

    private void alignQuickCardsToWidgetBoard() {
        if (this.quickCardsRow == null || this.stripContainer == null || this.contentHost == null) {
            return;
        }
        float f = getResources().getDisplayMetrics().density;
        ViewGroup.LayoutParams layoutParams = this.contentHost.getLayoutParams();
        int i = 0;
        if (layoutParams instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams layoutParams2 = (LinearLayout.LayoutParams) layoutParams;
            int iRound = "cards".equals(this.dockSurfaceMode) ? 0 : Math.round(12.0f * f);
            if (layoutParams2.leftMargin != iRound) {
                layoutParams2.leftMargin = iRound;
                this.contentHost.setLayoutParams(layoutParams2);
            }
            i = iRound;
        }
        int iMax = Math.max(0, (Math.max(0, leftFreeformBounds().left - pageOriginRect().left) - this.stripContainer.getPaddingLeft()) - i);
        if (this.quickCardsRow.getPaddingLeft() != iMax) {
            this.quickCardsRow.setPadding(iMax, this.quickCardsRow.getPaddingTop(), this.quickCardsRow.getPaddingRight(), this.quickCardsRow.getPaddingBottom());
        }
    }

    private static void allowHiddenApis() {
        try {
            Method declaredMethod = Class.class.getDeclaredMethod("forName", String.class);
            Method declaredMethod2 = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
            Class cls = (Class) declaredMethod.invoke(null, "dalvik.system.VMRuntime");
            ((Method) declaredMethod2.invoke(cls, "setHiddenApiExemptions", new Class[]{String[].class})).invoke(((Method) declaredMethod2.invoke(cls, "getRuntime", null)).invoke(null, new Object[0]), (Object) new String[]{"L"});
        } catch (Throwable th) {
        }
    }

    private PendingIntent apiCallerToken() {
        if (this.apiCallerToken == null) {
            this.apiCallerToken = PendingIntent.getBroadcast(this, 0, new Intent("com.havalh6.viewer.API_IDENTITY").setPackage(getPackageName()), 134217728);
        }
        return this.apiCallerToken;
    }

    private static void applyAnchorRect(View view, Rect rect) {
        FrameLayout.LayoutParams layoutParams;
        if (view == null || rect == null || (layoutParams = (FrameLayout.LayoutParams) view.getLayoutParams()) == null) {
            return;
        }
        layoutParams.width = rect.width();
        layoutParams.height = rect.height();
        layoutParams.leftMargin = rect.left;
        layoutParams.topMargin = rect.top;
        view.setLayoutParams(layoutParams);
    }

    private void applyAppsFabPosition() {
        if (!this.appsFabOverlayAttached || this.appsFabRoot == null) {
            return;
        }
        try {
            if (this.appsFabUsesSystemOverlay && this.appsFabLp != null) {
                this.appsFabLp.x = this.appsFabPosX;
                this.appsFabLp.y = this.appsFabPosY;
                getWindowManager().updateViewLayout(this.appsFabRoot, this.appsFabLp);
            } else if (this.appsFabRoot.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) this.appsFabRoot.getLayoutParams();
                layoutParams.leftMargin = this.appsFabPosX;
                layoutParams.topMargin = this.appsFabPosY;
                this.appsFabRoot.setLayoutParams(layoutParams);
            }
        } catch (Exception e) {
        }
    }

    private void applyBottomCardsConfiguration(JSONObject jSONObject) {
        ArrayList arrayList;
        String strSanitizeDrivingState;
        String[] strArrSanitizeWheelStates;
        String[] strArrSanitizeOpeningStates;
        int i;
        int iMax;
        if (jSONObject == null || !jSONObject.has("bottomCards")) {
            if (this.bottomCardsConfigured) {
                this.bottomCardsConfigured = false;
                this.bottomCardLimit = 0;
                this.bottomCards.clear();
                rebuildQuickCardsRow();
                return;
            }
            return;
        }
        JSONArray jSONArrayOptJSONArray = jSONObject.optJSONArray("bottomCards");
        if (jSONArrayOptJSONArray == null) {
            if (this.bottomCardsConfigured || !this.bottomCards.isEmpty()) {
                this.bottomCardsConfigured = false;
                this.bottomCardLimit = 0;
                this.bottomCards.clear();
                rebuildQuickCardsRow();
                return;
            }
            return;
        }
        ArrayList arrayList2 = new ArrayList();
        int iMin = Math.min(jSONArrayOptJSONArray.length(), 16);
        int i2 = 0;
        while (i2 < iMin) {
            JSONObject jSONObjectOptJSONObject = jSONArrayOptJSONArray.optJSONObject(i2);
            if (jSONObjectOptJSONObject == null) {
                arrayList = arrayList2;
            } else {
                String strCleanBottomCardText = cleanBottomCardText(jSONObjectOptJSONObject.optString("id", ""), 32);
                String strCleanBottomCardText2 = cleanBottomCardText(jSONObjectOptJSONObject.optString("title", ""), 24);
                String strCleanBottomCardText3 = cleanBottomCardText(jSONObjectOptJSONObject.optString("value", ""), 48);
                String strTrim = jSONObjectOptJSONObject.optString("action", "").trim();
                String strCleanBottomCardText4 = cleanBottomCardText(jSONObjectOptJSONObject.optString("primary", ""), 32);
                String strCleanBottomCardText5 = cleanBottomCardText(jSONObjectOptJSONObject.optString("secondary", ""), 48);
                String strCleanBottomCardText6 = cleanBottomCardText(jSONObjectOptJSONObject.optString("metricA", ""), 32);
                String strCleanBottomCardText7 = cleanBottomCardText(jSONObjectOptJSONObject.optString("metricB", ""), 32);
                int iMax2 = Math.max(0, Math.min(100, jSONObjectOptJSONObject.optInt("progress", 0)));
                boolean zOptBoolean = jSONObjectOptJSONObject.optBoolean("demo", false);
                if (strCleanBottomCardText.isEmpty() || strCleanBottomCardText2.isEmpty()) {
                    arrayList = arrayList2;
                } else if (BOTTOM_CARD_ACTIONS.contains(strTrim)) {
                    String strTrim2 = jSONObjectOptJSONObject.optString("iconAction", "").trim();
                    if (!BOTTOM_CARD_ACTIONS.contains(strTrim2)) {
                        strTrim2 = "";
                    }
                    String str = strTrim2;
                    String strTrim3 = jSONObjectOptJSONObject.optString("longAction", "").trim();
                    if (!BOTTOM_CARD_ACTIONS.contains(strTrim3)) {
                        strTrim3 = "";
                    }
                    String str2 = strTrim3;
                    String strSanitizeGlyphPath = sanitizeGlyphPath(jSONObjectOptJSONObject.optString("glyph", ""));
                    String strCleanBottomCardText8 = cleanBottomCardText(jSONObjectOptJSONObject.optString("glyphText", ""), 6);
                                        String strSanitizeClockOption = sanitizeClockOption(jSONObjectOptJSONObject.optString("clockFace", "panorama"), new String[]{"panorama", "meridian", "split", "date-spine"}, "panorama");
                    String strSanitizeClockOption2 = sanitizeClockOption(jSONObjectOptJSONObject.optString("clockHourFormat", "system"), new String[]{"system", "24", "12"}, "system");
                    String strSanitizeClockOption3 = sanitizeClockOption(jSONObjectOptJSONObject.optString("dialMarks", "index"), new String[]{"index", "plain"}, "index");
                    String strSanitizeClockOption4 = sanitizeClockOption(jSONObjectOptJSONObject.optString("splitPlates", "frost"), new String[]{"frost", "flat"}, "frost");
                    String strSanitizeClockOption5 = sanitizeClockOption(jSONObjectOptJSONObject.optString("dateSpineFormat", "month-name"), new String[]{"month-name", "numeric"}, "month-name");
                    String strSanitizeClockOption6 = sanitizeClockOption(jSONObjectOptJSONObject.optString("dateWording", "short"), new String[]{"short", "long"}, "short");
                    if ("power".equals(strCleanBottomCardText)) {
                        strCleanBottomCardText8 = sanitizePowerDirections(jSONObjectOptJSONObject.optString("glyphText", ""));
                    }
                    String str3 = strCleanBottomCardText8;
                    List<QuickMenuRow> quickMenu = parseQuickMenu(jSONObjectOptJSONObject.optJSONArray("menu"));
                    if ("tires".equals(strCleanBottomCardText)) {
                        strSanitizeDrivingState = sanitizeTiresState(jSONObjectOptJSONObject.optString("state", jSONObjectOptJSONObject.optString("tireState", "unavailable")));
                    } else if ("status".equals(strCleanBottomCardText)) {
                        strSanitizeDrivingState = sanitizeStatusState(jSONObjectOptJSONObject.optString("state", "unavailable"));
                    } else if ("power".equals(strCleanBottomCardText)) {
                        strSanitizeDrivingState = sanitizePowerState(jSONObjectOptJSONObject.optString("state", "unavailable"));
                    } else if ("navigation".equals(strCleanBottomCardText)) {
                        strSanitizeDrivingState = sanitizeNavigationState(jSONObjectOptJSONObject.optString("state", "unavailable"));
                    } else {
                        strSanitizeDrivingState = DRIVING_CARD_IDS.contains(strCleanBottomCardText) ? sanitizeDrivingState(jSONObjectOptJSONObject.optString("state", "unknown")) : "";
                    }
                    String str4 = strSanitizeDrivingState;
                    if ("tires".equals(strCleanBottomCardText) || "status".equals(strCleanBottomCardText)) {
                        strArrSanitizeWheelStates = sanitizeWheelStates(jSONObjectOptJSONObject.optString("wheelStates", jSONObjectOptJSONObject.optString("tireWheelStates", "")));
                    } else {
                        String[] strArr = new String[4];
                        strArr[0] = "unavailable";
                        strArr[1] = "unavailable";
                        strArr[2] = "unavailable";
                        strArr[3] = "unavailable";
                        strArrSanitizeWheelStates = strArr;
                    }
                    if ("status".equals(strCleanBottomCardText)) {
                        strArrSanitizeOpeningStates = sanitizeOpeningStates(jSONObjectOptJSONObject.optString("openingStates", ""));
                    } else {
                        String[] strArr2 = new String[5];
                        strArr2[0] = "unknown";
                        strArr2[1] = "unknown";
                        strArr2[2] = "unknown";
                        strArr2[3] = "unknown";
                        strArr2[4] = "unknown";
                        strArrSanitizeOpeningStates = strArr2;
                    }
                    String strSanitizePowerVariant = "power".equals(strCleanBottomCardText) ? sanitizePowerVariant(jSONObjectOptJSONObject.optString("powerVariant", "")) : "phev19";
                    boolean z2 = "power".equals(strCleanBottomCardText) && jSONObjectOptJSONObject.optBoolean("socKnown", false);
                    ArrayList arrayList3 = arrayList2;
                    double dOptDouble = jSONObjectOptJSONObject.optDouble("powerSoc", iMax2);
                    if (Double.isNaN(dOptDouble) || Double.isInfinite(dOptDouble) || dOptDouble < 0.0d || dOptDouble > 100.0d) {
                        z2 = false;
                        dOptDouble = 0.0d;
                    }
                    String[] strArrSanitizeSeatBeltStates = "status".equals(strCleanBottomCardText) ? sanitizeSeatBeltStates(jSONObjectOptJSONObject.optString("seatBeltStates", "")) : new String[]{"unknown", "unknown", "unknown", "unknown", "unknown"};
                    if ("status".equals(strCleanBottomCardText)) {
                        i = 0;
                        iMax = Math.max(0, Math.min(100, jSONObjectOptJSONObject.optInt("sunroofLevel", 0)));
                    } else {
                        i = 0;
                        iMax = 0;
                    }
                    BottomCardDescriptor bottomCardDescriptor = new BottomCardDescriptor(strCleanBottomCardText, strCleanBottomCardText2.toUpperCase(Locale.US), strCleanBottomCardText3, strTrim, strCleanBottomCardText4, strCleanBottomCardText5, strCleanBottomCardText6, strCleanBottomCardText7, iMax2, str4, strSanitizePowerVariant, z2, dOptDouble, strArrSanitizeWheelStates, strArrSanitizeOpeningStates, strArrSanitizeSeatBeltStates, iMax, "status".equals(strCleanBottomCardText) ? Math.max(i, Math.min(100, jSONObjectOptJSONObject.optInt("curtainLevel", i))) : 0, zOptBoolean, str, str2, strSanitizeGlyphPath, str3, strSanitizeClockOption, strSanitizeClockOption2, strSanitizeClockOption3, strSanitizeClockOption4, strSanitizeClockOption5, strSanitizeClockOption6, quickMenu);
                    bottomCardDescriptor.tirePressures = cleanBottomCardText(jSONObjectOptJSONObject.optString("tirePressures", ""), 48);
                    bottomCardDescriptor.tireTemperatures = cleanBottomCardText(jSONObjectOptJSONObject.optString("tireTemperatures", ""), 48);
                    bottomCardDescriptor.tirePressureUnit = cleanBottomCardText(jSONObjectOptJSONObject.optString("tirePressureUnit", ""), 6);
                    bottomCardDescriptor.appPackage = cleanBottomCardText(jSONObjectOptJSONObject.optString("appPackage", ""), 80);
                    bottomCardDescriptor.navRemaining = cleanBottomCardText(jSONObjectOptJSONObject.optString("navRemaining", ""), 16);
                    bottomCardDescriptor.navDuration = cleanBottomCardText(jSONObjectOptJSONObject.optString("navDuration", ""), 16);
                    bottomCardDescriptor.navEta = cleanBottomCardText(jSONObjectOptJSONObject.optString("navEta", ""), 8);
                    bottomCardDescriptor.navIdleCity = cleanBottomCardText(jSONObjectOptJSONObject.optString("navIdleCity", ""), 32);
                    bottomCardDescriptor.navIdleAction = cleanBottomCardText(jSONObjectOptJSONObject.optString("navIdleAction", ""), 48);
                    if ("consumption".equals(strCleanBottomCardText)) {
                        bottomCardDescriptor.energyBars = parseEnergyBars(jSONObjectOptJSONObject.optString("energyBars", ""));
                    }
                    bottomCardDescriptor.editMenu = parseEditMenu(jSONObjectOptJSONObject.optJSONArray("editMenu"));
                    arrayList = arrayList3;
                    arrayList.add(bottomCardDescriptor);
                } else {
                    arrayList = arrayList2;
                }
            }
            i2++;
            arrayList2 = arrayList;
        }
        ArrayList arrayList4 = arrayList2;
        int iMax3 = Math.max(0, Math.min(jSONObject.has("bottomCardLimit") ? jSONObject.optInt("bottomCardLimit", arrayList4.size()) : arrayList4.size(), arrayList4.size()));
        if (this.bottomCardsConfigured && this.bottomCardLimit == iMax3 && sameBottomCardStructure(this.bottomCards, arrayList4)) {
            updateBottomCardValues(arrayList4);
            this.bottomCards.clear();
            this.bottomCards.addAll(arrayList4);
        } else {
            this.bottomCardsConfigured = true;
            this.bottomCardLimit = iMax3;
            this.bottomCards.clear();
            this.bottomCards.addAll(arrayList4);
            rebuildQuickCardsRow();
        }
    }

    private void applyCenterFillIndicator(String str) {
        if (str == null || str.isEmpty()) {
            return;
        }
        try {
            JSONObject jSONObject = new JSONObject(str);
            if (jSONObject.has("mode")) {
                String strOptString = jSONObject.optString("mode", this.centerFillMode);
                this.centerFillMode = ("wallpaper".equals(strOptString) || "mixed".equals(strOptString)) ? strOptString : "car";
            }
            if (this.layoutCenterFillChip != null && jSONObject.has("visible")) {
                this.layoutCenterFillChip.setVisibility(jSONObject.optBoolean("visible", true) ? 0 : 8);
            }
            refreshCenterFillChip();
        } catch (Exception e) {
            Log.w("H6Viewer", "updateCenterFill parse failed", e);
        }
    }

    private void applyChromeOnTop(boolean chromeOnTop) {
        if (this.chromeOnTop == chromeOnTop) {
            return;
        }
        this.chromeOnTop = chromeOnTop;
        Log.w("H6Viewer", "Chrome on top: " + chromeOnTop);
        applyLauncherFocusPolicy();
        if (this.chromeOnTopTimeout != null) {
            this.mainHandler.removeCallbacks(this.chromeOnTopTimeout);
        }
        if (!chromeOnTop) {
            this.lastRaiseCompletedMs = 0L;
            this.lastRaisedTaskId = -1;
            raiseOverlayTasksSoon();
        } else {
            if (this.raiseOverlayRunnable != null) {
                this.mainHandler.removeCallbacks(this.raiseOverlayRunnable);
            }
            if (this.chromeOnTopTimeout == null) {
                this.chromeOnTopTimeout = () -> {
                    Log.w("H6Viewer", "Chrome on top timed out; restoring slot raise");
                    applyChromeOnTop(false);
                };
            }
            this.mainHandler.postDelayed(this.chromeOnTopTimeout, 20000L);
        }
    }

    /**
     * Esconde ou devolve a faixa nativa de icones, a pedido da pagina (AppLauncherBridge).
     *
     * A faixa e uma View desenhada FORA da WebView, entao nenhum z-index de dentro dela alcanca
     * a faixa: enquanto o launcher era um APK separado esse metodo nao existia no dex, e as duas
     * telas que precisam da central limpa -- CONFIGURACOES OPEN GW e o Modo Descanso -- tinham de
     * conviver com uma fileira de icones por cima. Com os dois apps fundidos o dex e nosso, e o
     * pedido passa a ser atendido de verdade.
     *
     * Nao ha temporizador de seguranca aqui de proposito: o Modo Descanso fica ligado por horas, e
     * um teto de tempo traria os icones de volta sozinho, que e exatamente o defeito. A rede de
     * seguranca e a recarga da pagina (ver onPageFinished), que zera o pedido -- quem ainda
     * precisar da faixa escondida torna a pedir.
     */
    private void applyLauncherHidden(boolean launcherHiddenByPage) {
        if (this.launcherHiddenByPage == launcherHiddenByPage) {
            return;
        }
        this.launcherHiddenByPage = launcherHiddenByPage;
        Log.w("H6Viewer", "Launcher strip hidden by page: " + launcherHiddenByPage);
        updateLauncherStripVisibility();
    }

    private void applyChromeSideInsets() {
        if (this.stripContainer == null) {
            return;
        }
        this.stripContainer.setPadding(pageSideInsetPx(false), this.stripContainer.getPaddingTop(), pageSideInsetPx(true), this.stripContainer.getPaddingBottom());
        alignQuickCardsToWidgetBoard();
    }

    private void applyDockIndicators(String str) {
        String strOptString;
        String strOptString2;
        String strOptString3;
        String strOptString4;
        String strOptString5;
        String strOptString6;
        String strOptString7;
        String strOptString8;
        if (str == null || str.isEmpty()) {
            return;
        }
        try {
            JSONObject jSONObject = new JSONObject(str);
            if (jSONObject.has("dockMode")) {
                String strOptString9 = jSONObject.optString("dockMode", this.dockSurfaceMode);
                if ("launcher".equals(strOptString9) || "cards".equals(strOptString9)) {
                    this.dockSurfaceMode = strOptString9;
                    persistDockSurfaceMode();
                }
            }
            if (jSONObject.has("activeDesktopName") && (strOptString8 = jSONObject.optString("activeDesktopName", this.activeDesktopName)) != null && !strOptString8.trim().isEmpty()) {
                this.activeDesktopName = strOptString8.trim();
            }
            boolean z = true;
            if (jSONObject.has("desktopCount")) {
                this.desktopCount = Math.max(1, jSONObject.optInt("desktopCount", 1));
            }
            if (jSONObject.has("desktopStudioOpen")) {
                this.desktopStudioOpen = jSONObject.optBoolean("desktopStudioOpen", false);
                updateLauncherStripVisibility();
            }
            if (jSONObject.has("activeDesktopIndex")) {
                this.activeDesktopIndex = Math.max(0, jSONObject.optInt("activeDesktopIndex", 0));
            }
            if (jSONObject.has("studioScreen")) {
                String strOptString10 = jSONObject.optString("studioScreen", "");
                if (!"desktops".equals(strOptString10) && !"layout".equals(strOptString10) && !"appearance".equals(strOptString10)) {
                    strOptString10 = "";
                }
                if (!strOptString10.equals(this.studioScreen)) {
                    this.studioScreen = strOptString10;
                    if (!strOptString10.isEmpty()) {
                        setWorkspaceLayoutMode(true);
                    }
                    styleWorkspaceLayoutCells();
                }
            }
            boolean z2 = false;
            if (jSONObject.has("accent") && (strOptString7 = jSONObject.optString("accent", "")) != null && strOptString7.trim().startsWith("#")) {
                int cssColor = parseCssColor(strOptString7.trim(), this.dockAccentColor);
                if (cssColor == this.dockAccentColor) {
                    z = false;
                }
                z2 = z;
                this.dockAccentColor = cssColor;
            }
            applyBottomCardsConfiguration(jSONObject);
            if (jSONObject.has("railEdit")) {
                setRailEditMode(jSONObject.optBoolean("railEdit", false));
            }
            if (jSONObject.has("railFocus")) {
                String strCleanBottomCardText = cleanBottomCardText(jSONObject.optString("railFocus", ""), 32);
                if (!strCleanBottomCardText.equals(this.railFocusId)) {
                    this.railFocusId = strCleanBottomCardText;
                    if (!strCleanBottomCardText.isEmpty()) {
                        scrollRailToCard(strCleanBottomCardText);
                    }
                }
            }
            if (z2) {
                rebuildQuickCardsRow();
            }
            applyQuickCardIndicators(jSONObject);
            if (jSONObject.has("model") && (strOptString6 = jSONObject.optString("model", this.dockModelLabel)) != null && !strOptString6.isEmpty()) {
                this.dockModelLabel = strOptString6;
            }
            if (jSONObject.has("perf") && (strOptString5 = jSONObject.optString("perf", this.dockPerfLabel)) != null && !strOptString5.isEmpty()) {
                this.dockPerfLabel = strOptString5;
            }
            if (jSONObject.has("paint") && (strOptString4 = jSONObject.optString("paint", this.dockPaintHex)) != null && !strOptString4.isEmpty()) {
                this.dockPaintHex = strOptString4;
            }
            if (jSONObject.has("time") && (strOptString3 = jSONObject.optString("time", this.dockTimeMode)) != null && !strOptString3.isEmpty()) {
                this.dockTimeMode = strOptString3;
            }
            if (jSONObject.has("fps")) {
                this.dockFpsOn = jSONObject.optBoolean("fps", false);
            }
            if (jSONObject.has("xray")) {
                this.dockXrayOn = jSONObject.optBoolean("xray", false);
            }
            if (jSONObject.has("widgetThemeMode") && (strOptString2 = jSONObject.optString("widgetThemeMode", this.dockWidgetThemeMode)) != null && !strOptString2.isEmpty()) {
                this.dockWidgetThemeMode = strOptString2;
            }
            if (jSONObject.has("widgetTheme") && (strOptString = jSONObject.optString("widgetTheme", this.dockWidgetThemeEffective)) != null && !strOptString.isEmpty()) {
                this.dockWidgetThemeEffective = strOptString;
            }
            if (jSONObject.has("uiTheme")) {
                applyDockUiTheme("light".equals(jSONObject.optString("uiTheme", "dark")));
            }
            refreshDockToolGlyph("model", this.dockModelLabel);
            refreshDockToolGlyph("perf", this.dockPerfLabel);
            refreshDockToolGlyph("paint", this.dockPaintHex);
            refreshDockToolGlyph("time", this.dockTimeMode);
            refreshDockToolGlyph("fps", this.dockFpsOn ? "ON" : "OFF");
            refreshDockToolGlyph("xray", this.dockXrayOn ? "ON" : "OFF");
            refreshLayoutThemeChip();
            refreshDockSurfaceUi(false);
            refreshAllDockPlates();
        } catch (Exception e) {
            Log.w("H6Viewer", "updateDockIndicators parse failed", e);
        }
    }

    private void applyDockPanelBackground() {
        clearDockStripBackground(this.stripRow);
        clearDockStripBackground(this.contentHost);
        clearDockStripBackground(this.appsScrollView);
        clearDockStripBackground(this.cardsScrollView);
        clearDockStripBackground(this.modeDrawer);
        clearDockStripBackground(this.layoutContentRow);
        clearDockStripBackground(this.configContentScroll);
    }

    private void applyDockUiTheme(boolean dockUiLight) {
        if (this.dockUiLight == dockUiLight) {
            return;
        }
        this.dockUiLight = dockUiLight;
        refreshDockChromeTheme();
    }

    private void applyDrawerCollapsedUi(boolean z) {
        this.drawerExpanded = false;
        boolean z2 = !"cards".equals(this.dockSurfaceMode);
        if (this.modeCollapsedBtn != null) {
            this.modeCollapsedBtn.setVisibility(z2 ? 0 : 8);
            if (z2) {
                setModeCellSelected(this.modeCollapsedBtn, false);
                setModeCellLabelVisible(this.modeCollapsedBtn, true);
            }
        }
        if (this.modeAppsBtn != null) {
            this.modeAppsBtn.setVisibility(8);
            setModeCellLabelVisible(this.modeAppsBtn, false);
        }
        if (this.modeLayoutBtn != null) {
            this.modeLayoutBtn.setVisibility(8);
            setModeCellLabelVisible(this.modeLayoutBtn, false);
        }
        if (this.modeConfigBtn != null) {
            this.modeConfigBtn.setVisibility(8);
            setModeCellLabelVisible(this.modeConfigBtn, false);
        }
        if (this.modeSurfaceBtn != null) {
            this.modeSurfaceBtn.setVisibility(8);
            setModeCellLabelVisible(this.modeSurfaceBtn, false);
        }
        if (this.modeDrawer == null || this.dockCellPx <= 0) {
            return;
        }
        this.modeDrawer.setVisibility(z2 ? 0 : 8);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) this.modeDrawer.getLayoutParams();
        if (layoutParams != null) {
            int i = z2 ? this.dockCellPx : 0;
            if (!z || layoutParams.width == i) {
                layoutParams.width = i;
                this.modeDrawer.setLayoutParams(layoutParams);
            } else {
                ValueAnimator valueAnimatorOfInt = ValueAnimator.ofInt(layoutParams.width, i);
                valueAnimatorOfInt.setDuration(180L);
                valueAnimatorOfInt.addUpdateListener(valueAnimator -> {
                    layoutParams.width = ((Integer) valueAnimator.getAnimatedValue()).intValue();
                    this.modeDrawer.setLayoutParams(layoutParams);
                });
                valueAnimatorOfInt.start();
            }
        }
    }

    private void applyDrawerExpandedUi() {
        LinearLayout.LayoutParams layoutParams;
        this.drawerExpanded = true;
        if (this.modeCollapsedBtn != null) {
            this.modeCollapsedBtn.setVisibility(8);
        }
        if (this.modeAppsBtn != null) {
            this.modeAppsBtn.setVisibility(0);
            setModeCellSelected(this.modeAppsBtn, "apps".equals(this.stripMode));
            setModeCellLabelVisible(this.modeAppsBtn, true);
        }
        if (this.modeLayoutBtn != null) {
            this.modeLayoutBtn.setVisibility(0);
            setModeCellSelected(this.modeLayoutBtn, "layout".equals(this.stripMode));
            setModeCellLabelVisible(this.modeLayoutBtn, true);
        }
        if (this.modeConfigBtn != null) {
            this.modeConfigBtn.setVisibility(0);
            setModeCellSelected(this.modeConfigBtn, "config".equals(this.stripMode));
            setModeCellLabelVisible(this.modeConfigBtn, true);
        }
        if (this.modeSurfaceBtn != null) {
            this.modeSurfaceBtn.setVisibility(0);
            setModeCellSelected(this.modeSurfaceBtn, "cards".equals(this.dockSurfaceMode));
            TextView textViewFindModeCellLabel = findModeCellLabel(this.modeSurfaceBtn);
            if (textViewFindModeCellLabel != null) {
                textViewFindModeCellLabel.setText("cards".equals(this.dockSurfaceMode) ? "Launcher" : "Cards");
            }
            this.modeSurfaceBtn.setContentDescription("cards".equals(this.dockSurfaceMode) ? "Show launcher. Long press to customize" : "Show quick cards. Long press to customize");
            setModeCellLabelVisible(this.modeSurfaceBtn, true);
        }
        if (this.modeDrawer == null || this.dockCellPx <= 0 || (layoutParams = (LinearLayout.LayoutParams) this.modeDrawer.getLayoutParams()) == null) {
            return;
        }
        ValueAnimator valueAnimatorOfInt = ValueAnimator.ofInt(Math.max(this.dockCellPx, layoutParams.width), this.dockCellPx * 4);
        valueAnimatorOfInt.setDuration(200L);
        valueAnimatorOfInt.addUpdateListener(valueAnimator -> {
            layoutParams.width = ((Integer) valueAnimator.getAnimatedValue()).intValue();
            this.modeDrawer.setLayoutParams(layoutParams);
        });
        valueAnimatorOfInt.start();
    }

    private void applyReserveBandGeometry() {
        FrameLayout.LayoutParams layoutParams;
        if (this.reserveBand == null || (layoutParams = (FrameLayout.LayoutParams) this.reserveBand.getLayoutParams()) == null) {
            return;
        }
        int iMax = Math.max(0, launcherBottomGapPx() - overscanBottomPx());
        if (layoutParams.height != iMax) {
            layoutParams.height = iMax;
            this.reserveBand.setLayoutParams(layoutParams);
        }
        this.reserveBand.setVisibility((!this.launcherRevealed || iMax <= 0) ? 4 : 0);
    }

    private void applyLaunchSide(String launchSidePref) {
        if ("left".equals(launchSidePref) || "right".equals(launchSidePref) || "auto".equals(launchSidePref)) {
            this.launchSidePref = launchSidePref;
        } else {
            this.launchSidePref = "auto";
        }
        if ("left".equals(this.launchSidePref)) {
            this.nextLaunchLeft = true;
        } else if ("right".equals(this.launchSidePref)) {
            this.nextLaunchLeft = false;
        }
        saveShellPrefs();
        notifyViewerShellLayout();
    }

    private void applyLauncherFocusPolicy() {
        WindowManager.LayoutParams attributes;
        if (getWindow() == null || (attributes = getWindow().getAttributes()) == null) {
            return;
        }
        int i = attributes.flags;
        int i2 = (!hasOverlayWindow() || this.chromeOnTop) ? i & (-9) : i | 8;
        int i3 = hasOverlayWindow() ? i2 | 768 : i2 & (-769);
        if (i3 == attributes.flags) {
            return;
        }
        attributes.flags = i3;
        getWindow().setAttributes(attributes);
        if (!hasOverlayWindow()) {
            enterImmersiveMode();
        }
        Log.w("H6Viewer", hasOverlayWindow() ? "Launcher NOT_FOCUSABLE while freeform overlay is open" : "Launcher focusable");
        if (hasOverlayWindow()) {
            startOverlayWatchdog();
        } else {
            stopOverlayWatchdog();
        }
    }

    private void applyLauncherStripBottomMargin() {
        FrameLayout.LayoutParams layoutParams;
        int iCompensatedBottomMarginPx;
        FrameLayout.LayoutParams layoutParams2;
        int iCompensatedBottomMarginPx2;
        int iOverscanBottomPx = overscanBottomPx();
        if (iOverscanBottomPx != this.loggedOverscanBottomPx) {
            this.loggedOverscanBottomPx = iOverscanBottomPx;
            Log.w("H6Viewer", "Overscan bottom=" + iOverscanBottomPx + "px (compensating chrome)");
        }
        if (this.stripContainer != null && (layoutParams2 = (FrameLayout.LayoutParams) this.stripContainer.getLayoutParams()) != null && layoutParams2.bottomMargin != (iCompensatedBottomMarginPx2 = compensatedBottomMarginPx(launcherBottomGapPx()))) {
            layoutParams2.bottomMargin = iCompensatedBottomMarginPx2;
            this.stripContainer.setLayoutParams(layoutParams2);
        }
        applyReserveBandGeometry();
        alignQuickCardsToWidgetBoard();
        if (this.splashSkipBtn == null || (layoutParams = (FrameLayout.LayoutParams) this.splashSkipBtn.getLayoutParams()) == null || layoutParams.bottomMargin == (iCompensatedBottomMarginPx = compensatedBottomMarginPx(chromeBottomGapPx()))) {
            return;
        }
        layoutParams.bottomMargin = iCompensatedBottomMarginPx;
        this.splashSkipBtn.setLayoutParams(layoutParams);
    }

    private void applyNavigationSourceChip(BottomCardDescriptor bottomCardDescriptor) {
        SourceChip sourceChip = this.quickCardSourceChips.get(bottomCardDescriptor.id);
        if (sourceChip == null) {
            return;
        }
        String strTrim = bottomCardDescriptor.appPackage == null ? "" : bottomCardDescriptor.appPackage.trim();
        if (strTrim.isEmpty()) {
            applySourceChip(sourceChip, null, null);
        } else {
            applySourceChip(sourceChip, resolveSourceChipIcon(strTrim, null), null);
        }
    }

    private void applyQuickCardIndicators(JSONObject jSONObject) {
        String str;
        if (jSONObject == null) {
            return;
        }
        String strCleanIndicator = cleanIndicator(jSONObject.optString("climateSummary", ""));
        JSONObject jSONObjectOptJSONObject = jSONObject.optJSONObject("climate");
        if (strCleanIndicator.isEmpty() && jSONObjectOptJSONObject != null) {
            String strCleanIndicator2 = cleanIndicator(jSONObjectOptJSONObject.optString("temperature", jSONObjectOptJSONObject.optString("temp", "")));
            String strCleanIndicator3 = cleanIndicator(jSONObjectOptJSONObject.optString("fan", ""));
            if (jSONObjectOptJSONObject.has("auto")) {
                str = jSONObjectOptJSONObject.optBoolean("auto", false) ? "AUTO ON" : "AUTO OFF";
            } else {
                str = "";
            }
            StringBuilder sb = new StringBuilder();
            if (!strCleanIndicator2.isEmpty()) {
                sb.append(strCleanIndicator2).append(strCleanIndicator2.contains("°") ? "" : " °C");
            }
            if (!strCleanIndicator3.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("  ·  ");
                }
                sb.append("Fan ").append(strCleanIndicator3);
            }
            if (!str.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("  ·  ");
                }
                sb.append(str);
            }
            strCleanIndicator = sb.toString();
        }
        if (this.quickClimateValue != null && (!strCleanIndicator.isEmpty() || jSONObject.has("climateSummary"))) {
            String str2 = strCleanIndicator.isEmpty() ? "— °C  ·  Fan —  ·  AUTO —" : strCleanIndicator;
            this.quickClimateValue.setText(str2);
            View view = this.quickClimateValue.getParent() instanceof View ? (View) this.quickClimateValue.getParent() : null;
            if (view != null) {
                view.setContentDescription("Climate summary: " + str2);
            }
        }
        String strCleanIndicator4 = cleanIndicator(jSONObject.optString("consumptionSummary", ""));
        Object objOpt = jSONObject.opt("consumption");
        if (strCleanIndicator4.isEmpty() && (objOpt instanceof JSONObject)) {
            JSONObject jSONObject2 = (JSONObject) objOpt;
            String strCleanIndicator5 = cleanIndicator(jSONObject2.optString("value", ""));
            String strCleanIndicator6 = cleanIndicator(jSONObject2.optString("unit", ""));
            if (!strCleanIndicator5.isEmpty()) {
                strCleanIndicator4 = strCleanIndicator5 + (strCleanIndicator6.isEmpty() ? "" : " " + strCleanIndicator6);
            }
        } else if (strCleanIndicator4.isEmpty() && objOpt != null && objOpt != JSONObject.NULL) {
            strCleanIndicator4 = cleanIndicator(String.valueOf(objOpt));
        }
        if (this.quickConsumptionValue != null) {
            if (!strCleanIndicator4.isEmpty() || jSONObject.has("consumptionSummary")) {
                String str3 = strCleanIndicator4.isEmpty() ? "—" : strCleanIndicator4;
                this.quickConsumptionValue.setText(str3);
                View view2 = this.quickConsumptionValue.getParent() instanceof View ? (View) this.quickConsumptionValue.getParent() : null;
                if (view2 != null) {
                    view2.setContentDescription("Consumption summary: " + str3);
                }
            }
        }
    }

    private void applyQuickClockFace(String quickClockFaceRevision, int quickClockFaceSequence, Bitmap quickClockFaceBitmap) {
        if (quickClockFaceBitmap == null) {
            return;
        }
        if (!quickClockFaceRevision.equals(this.quickClockFaceRevision) || quickClockFaceSequence >= this.quickClockFaceSequence) {
            this.quickClockFaceRevision = quickClockFaceRevision;
            this.quickClockFaceSequence = quickClockFaceSequence;
            this.quickClockFaceBitmap = quickClockFaceBitmap;
            for (QuickClockCardView quickClockCardView : this.quickClockCards.values()) {
                if (quickClockCardView != null) {
                    quickClockCardView.setRenderedFace(quickClockFaceBitmap);
                }
            }
        }
    }

    private void applyQuickMediaAppChip(String str, String str2) {
        if (this.quickMediaAppRow == null) {
            return;
        }
        Drawable drawableResolveSourceChipIcon = resolveSourceChipIcon(this.quickMediaPackage != null ? this.quickMediaPackage : "", str2);
        applySourceChip(this.quickMediaChip, drawableResolveSourceChipIcon, drawableResolveSourceChipIcon == null ? decodeDataUrlBitmap(str) : null);
    }

    private void applyRailEdit(boolean z) {
        MainActivity mainActivity = this;
        Iterator<Animator> it = mainActivity.railJiggles.values().iterator();
        while (it.hasNext()) {
            it.next().cancel();
        }
        mainActivity.railJiggles.clear();
        float f = mainActivity.getResources().getDisplayMetrics().density;
        List<View> listRailCardViews = mainActivity.railCardViews();
        int i = 0;
        while (i < listRailCardViews.size()) {
            View view = listRailCardViews.get(i);
            view.getOverlay().clear();
            view.setRotation(0.0f);
            view.setTranslationX(0.0f);
            view.setAlpha(1.0f);
            RailBadge railBadge = null;
            if (z) {
                String strSubstring = ((String) view.getTag()).substring("bottomCard:".length());
                setSubtreeClickable(view, false);
                RailBadge railBadge2 = new RailBadge(f, false);
                BottomCardDescriptor bottomCardDescriptorBottomCardById = mainActivity.bottomCardById(strSubstring);
                if (bottomCardDescriptorBottomCardById != null && !bottomCardDescriptorBottomCardById.editMenu.isEmpty()) {
                    railBadge = new RailBadge(f, true);
                }
                RailBadge railBadge3 = railBadge;
                int iRound = Math.round(26.0f * f);
                int iRound2 = Math.round(30.0f * f);
                int iRound3 = Math.round(4.0f * f);
                view.getOverlay().add(railBadge2);
                if (railBadge3 != null) {
                    view.getOverlay().add(railBadge3);
                }
                view.post(() -> {
                    int width = view.getWidth();
                    int height = view.getHeight();
                    railBadge2.setBounds((width - iRound) - iRound3, iRound3, width - iRound3, iRound3 + iRound);
                    if (railBadge3 != null) {
                        railBadge3.setBounds((width - iRound2) - iRound3, (height - iRound2) - iRound3, width - iRound3, height - iRound3);
                    }
                    view.invalidate();
                });
                ObjectAnimator objectAnimatorOfFloat = ObjectAnimator.ofFloat(view, (Property<View, Float>) View.ROTATION, -1.1f, 1.1f);
                objectAnimatorOfFloat.setDuration(i % 2 == 0 ? 130L : 150L);
                objectAnimatorOfFloat.setRepeatMode(2);
                objectAnimatorOfFloat.setRepeatCount(-1);
                objectAnimatorOfFloat.setStartDelay((((long) i) * 37) % 90);
                objectAnimatorOfFloat.start();
                mainActivity.railJiggles.put(view, objectAnimatorOfFloat);
                view.setOnTouchListener(mainActivity.new RailEditTouch(strSubstring, view, railBadge2, railBadge3, f));
            } else {
                view.setOnTouchListener(null);
            }
            i++;
            mainActivity = this;
        }
    }

    private void applyShellMode(String shellMode) {
        if (shellMode == null) {
            return;
        }
        if (!"appsOnly".equals(shellMode)) {
            shellMode = "appCar";
        }
        String str2 = this.shellMode;
        this.lastNonAppsShellMode = "appCar";
        this.shellMode = shellMode;
        saveShellPrefs();
        loadSlotUsesForMode();
        updateSlotAnchors();
        boolean z = (this.activePopupPackage == null || this.activePopupPackage.isEmpty()) ? false : true;
        boolean z2 = (this.activeMediaPackage == null || this.activeMediaPackage.isEmpty()) ? false : true;
        if ("appsOnly".equals(str2) && !"appsOnly".equals(this.shellMode)) {
            minimizeAppsOnlySlots();
            z = false;
            z2 = false;
        }
        if ("appCar".equals(this.shellMode) && z2) {
            dismissMediaPopup(this.activeMediaPackage);
            z2 = false;
        }
        if (z && this.activePopupComponent != null) {
            schedulePinPopupBounds(this.activePopupPackage, this.activePopupComponent, leftFreeformBounds(), false);
        }
        if (z2 && this.activeMediaComponent != null) {
            schedulePinPopupBounds(this.activeMediaPackage, this.activeMediaComponent, rightFreeformBounds(), true);
        }
        if (!str2.equals(this.shellMode)) {
            Log.i("H6Viewer", "Shell mode " + str2 + " → " + this.shellMode + " leftOpen=" + z + " rightOpen=" + z2);
        }
        refreshLayoutChipSelection();
        updateAppsOnlyChrome();
        notifyViewerShellLayout();
    }

    private void applySlotUse(String str, String str2) {
        if (!"widgets".equals(str2)) {
            str2 = "app";
        }
        if (str2.equals("right".equals(str) ? this.rightSlotUse : this.leftSlotUse)) {
            return;
        }
        if ("right".equals(str)) {
            this.rightSlotUse = str2;
        } else {
            this.leftSlotUse = str2;
        }
        getSharedPreferences("opengw_lancador", 0).edit().putString("slot_" + this.shellMode + "_" + ("right".equals(str) ? "right" : "left"), str2).apply();
        if ("widgets".equals(str2)) {
            if ("right".equals(str) && this.activeMediaPackage != null && !this.activeMediaPackage.isEmpty()) {
                dismissMediaPopup(this.activeMediaPackage);
            } else if ("left".equals(str) && this.activePopupPackage != null && !this.activePopupPackage.isEmpty()) {
                dismissPopup(this.activePopupPackage);
            }
        }
        notifyViewerShellLayout();
    }

    private void applySourceChip(SourceChip sourceChip, Drawable drawable, Bitmap bitmap) {
        if (sourceChip == null) {
            return;
        }
        boolean z = (drawable == null && bitmap == null) ? false : true;
        sourceChip.row.setVisibility(z ? 0 : 8);
        if (z) {
            if (drawable != null) {
                sourceChip.icon.setImageDrawable(drawable);
            } else {
                sourceChip.icon.setImageBitmap(bitmap);
            }
        }
    }

    private void applySplitRatio(String splitRatio) {
        if (isValidSplitRatio(splitRatio) && !splitRatio.equals(this.splitRatio)) {
            this.splitRatio = splitRatio;
            saveShellPrefs();
            updateSlotAnchors();
            boolean z = (this.activePopupPackage == null || this.activePopupPackage.isEmpty()) ? false : true;
            boolean z2 = (this.activeMediaPackage == null || this.activeMediaPackage.isEmpty()) ? false : true;
            if (z) {
                pinSlotToBounds(this.activePopupPackage, this.activePopupComponent, this.activePopupTaskId, leftFreeformBounds(), false);
            }
            if (z2) {
                pinSlotToBounds(this.activeMediaPackage, this.activeMediaComponent, this.activeMediaTaskId, rightFreeformBounds(), true);
            }
            notifyViewerShellLayout();
        }
    }

    private void applyWorkspaceActionGlyph(View view) {
        if (view == null) {
            return;
        }
        int iRound = Math.round(32.0f * getResources().getDisplayMetrics().density);
        int iDockLabelColor = dockLabelColor();
        ImageView imageView = (ImageView) view.findViewWithTag("workspaceGlyph");
        if (imageView != null) {
            imageView.setImageDrawable("workspaceApps".equals(view.getTag()) ? dockGlyphAppTiles(iRound, iDockLabelColor) : dockGlyphMosaic(iRound, iDockLabelColor));
        }
        TextView textView = (TextView) view.findViewWithTag("workspaceLabel");
        if (textView != null) {
            textView.setTextColor(iDockLabelColor);
        }
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setShape(0);
        gradientDrawable.setColor(-1);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(iDockLabelColor, 51)), null, gradientDrawable));
    }

    private JSONObject appsCatalogEntry(PackageManager packageManager, ResolveInfo resolveInfo, String str) throws JSONException {
        return appsCatalogObject(str, resolveDockLabel(str, isPinnedPackage(str) ? pinnedLabel(str, packageManager, resolveInfo) : launcherLabel(packageManager, resolveInfo)), resolveDockIcon(str, isPinnedPackage(str) ? flyoutIconForPinned(str) : iconForLauncherApp(packageManager, resolveInfo)));
    }

    private JSONObject appsCatalogObject(String str, String str2, Drawable drawable) throws JSONException {
        JSONObject jSONObject = new JSONObject();
        jSONObject.put("packageName", str);
        jSONObject.put("label", str2 != null ? str2 : "");
        try {
            String strEncodeIconDataUrl = encodeIconDataUrl(normalizeAdaptiveIcon(drawable, 96), 96, appsCatalogPlateColor(str));
            if (strEncodeIconDataUrl != null) {
                jSONObject.put("icon", strEncodeIconDataUrl);
            }
        } catch (Exception e) {
            Log.e("H6Viewer", "Error drawing icon for " + str, e);
        }
        return jSONObject;
    }

    private Integer appsCatalogPlateColor(String str) {
        int i;
        Integer numDockOverridePlateColor = dockOverridePlateColor(str);
        if (numDockOverridePlateColor != null) {
            return numDockOverridePlateColor;
        }
        if (!((this.dockAppOverrides == null || this.dockAppOverrides.icon(str) == null) ? false : true) && !usesDarkIconPlate(str)) {
            return null;
        }
        boolean zUsesDarkIconPlate = usesDarkIconPlate(str);
        boolean z = this.dockUiLight;
        if (zUsesDarkIconPlate) {
            i = z ? -14670029 : -234222318;
        } else {
            i = z ? -197122 : -118229254;
        }
        return Integer.valueOf(i);
    }

    private JSONObject appsCatalogStubEntry(String str, String str2) throws JSONException {
        return appsCatalogObject(str, resolveDockLabel(str, str2), resolveDockIcon(str, launcherIconForPackage(str)));
    }

    private void beginProjectionResolve(ProjectionPresence.Kind kind) {
        clearPendingProjection();
        this.pendingProjectionKind = kind;
        for (String str : ProjectionPresence.displayPackagesFor(kind)) {
            this.pendingProjectionPackages.add(str);
            requestProjectionTaskResolve(str);
        }
        if (this.projectionPresence != null) {
            this.projectionPresence.requestShow(kind);
        }
        this.pendingProjectionTimeout = () -> {
            if (this.pendingProjectionKind != kind) {
                return;
            }
            Log.w("H6Viewer", "Projection resolve timed out for " + kind + "; trying launch intents");
            clearPendingProjection();
            List<Intent> listLaunchIntents = this.projectionPresence != null ? this.projectionPresence.launchIntents(kind) : null;
            if (listLaunchIntents == null || listLaunchIntents.isEmpty()) {
                Log.w("H6Viewer", "Projection " + kind + ": no launch fallback available");
                return;
            }
            for (Intent intent : listLaunchIntents) {
                ComponentName component = intent.getComponent();
                intent.addFlags(337641472);
                try {
                    Bundle bundleBuildWindowOptions = buildWindowOptions(1, FULLSCREEN_BOUNDS);
                    Log.w("H6Viewer", "Projection launch " + kind + " -> " + component);
                    startActivity(intent, bundleBuildWindowOptions);
                    if (component != null) {
                        requestTaskBounds(component.getPackageName(), FULLSCREEN_BOUNDS);
                    }
                    notifyViewerShellLayout();
                    return;
                } catch (Exception e) {
                    Log.w("H6Viewer", "Projection launch failed for " + component, e);
                }
            }
        };
        this.mainHandler.postDelayed(this.pendingProjectionTimeout, 1500L);
    }

    private void beginSplashFade(int i) {
        if (this.splashFadeStarted) {
            return;
        }
        this.splashFadeStarted = true;
        hideBootHud();
        hideSplashSkip();
        if (this.splashFadeView == null) {
            endSplashOverlay();
            return;
        }
        Bitmap bitmap = this.splashHoldFrame;
        ImageView imageView = this.splashFadeView;
        if (bitmap != null) {
            imageView.setImageBitmap(this.splashHoldFrame);
            this.splashFadeView.setBackgroundColor(-16777216);
        } else {
            imageView.setImageDrawable(null);
            this.splashFadeView.setBackgroundColor(-16777216);
        }
        this.splashFadeView.setAlpha(1.0f);
        this.splashFadeView.setVisibility(0);
        this.splashFadeView.bringToFront();
        this.splashFadeView.animate().alpha(0.0f).setDuration(Math.max(200, i)).setStartDelay(48L).withEndAction(() -> {
            this.splashFadeView.setVisibility(8);
            this.splashFadeView.setImageDrawable(null);
            endSplashOverlay();
        }).start();
    }

    private void bindDockItem(MotionTrailLayout motionTrailLayout, Drawable drawable, String str, View.OnClickListener onClickListener) {
        bindDockItem(motionTrailLayout, drawable, str, onClickListener, null);
    }

    private void bindDockItem(MotionTrailLayout motionTrailLayout, Drawable drawable, String str, View.OnClickListener onClickListener, String str2) {
        String strResolveDockLabel;
        if (motionTrailLayout == null) {
            return;
        }
        ImageView imageView = (ImageView) motionTrailLayout.findViewWithTag("icon");
        TextView textView = (TextView) motionTrailLayout.findViewWithTag("label");
        float f = getResources().getDisplayMetrics().density;
        int iRound = this.dockIconPx > 0 ? this.dockIconPx : Math.round(60.0f * f);
        Drawable drawableResolveDockIcon = str2 != null ? resolveDockIcon(str2, drawable) : drawable;
        if (str2 != null) {
            strResolveDockLabel = resolveDockLabel(str2, str);
        } else {
            strResolveDockLabel = str != null ? str : "";
        }
        if (imageView != null) {
            imageView.setImageDrawable(normalizeAdaptiveIcon(drawableResolveDockIcon, iRound));
        }
        View viewFindViewWithTag = motionTrailLayout.findViewWithTag("iconPlate");
        if (viewFindViewWithTag != null) {
            Integer numDockOverridePlateColor = str2 != null ? dockOverridePlateColor(str2) : null;
            if (numDockOverridePlateColor != null) {
                motionTrailLayout.setTag(numDockOverridePlateColor);
                viewFindViewWithTag.setBackground(makeLauncherIconPlateDrawable(f, numDockOverridePlateColor.intValue()));
            } else {
                boolean z = str2 != null && usesDarkIconPlate(str2);
                motionTrailLayout.setTag(Boolean.valueOf(z));
                viewFindViewWithTag.setBackground(makeLauncherIconPlateDrawable(f, z));
            }
            int iRound2 = Math.round(dockPlatePx(iRound, f) * launcherIconInsetFrac());
            viewFindViewWithTag.setPadding(iRound2, iRound2, iRound2, iRound2);
        }
        if (textView != null) {
            textView.setText(strResolveDockLabel);
            textView.setVisibility(strResolveDockLabel.isEmpty() ? 8 : 0);
        }
        motionTrailLayout.setOnClickListener(onClickListener);
        motionTrailLayout.setClickable(onClickListener != null);
        if (str2 == null || str2.isEmpty()) {
            motionTrailLayout.setOnLongClickListener(null);
        } else {
            motionTrailLayout.setOnLongClickListener(view -> {
                showDockEditMenu(view, str2);
                return true;
            });
        }
        motionTrailLayout.setVisibility(0);
        if (this.launcherRevealed) {
            motionTrailLayout.setAlpha(1.0f);
            motionTrailLayout.setTranslationX(0.0f);
            motionTrailLayout.setTrailPx(0.0f);
        }
    }

    private void bindGwmHub() {
        Drawable drawable;
        if (this.gwmHubItem == null) {
            return;
        }
        Drawable drawableResolveDockIcon = resolveDockIcon("__gwm_hub", launcherIconForPackage("com.beantechs.vehiclecenter"));
        if (drawableResolveDockIcon == null) {
            try {
                drawable = getDrawable(R.drawable.ic_gwm_viewer);
            } catch (Exception e) {
                drawable = drawableResolveDockIcon;
            }
        } else {
            drawable = drawableResolveDockIcon;
        }
        bindDockItem(this.gwmHubItem, drawable, resolveDockLabel("__gwm_hub", "GWM"), this::showGwmHubMenu, "__gwm_hub");
    }

    private void bindProjectionSlot(ProjectionPresence.Kind kind) {
        if (this.projectionItem == null) {
            return;
        }
        if (kind == null || kind == ProjectionPresence.Kind.NONE) {
            this.projectionItem.setVisibility(8);
        } else {
            bindDockItem(this.projectionItem, this.projectionPresence != null ? this.projectionPresence.iconFor(kind) : null, this.projectionPresence != null ? this.projectionPresence.labelFor(kind) : "", view -> {
                launchProjection(kind);
            });
        }
    }

    private void bindRailEditLongPress() {
        for (View view : railCardViews()) {
            view.setLongClickable(true);
            view.setOnLongClickListener(view2 -> {
                if (this.railEditMode) {
                    return false;
                }
                view2.performHapticFeedback(0);
                callViewerDock("openRailEdit");
                return true;
            });
        }
    }

    private void bindRecentSlots(PackageManager packageManager, List<ResolveInfo> list) {
        ResolveInfo resolveInfo;
        HashMap map = new HashMap();
        if (list != null) {
            for (ResolveInfo resolveInfo2 : list) {
                if (resolveInfo2 != null && resolveInfo2.activityInfo != null) {
                    map.put(resolveInfo2.activityInfo.packageName, resolveInfo2);
                }
            }
        }
        HashSet hashSet = new HashSet();
        int i = 0;
        for (String str : this.recentPackages) {
            if (i >= this.recentItems.length) {
                break;
            }
            if (!skipInRecents(str) && (resolveInfo = (ResolveInfo) map.get(str)) != null) {
                String strLauncherLabel = launcherLabel(packageManager, resolveInfo);
                bindDockItem(this.recentItems[i], iconForLauncherApp(packageManager, resolveInfo), strLauncherLabel, view -> {
                    launchAppForPackage(str, strLauncherLabel);
                }, str);
                hashSet.add(str);
                i++;
            }
        }
        for (int i2 = i; i2 < this.recentItems.length; i2++) {
            if (this.recentItems[i2] != null) {
                this.recentItems[i2].setVisibility(8);
            }
        }
        hideRecentDuplicatesFromStrip(hashSet);
    }

    private void bindSideBySideItem() {
        if (this.sideBySideItem == null) {
            return;
        }
        float f = getResources().getDisplayMetrics().density;
        bindDockItem(this.sideBySideItem, makeSideBySideGlyph(this.dockIconPx > 0 ? this.dockIconPx : Math.round(60.0f * f)), "Side by Side", view -> {
            applyShellMode("appsOnly");
        });
        View viewFindViewWithTag = this.sideBySideItem.findViewWithTag("iconPlate");
        if (viewFindViewWithTag != null) {
            this.sideBySideItem.setTag(Boolean.TRUE);
            viewFindViewWithTag.setBackground(makeLauncherIconPlateDrawable(f, true));
        }
        this.sideBySideItem.setContentDescription("Side by Side: two apps split the screen");
    }

    private static int blendArgb(int i, int i2, float f) {
        float fMax = Math.max(0.0f, Math.min(1.0f, f));
        return (-16777216) | (Math.round((((i >> 16) & 255) * (1.0f - fMax)) + (((i2 >> 16) & 255) * fMax)) << 16) | (Math.round((((i >> 8) & 255) * (1.0f - fMax)) + (((i2 >> 8) & 255) * fMax)) << 8) | Math.round(((i & 255) * (1.0f - fMax)) + ((i2 & 255) * fMax));
    }

    private int bootProgressLineTopMarginPx() {
        return Math.max(0, 655 - pageOriginRect().top);
    }

    private String bottomCardAccessibilityDescription(BottomCardDescriptor bottomCardDescriptor) {
        if ("power".equals(bottomCardDescriptor.id)) {
            StringBuilder sb = new StringBuilder("Power flow. ");
            sb.append(bottomCardDescriptor.primary.isEmpty() ? "Power flow unavailable" : bottomCardDescriptor.primary);
            if (!bottomCardDescriptor.metricA.isEmpty()) {
                sb.append(". ").append(bottomCardDescriptor.metricA);
            }
            if (!bottomCardDescriptor.metricB.isEmpty()) {
                sb.append(". ").append(bottomCardDescriptor.metricB);
            }
            if (!bottomCardDescriptor.secondary.isEmpty()) {
                sb.append(". Source ").append(bottomCardDescriptor.secondary);
            }
            sb.append(". Opens power flow details.");
            return sb.toString();
        }
        if ("status".equals(bottomCardDescriptor.id)) {
            StringBuilder sb2 = new StringBuilder("Vehicle status. ");
            sb2.append(bottomCardDescriptor.primary.isEmpty() ? "Status unavailable" : bottomCardDescriptor.primary);
            if (!bottomCardDescriptor.secondary.isEmpty()) {
                sb2.append(". Source ").append(bottomCardDescriptor.secondary);
            }
            String[] strArr = {"driver door", "front passenger door", "rear left door", "rear right door", "tailgate"};
            int i = 0;
            while (i < strArr.length) {
                sb2.append(". ").append(strArr[i]).append(" ").append((bottomCardDescriptor.openingStates == null || i >= bottomCardDescriptor.openingStates.length) ? "unknown" : bottomCardDescriptor.openingStates[i]);
                i++;
            }
            sb2.append(". Opens vehicle status details.");
            return sb2.toString();
        }
        if ("navigation".equals(bottomCardDescriptor.id)) {
            StringBuilder sb3 = new StringBuilder("Navigation. ");
            sb3.append(bottomCardDescriptor.primary.isEmpty() ? "No route data" : bottomCardDescriptor.primary);
            if (!bottomCardDescriptor.navIdleCity.isEmpty()) {
                sb3.append(". ").append(bottomCardDescriptor.navIdleCity);
            }
            if (!bottomCardDescriptor.secondary.isEmpty()) {
                sb3.append(". ").append(bottomCardDescriptor.secondary);
            }
            if (!bottomCardDescriptor.metricA.isEmpty()) {
                sb3.append(". ").append(bottomCardDescriptor.metricA);
            }
            if (!bottomCardDescriptor.navIdleAction.isEmpty()) {
                sb3.append(". ").append(bottomCardDescriptor.navIdleAction);
            }
            sb3.append(". Opens navigation.");
            return sb3.toString();
        }
        if (!"tires".equals(bottomCardDescriptor.id)) {
            return bottomCardDescriptor.title + (bottomCardDescriptor.value.isEmpty() ? "" : ": " + bottomCardDescriptor.value);
        }
        String[] strArrTireReadings = tireReadings(bottomCardDescriptor);
        StringBuilder sb4 = new StringBuilder("Tires. ");
        sb4.append("Data state ").append(bottomCardDescriptor.state);
        if (!bottomCardDescriptor.secondary.isEmpty()) {
            sb4.append(". Source ").append(bottomCardDescriptor.secondary);
        }
        String[] strArr2 = {"front left", "front right", "rear left", "rear right"};
        for (int i2 = 0; i2 < strArr2.length; i2++) {
            sb4.append(". ").append(strArr2[i2]).append(" ").append(strArrTireReadings[i2]);
            if (bottomCardDescriptor.wheelStates != null && i2 < bottomCardDescriptor.wheelStates.length) {
                sb4.append(", ").append(bottomCardDescriptor.wheelStates[i2]);
            }
        }
        return sb4.toString();
    }

    private BottomCardDescriptor bottomCardById(String str) {
        for (BottomCardDescriptor bottomCardDescriptor : this.bottomCards) {
            if (bottomCardDescriptor.id.equals(str)) {
                return bottomCardDescriptor;
            }
        }
        return null;
    }

    private String boundsToCssJson(Rect rect) {
        float f = getResources().getDisplayMetrics().density;
        if (f <= 0.0f) {
            f = 1.0f;
        }
        Rect windowRect = toWindowRect(rect, pageOriginRect());
        return "{l:" + (windowRect.left / f) + ",t:" + (windowRect.top / f) + ",r:" + (windowRect.right / f) + ",b:" + (windowRect.bottom / f) + "}";
    }

    private LinearLayout buildAppsFabMenu(float f) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setGravity(1);
        int iRound = Math.round(f * 14.0f);
        linearLayout.setPadding(iRound, iRound, iRound, iRound);
        int iRound2 = Math.round(300.0f * f);
        linearLayout.setMinimumWidth(iRound2);
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(iRound2, -2));
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(-267513318);
        gradientDrawable.setCornerRadius(14.0f * f);
        gradientDrawable.setStroke(Math.max(1, Math.round(1.0f * f)), 620756991);
        linearLayout.setBackground(gradientDrawable);
        linearLayout.setElevation(10.0f * f);
        linearLayout.addView(makeAppsFabSectionLabel("SPLIT", f));
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(0);
        linearLayout2.setGravity(17);
        linearLayout2.setTag("ratioRow");
        linearLayout2.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        linearLayout2.addView(makeAppsFabRatioBtn("1:1", f));
        linearLayout2.addView(makeAppsFabRatioBtn("2:1", f));
        linearLayout2.addView(makeAppsFabRatioBtn("1:2", f));
        linearLayout.addView(linearLayout2);
        linearLayout.addView(makeAppsFabSectionLabel("LAYOUT", f));
        LinearLayout linearLayout3 = new LinearLayout(this);
        linearLayout3.setOrientation(1);
        linearLayout3.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        linearLayout3.addView(makeAppsFabLayoutTextBtn("appCar", "Exit Side by Side", f));
        linearLayout.addView(linearLayout3);
        LinearLayout linearLayout4 = new LinearLayout(this);
        linearLayout4.setOrientation(0);
        linearLayout4.setGravity(17);
        linearLayout4.setPadding(0, Math.round(8.0f * f), 0, 0);
        linearLayout4.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        linearLayout4.addView(makeAppsFabTextBtn("SWAP", f, () -> {
            setAppsFabMenuOpen(false);
            swapSlotApps();
        }));
        linearLayout4.addView(makeAppsFabTextBtn("SAVE", f, () -> {
            setAppsFabMenuOpen(false);
            saveAppsOnlyDefaultNow();
        }));
        linearLayout.addView(linearLayout4);
        return linearLayout;
    }

    private WindowManager.LayoutParams buildAppsFabWindowLp() {
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(-2, -2, Build.VERSION.SDK_INT < 26 ? 2002 : 2038, 16777480, -3);
        layoutParams.gravity = 8388659;
        return layoutParams;
    }

    private View buildConfigContentRow(float f, int i, int i2) {
        MainActivity mainActivity = this;
        int i3 = i2;
        BounceHorizontalScrollView bounceHorizontalScrollView = new BounceHorizontalScrollView(mainActivity);
        bounceHorizontalScrollView.setHorizontalScrollBarEnabled(false);
        bounceHorizontalScrollView.setOverScrollMode(0);
        bounceHorizontalScrollView.setHorizontalFadingEdgeEnabled(true);
        bounceHorizontalScrollView.setFadingEdgeLength(Math.round(48.0f * f));
        bounceHorizontalScrollView.setClipChildren(true);
        bounceHorizontalScrollView.setClipToPadding(true);
        bounceHorizontalScrollView.setBackgroundColor(0);
        LinearLayout linearLayout = new LinearLayout(mainActivity);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        linearLayout.setPadding(Math.round(8.0f * f), 0, Math.round(24.0f * f), 0);
        linearLayout.setClipChildren(false);
        String[][] strArr = {new String[]{"camera", "Camera"}, new String[]{"paint", "Color"}, new String[]{"wheels", "Wheels"}, new String[]{"speed", "Speed"}, new String[]{"doors", "Doors"}, new String[]{"lights", "Lights"}, new String[]{"env", "Env"}, new String[]{"model", "Model"}, new String[]{"perf", "Perf"}, new String[]{"fps", "FPS"}, new String[]{"xray", "X-Ray"}, new String[]{"time", "Time"}, new String[]{"save", "Save"}};
        mainActivity.dockToolIcons.clear();
        mainActivity.dockToolGlyphPx = Math.round(i3 * 0.55f);
        int i4 = 0;
        for (int length = strArr.length; i4 < length; length = length) {
            String[] strArr2 = strArr[i4];
            linearLayout.addView(mainActivity.makeToolDockChip(f, i, i3, mainActivity.dockToolGlyph(strArr2[0], "model".equals(strArr2[0]) || "perf".equals(strArr2[0]) || "fps".equals(strArr2[0]) ? Math.round(i3 * 0.92f) : mainActivity.dockToolGlyphPx), strArr2[0], strArr2[1], view -> {
                callViewerDock(strArr2[0]);
            }));
            i4++;
            mainActivity = this;
            i3 = i2;
        }
        bounceHorizontalScrollView.addView(linearLayout, new FrameLayout.LayoutParams(-2, -1));
        return bounceHorizontalScrollView;
    }

    private View buildLayoutContentRow(float f, int i, int i2) {
        BounceHorizontalScrollView bounceHorizontalScrollView = new BounceHorizontalScrollView(this, false);
        bounceHorizontalScrollView.setHorizontalScrollBarEnabled(false);
        bounceHorizontalScrollView.setFadingEdgeLength(Math.round(48.0f * f));
        bounceHorizontalScrollView.setClipChildren(true);
        bounceHorizontalScrollView.setClipToPadding(true);
        bounceHorizontalScrollView.setBackgroundColor(0);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(8388627);
        linearLayout.setPadding(Math.round(8.0f * f), Math.round(f * 4.0f), Math.round(24.0f * f), Math.round(4.0f * f));
        linearLayout.setClipChildren(false);
        linearLayout.setBackgroundColor(0);
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(0);
        linearLayout2.setGravity(16);
        View viewMakeWideDockChip = makeWideDockChip(f, Math.round(f * 64.0f), i2, "‹", view2 -> {
            callViewerDock("previousDesktop");
        });
        viewMakeWideDockChip.setContentDescription("Previous desktop");
        linearLayout2.addView(viewMakeWideDockChip);
        View viewMakeDesktopStatusChip = makeDesktopStatusChip(f, Math.round(178.0f * f), i2);
        viewMakeDesktopStatusChip.setOnClickListener(view3 -> {
            callViewerDock("openDesktopStudio");
        });
        viewMakeDesktopStatusChip.setOnLongClickListener(view4 -> {
            callViewerDock("openDesktopStudio");
            return true;
        });
        linearLayout2.addView(viewMakeDesktopStatusChip);
        View viewMakeWideDockChip2 = makeWideDockChip(f, Math.round(f * 64.0f), i2, "›", view5 -> {
            callViewerDock("nextDesktop");
        });
        viewMakeWideDockChip2.setContentDescription("Next desktop");
        linearLayout2.addView(viewMakeWideDockChip2);
        View viewMakeWideDockChip3 = makeWideDockChip(f, Math.round(118.0f * f), i2, "Customize", view6 -> {
            callViewerDock("openDesktopStudio");
        });
        viewMakeWideDockChip3.setContentDescription("Customize desktop");
        linearLayout2.addView(viewMakeWideDockChip3);
        linearLayout.addView(makeLayoutGroup(f, "DESKTOP", linearLayout2));
        linearLayout.addView(makeLayoutDivider(f));
        LinearLayout linearLayout3 = new LinearLayout(this);
        linearLayout3.setOrientation(0);
        linearLayout3.setGravity(16);
        this.layoutLauncherSurfaceChip = makeWideDockChip(f, Math.round(116.0f * f), i2, "Launcher", view7 -> {
            chooseDockSurface("launcher", true);
        });
        this.layoutLauncherSurfaceChip.setContentDescription("Show app launcher. Long press to customize");
        this.layoutLauncherSurfaceChip.setOnLongClickListener(view8 -> {
            callViewerDock("openDesktopStudio");
            return true;
        });
        this.layoutCardsSurfaceChip = makeWideDockChip(f, Math.round(102.0f * f), i2, "Cards", view9 -> {
            chooseDockSurface("cards", true);
        });
        this.layoutCardsSurfaceChip.setContentDescription("Show quick cards. Long press to customize");
        this.layoutCardsSurfaceChip.setOnLongClickListener(view10 -> {
            callViewerDock("openDesktopStudio");
            return true;
        });
        linearLayout3.addView(this.layoutLauncherSurfaceChip);
        linearLayout3.addView(this.layoutCardsSurfaceChip);
        linearLayout.addView(makeLayoutGroup(f, "BOTTOM", linearLayout3));
        linearLayout.addView(makeLayoutDivider(f));
        LinearLayout linearLayout4 = new LinearLayout(this);
        linearLayout4.setOrientation(0);
        linearLayout4.setGravity(16);
        this.layoutCenterFillChip = makeCenterFillChip(f, Math.round(190.0f * f), i2);
        linearLayout4.addView(this.layoutCenterFillChip);
        refreshCenterFillChip();
        this.layoutAddWidgetChip = makeWideDockChip(f, Math.round(126.0f * f), i2, "Add card", view11 -> {
            callViewerDock("addWidget");
        });
        this.layoutAddWidgetChip.setContentDescription("Add card");
        linearLayout4.addView(this.layoutAddWidgetChip);
        this.layoutThemeChip = makeWideDockChip(f, Math.round(142.0f * f), i2, "Appearance", view -> {
            callViewerDock("cycleWidgetTheme");
        });
        this.layoutThemeLabel = findDockChipLabel(this.layoutThemeChip);
        this.layoutThemeChip.setContentDescription("Cycle appearance theme");
        linearLayout4.addView(this.layoutThemeChip);
        linearLayout.addView(makeLayoutGroup(f, "OTHER", linearLayout4));
        bounceHorizontalScrollView.addView(linearLayout, new FrameLayout.LayoutParams(-2, -1));
        return bounceHorizontalScrollView;
    }

    private LinearLayout buildModeDrawer(float f, int i, int i2, int i3) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(8388659);
        linearLayout.setClipChildren(false);
        linearLayout.setBackgroundColor(0);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(i, -1);
        layoutParams.leftMargin = Math.round(8.0f * f);
        linearLayout.setLayoutParams(layoutParams);
        this.modeCollapsedBtn = makeModeCell(f, i, i2, i3, dockGlyphCards(cardsDockGlyphPx(i2, f), dockGlyphColor(false)), "Cards", view2 -> {
            toggleDockSurface();
            selectStripMode("apps");
        });
        this.modeCollapsedBtn.setContentDescription("Show quick cards");
        this.modeCollapsedBtn.setOnLongClickListener(view -> {
            callViewerDock("openDesktopStudio");
            return true;
        });
        this.modeCollapsedIcon = (ImageView) this.modeCollapsedBtn.findViewWithTag("modeIcon");
        styleCardsDockCell(this.modeCollapsedBtn, f, i2);
        linearLayout.addView(this.modeCollapsedBtn);
        this.modeAppsBtn = null;
        this.modeLayoutBtn = null;
        this.modeConfigBtn = null;
        this.modeSurfaceBtn = null;
        return linearLayout;
    }

    private View buildQuickCardsRow(float f, int i) {
        BounceHorizontalScrollView bounceHorizontalScrollView = new BounceHorizontalScrollView(this, false);
        bounceHorizontalScrollView.setHorizontalScrollBarEnabled(false);
        bounceHorizontalScrollView.setFadingEdgeLength(Math.round(42.0f * f));
        bounceHorizontalScrollView.setClipChildren(true);
        bounceHorizontalScrollView.setClipToPadding(true);
        bounceHorizontalScrollView.setBackgroundColor(0);
        bounceHorizontalScrollView.setHorizontalFadingEdgeEnabled(false);
        bounceHorizontalScrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> bounceHorizontalScrollView.setHorizontalFadingEdgeEnabled(scrollX > 0));
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(8388659);
        linearLayout.setPadding(Math.round(12.0f * f), Math.round(5.0f * f), Math.round(24.0f * f), Math.round(6.0f * f));
        this.quickCardsRow = linearLayout;
        populateQuickCardsRow(linearLayout, f);
        bindRailEditLongPress();
        bounceHorizontalScrollView.addView(linearLayout, new FrameLayout.LayoutParams(-2, -1));
        return bounceHorizontalScrollView;
    }

    /**
     * Tela cheia de um app COMUM no display 0, já sem a faixa da barra inferior (Alpha 6.7).
     *
     * Era sempre FULLSCREEN_BOUNDS (0,0,1920,720): limites explícitos de tela cheia inteira, os
     * únicos do app que não descontavam a barra — e a faixa preta acima dela aparecia justamente
     * nos apps abertos. O desconto é o mesmo das outras aberturas, e com a barra desligada volta a
     * ser a tela inteira. Ver br.com.opengw.telas.Telas.limitesDeTelaCheiaNoDisplay0.
     *
     * Projeção (Android Auto/CarPlay) NÃO passa por aqui: a barra sai da frente dela, que continua
     * com FULLSCREEN_BOUNDS.
     */
    private Rect fullscreenAppBounds(String str) {
        try {
            int[] b = br.com.opengw.telas.Telas.limitesDeTelaCheiaNoDisplay0(str);
            if (b != null && b.length == 4 && b[2] > b[0] && b[3] > b[1]) {
                return new Rect(b[0], b[1], b[2], b[3]);
            }
        } catch (Throwable t) {
            Log.w("H6Viewer", "Tela cheia sem desconto da barra para " + str + " (" + t.getClass().getSimpleName() + ")");
        }
        return FULLSCREEN_BOUNDS;
    }

    private Bundle buildWindowOptions(int i, Rect rect) {
        allowHiddenApis();
        View viewLaunchAnchorForBounds = launchAnchorForBounds(rect);
        ActivityOptions activityOptionsMakeBasic = (i != 5 || viewLaunchAnchorForBounds == null || viewLaunchAnchorForBounds.getWidth() <= 0 || viewLaunchAnchorForBounds.getHeight() <= 0) ? ActivityOptions.makeBasic() : ActivityOptions.makeClipRevealAnimation(viewLaunchAnchorForBounds, 0, 0, viewLaunchAnchorForBounds.getWidth(), viewLaunchAnchorForBounds.getHeight());
        try {
            ActivityOptions.class.getMethod("setLaunchWindowingMode", Integer.TYPE).invoke(activityOptionsMakeBasic, Integer.valueOf(i));
        } catch (Exception e) {
            Log.w("H6Viewer", "setLaunchWindowingMode reflection failed; using bundle key", e);
        }
        if (rect != null) {
            activityOptionsMakeBasic.setLaunchBounds(new Rect(rect));
        }
        Bundle bundle = activityOptionsMakeBasic.toBundle();
        if (bundle == null) {
            bundle = new Bundle();
        }
        bundle.putInt("android.activity.windowingMode", i);
        if (rect != null) {
            bundle.putParcelable("android:activity.launchBounds", new Rect(rect));
        }
        return bundle;
    }

    private void callViewerDock(String str) {
        if (this.webView == null || str == null) {
            return;
        }
        final String script2 = "try{if(window.__app&&typeof window.__app.dockCommand==='function'){window.__app.dockCommand('" + str.replace("'", "").replace("\\", "").replace("\"", "") + "');}}catch(e){}";
        this.webView.post(() -> {
            this.webView.evaluateJavascript(script2, null);
        });
    }

    private boolean canToggleOpenAsWindow(String str) {
        if (str == null || str.isEmpty() || str.equals(getPackageName()) || isPinnedPackage(str) || isGwmApp(str)) {
            return false;
        }
        return !ProjectionPresence.isProjectionPackage(str);
    }

    private boolean canUninstallPackage(String str) {
        if (str == null || str.isEmpty() || str.equals(getPackageName()) || isPinnedPackage(str) || isGwmApp(str)) {
            return false;
        }
        try {
            return (getPackageManager().getApplicationInfo(str, 0).flags & 1) == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void captureDesktopSnapshotNow(String str, int i, int i2) {
        String strReplaceAll = str != null ? str.replaceAll("[^A-Za-z0-9_-]", "") : "";
        if (strReplaceAll.isEmpty() || this.webView == null || Build.VERSION.SDK_INT < 26) {
            return;
        }
        View decorView = getWindow().getDecorView();
        if (decorView.getWidth() <= 0 || decorView.getHeight() <= 0) {
            return;
        }
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(Math.max(64, Math.min(960, i)), Math.max(24, Math.min(360, i2)), Bitmap.Config.ARGB_8888);
        try {
            PixelCopy.request(getWindow(), new Rect(0, 0, decorView.getWidth(), decorView.getHeight()), bitmapCreateBitmap, i3 -> {
                if (i3 == 0) {
                    new Thread(() -> {
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        bitmapCreateBitmap.compress(Bitmap.CompressFormat.JPEG, 72, byteArrayOutputStream);
                        bitmapCreateBitmap.recycle();
                        final String script7 = "try{window.__app&&window.__app.onDesktopSnapshot('" + strReplaceAll + "','data:image/jpeg;base64," + Base64.encodeToString(byteArrayOutputStream.toByteArray(), 2) + "');}catch(e){}";
                        this.webView.post(() -> {
                            this.webView.evaluateJavascript(script7, null);
                        });
                    }, "desktop-snapshot").start();
                } else {
                    bitmapCreateBitmap.recycle();
                    Log.w("H6Viewer", "desktop snapshot failed: " + i3);
                }
            }, this.mainHandler);
        } catch (RuntimeException e) {
            bitmapCreateBitmap.recycle();
            Log.w("H6Viewer", "desktop snapshot failed", e);
        }
    }

    private static int cardsDockGlyphPx(int i, float f) {
        return Math.round(dockPlatePx(i, f) * 0.44444445f);
    }

    private void chooseDockSurface(String str, boolean z) {
        this.dockSurfaceMode = normalizeDockSurfaceMode(str);
        persistDockSurfaceMode();
        refreshDockSurfaceUi(true);
        if (z) {
            callViewerDock("cards".equals(this.dockSurfaceMode) ? "showCards" : "showLauncher");
        }
    }

    private int chromeBottomGapPx() {
        return Math.round(80.0f * getResources().getDisplayMetrics().density);
    }

    private int chromeReservePx() {
        return Math.round(28.0f * getResources().getDisplayMetrics().density);
    }

    private void clampAppsFabPosition() {
        int iRound;
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        if (this.appsFabRoot == null || this.appsFabRoot.getWidth() <= 0) {
            iRound = Math.round((this.appsFabMenuOpen ? 300 : 72) * displayMetrics.density);
        } else {
            iRound = this.appsFabRoot.getWidth();
        }
        int iRound2 = (this.appsFabRoot == null || this.appsFabRoot.getHeight() <= 0) ? Math.round(displayMetrics.density * 72.0f) : this.appsFabRoot.getHeight();
        if (this.appsFabRoot != null && (this.appsFabRoot.getWidth() <= 0 || this.appsFabRoot.getHeight() <= 0)) {
            this.appsFabRoot.measure(View.MeasureSpec.makeMeasureSpec(displayMetrics.widthPixels, Integer.MIN_VALUE), View.MeasureSpec.makeMeasureSpec(displayMetrics.heightPixels, Integer.MIN_VALUE));
            iRound = Math.max(iRound, this.appsFabRoot.getMeasuredWidth());
            iRound2 = Math.max(iRound2, this.appsFabRoot.getMeasuredHeight());
        }
        if (this.appsFabPosX == Integer.MIN_VALUE) {
            this.appsFabPosX = 0;
        }
        if (this.appsFabPosY == Integer.MIN_VALUE) {
            this.appsFabPosY = 0;
        }
        this.appsFabPosX = Math.max(0, Math.min(this.appsFabPosX, Math.max(0, displayMetrics.widthPixels - iRound)));
        this.appsFabPosY = Math.max(0, Math.min(this.appsFabPosY, Math.max(0, displayMetrics.heightPixels - iRound2)));
    }

    private Rect clampSlot(Rect rect, Rect rect2) {
        int iMax = Math.max(rect.top, slotBandTopPx());
        int iMin = Math.min(rect.bottom, slotBandBottomPx());
        if (iMin - iMax < 200) {
            iMin = iMax + 200;
        }
        int iNavRailReservePx = navRailReservePx();
        Point point = new Point();
        try {
            getWindowManager().getDefaultDisplay().getRealSize(point);
        } catch (Exception e) {
        }
        return new Rect(Math.max(Math.max(rect.left, rect2.left), iNavRailReservePx), iMax, Math.min(Math.min(rect.right, rect2.right), point.x > 0 ? point.x - iNavRailReservePx : rect2.right), iMin);
    }

    private String cleanBottomCardText(String str, int i) {
        String strCleanIndicator = cleanIndicator(str);
        return strCleanIndicator.length() > i ? strCleanIndicator.substring(0, i) : strCleanIndicator;
    }

    private String cleanIndicator(String str) {
        if (str == null) {
            return "";
        }
        String strReplace = str.trim().replace('\n', ' ').replace('\r', ' ');
        return strReplace.length() > 48 ? strReplace.substring(0, 48) : strReplace;
    }

    private void clearDockStripBackground(View view) {
        if (view == null) {
            return;
        }
        view.setBackground(null);
        view.setBackgroundColor(0);
    }

    private void clearLeftSlot() {
        if (this.pinBoundsRunnable != null) {
            this.mainHandler.removeCallbacks(this.pinBoundsRunnable);
        }
        this.activePopupPackage = "";
        this.activePopupTaskId = -1;
        this.activePopupComponent = null;
        notifyViewerShellLayout();
    }

    private void clearPendingProjection() {
        this.pendingProjectionKind = null;
        this.pendingProjectionPackages.clear();
        if (this.pendingProjectionTimeout != null) {
            this.mainHandler.removeCallbacks(this.pendingProjectionTimeout);
            this.pendingProjectionTimeout = null;
        }
    }

    private void clearRightSlot() {
        if (this.pinMediaBoundsRunnable != null) {
            this.mainHandler.removeCallbacks(this.pinMediaBoundsRunnable);
        }
        this.activeMediaPackage = "";
        this.activeMediaTaskId = -1;
        this.activeMediaComponent = null;
        notifyViewerShellLayout();
    }

    private void closeExistingTasksForPackage(String str) {
        int iFindTaskIdForPackage;
        int i = 0;
        while (true) {
            int i2 = i + 1;
            if (i >= 4 || (iFindTaskIdForPackage = findTaskIdForPackage(str)) < 0) {
                return;
            }
            removeTaskById(iFindTaskIdForPackage);
            i = i2;
        }
    }

    private void closePopupApp(String str) {
        if (str != null) {
            try {
                if (str.equals(this.activeMediaPackage)) {
                    dismissMediaPopup(str);
                } else {
                    dismissPopup(str);
                }
            } catch (Exception e) {
                Log.e("H6Viewer", "Error closing app " + str, e);
            }
        } else {
            dismissPopup(str);
        }
    }

    private int compensatedBottomMarginPx(int i) {
        return Math.max(0, i - overscanBottomPx());
    }

    private void completeProjectionRaise(int i, String str) {
        clearPendingProjection();
        if (i < 0) {
            return;
        }
        Log.w("H6Viewer", "Projection raise task=" + i + " pkg=" + str);
        if (moveTaskToFrontNoAnim(i)) {
            requestTaskBounds(str, FULLSCREEN_BOUNDS);
            notifyViewerShellLayout();
        }
    }

    private void crossfadeFaces(View view, View view2) {
        if (view2 != null) {
            view2.animate().cancel();
            view2.setAlpha(0.0f);
            view2.setVisibility(0);
            view2.animate().alpha(1.0f).setDuration(160L).start();
        }
        if (view != null) {
            view.animate().cancel();
            view.animate().alpha(0.0f).setDuration(120L).withEndAction(() -> {
                view.setVisibility(8);
                view.setAlpha(1.0f);
            }).start();
        }
    }

    private void crossfadeStripContent(String str, String str2) {
        showStripContent(str2, true);
    }

    private float cssPx(int i) {
        float f = getResources().getDisplayMetrics().density;
        return i / (f <= 0.0f ? 1.0f : f);
    }

    private static Bitmap decodeDataUrlBitmap(String str) {
        int iIndexOf;
        if (str == null || (iIndexOf = str.indexOf(44)) < 0 || iIndexOf + 1 >= str.length()) {
            return null;
        }
        try {
            byte[] bArrDecode = Base64.decode(str.substring(iIndexOf + 1), 0);
            return BitmapFactory.decodeByteArray(bArrDecode, 0, bArrDecode.length);
        } catch (Exception e) {
            return null;
        }
    }

    private void dismissAllOverlays() {
        if (this.activePopupPackage != null && !this.activePopupPackage.isEmpty()) {
            dismissPopup(this.activePopupPackage);
        }
        if (this.activeMediaPackage == null || this.activeMediaPackage.isEmpty()) {
            return;
        }
        dismissMediaPopup(this.activeMediaPackage);
    }

    private void dismissAppsFabOverlay() {
        this.appsFabMenuOpen = false;
        this.appsFabBtnAnchorY = Integer.MIN_VALUE;
        dismissAppsFabScrim();
        if (this.appsFabMenu != null) {
            this.appsFabMenu.setVisibility(8);
        }
        if (!this.appsFabOverlayAttached || this.appsFabRoot == null) {
            this.appsFabOverlayAttached = false;
            return;
        }
        try {
            if (this.appsFabUsesSystemOverlay) {
                getWindowManager().removeView(this.appsFabRoot);
            } else if (this.appsFabRoot.getParent() instanceof ViewGroup) {
                ((ViewGroup) this.appsFabRoot.getParent()).removeView(this.appsFabRoot);
            }
        } catch (Exception e) {
        }
        this.appsFabOverlayAttached = false;
        this.appsFabUsesSystemOverlay = false;
        applyChromeOnTop(false);
    }

    private void dismissAppsFabScrim() {
        if (!this.appsFabScrimAttached || this.appsFabScrim == null) {
            this.appsFabScrimAttached = false;
            return;
        }
        try {
            if (this.appsFabUsesSystemOverlay) {
                getWindowManager().removeView(this.appsFabScrim);
            } else if (this.appsFabScrim.getParent() instanceof ViewGroup) {
                ((ViewGroup) this.appsFabScrim.getParent()).removeView(this.appsFabScrim);
            }
        } catch (Exception e) {
        }
        this.appsFabScrimAttached = false;
    }

    private void dismissDockCustomizeSheet() {
        if (this.dockCustomizeSheet == null) {
            return;
        }
        try {
            this.dockCustomizeSheet.dismiss();
        } catch (Exception e) {
        }
        this.dockCustomizeSheet = null;
    }

    private void dismissDockEditMenu() {
        dismissUnhidePicker();
        dismissDockCustomizeSheet();
        if (this.dockEditMenu == null) {
            return;
        }
        try {
            this.dockEditMenu.dismiss();
        } catch (Exception e) {
        }
        this.dockEditMenu = null;
    }

    private void dismissGwmHubMenu() {
        if (this.gwmHubMenu == null) {
            return;
        }
        try {
            this.gwmHubMenu.dismiss();
        } catch (Exception e) {
        }
        this.gwmHubMenu = null;
    }

    private void dismissMediaPopup(String str) {
        String str2 = (str == null || str.isEmpty()) ? this.activeMediaPackage : str;
        if (str2 == null || str2.isEmpty()) {
            return;
        }
        if (this.pinMediaBoundsRunnable != null) {
            this.mainHandler.removeCallbacks(this.pinMediaBoundsRunnable);
        }
        int iFindTaskIdForPackage = this.activeMediaTaskId;
        if (iFindTaskIdForPackage < 0 || (str != null && !str.equals(this.activeMediaPackage))) {
            iFindTaskIdForPackage = findTaskIdForPackage(str2);
        }
        removeTaskById(iFindTaskIdForPackage);
        if (str2.equals(this.activeMediaPackage)) {
            clearRightSlot();
        }
    }

    private void dismissPopup(String str) {
        String str2 = (str == null || str.isEmpty()) ? this.activePopupPackage : str;
        if (str2 == null || str2.isEmpty()) {
            return;
        }
        if (this.pinBoundsRunnable != null) {
            this.mainHandler.removeCallbacks(this.pinBoundsRunnable);
        }
        int iFindTaskIdForPackage = this.activePopupTaskId;
        if (iFindTaskIdForPackage < 0 || (str != null && !str.equals(this.activePopupPackage))) {
            iFindTaskIdForPackage = findTaskIdForPackage(str2);
        }
        removeTaskById(iFindTaskIdForPackage);
        forceStopPackage(str2);
        if (str2.equals(this.activePopupPackage)) {
            clearLeftSlot();
        }
    }

    private void dismissQuickMenu() {
        if (this.quickMenuWindow == null) {
            return;
        }
        try {
            this.quickMenuWindow.dismiss();
        } catch (RuntimeException e) {
        }
        this.quickMenuWindow = null;
    }

    private void dismissUnhidePicker() {
        if (this.unhidePicker == null) {
            return;
        }
        try {
            this.unhidePicker.dismiss();
        } catch (Exception e) {
        }
        this.unhidePicker = null;
    }

    private Rect displayRect() {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        return new Rect(0, 0, displayMetrics.widthPixels, displayMetrics.heightPixels);
    }

    private Drawable dockGlyphAppTiles(int i, int i2) {
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(i, i, Bitmap.Config.ARGB_8888);
        bitmapCreateBitmap.setDensity(getResources().getDisplayMetrics().densityDpi);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        Paint paintDockGlyphStroke = dockGlyphStroke(i2, i, 0.05f);
        float f = i;
        float strokeWidth = (paintDockGlyphStroke.getStrokeWidth() * 0.5f) + 0.5f;
        float f2 = f * 0.14f;
        float f3 = ((f - (strokeWidth * 2.0f)) - f2) / 2.0f;
        float f4 = f * 0.08f;
        int i3 = 0;
        while (true) {
            if (i3 >= 2) {
                return new BitmapDrawable(getResources(), bitmapCreateBitmap);
            }
            int i4 = 0;
            for (int i5 = 2; i4 < i5; i5 = i5) {
                float f5 = (i4 * (f3 + f2)) + strokeWidth;
                float f6 = (i3 * (f3 + f2)) + strokeWidth;
                canvas.drawRoundRect(f5, f6, f5 + f3, f6 + f3, f4, f4, paintDockGlyphStroke);
                i4++;
            }
            i3++;
        }
    }

    private Drawable dockGlyphApps(int i, int i2) {
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(i, i, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        Paint paint = new Paint(1);
        paint.setColor(i2);
        float f = i;
        float f2 = 0.18f * f;
        float f3 = 0.14f * f;
        float f4 = ((f - (f2 * 2.0f)) - (2.0f * f3)) / 3.0f;
        for (int i3 = 0; i3 < 3; i3++) {
            for (int i4 = 0; i4 < 3; i4++) {
                canvas.drawCircle((i4 * (f4 + f3)) + f2 + (f4 * 0.5f), (i3 * (f4 + f3)) + f2 + (0.5f * f4), 0.42f * f4, paint);
            }
        }
        return new BitmapDrawable(getResources(), bitmapCreateBitmap);
    }

    private Drawable dockGlyphCards(int i, int i2) {
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(i, i, Bitmap.Config.ARGB_8888);
        bitmapCreateBitmap.setDensity(getResources().getDisplayMetrics().densityDpi);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        Paint paintDockGlyphStroke = dockGlyphStroke(i2, i, 0.07083333f);
        float f = i;
        float f2 = f * 0.083333336f;
        canvas.drawRoundRect(0.25833333f * f, f * 0.14166667f, 0.84999996f * f, 0.575f * f, f2, f2, paintDockGlyphStroke);
        canvas.drawRoundRect(f * 0.14166667f, 0.39166665f * f, 0.73333335f * f, 0.85833335f * f, f2, f2, paintDockGlyphStroke);
        return new BitmapDrawable(getResources(), bitmapCreateBitmap);
    }

    private int dockGlyphColor() {
        return dockGlyphColor(true);
    }

    private int dockGlyphColor(boolean z) {
        if (z) {
            return this.dockAccentColor;
        }
        return !this.dockUiLight ? -385875969 : -8484458;
    }

    private Drawable dockGlyphConfig(int i, int i2) {
        Drawable drawableSystemDockIcon = systemDockIcon(17301577, i2);
        return drawableSystemDockIcon != null ? drawableSystemDockIcon : new ColorDrawable(i2);
    }

    private Drawable dockGlyphLayout(int i, int i2) {
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(i, i, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        Paint paint = new Paint(1);
        paint.setColor(i2);
        paint.setStyle(Paint.Style.FILL);
        float f = i;
        float f2 = f * 0.18f;
        float f3 = f - (2.0f * f2);
        float f4 = f * 0.18f;
        float f5 = f * 0.28f;
        float f6 = f * 0.06f;
        float f7 = (f - ((((f4 + f6) + f5) + f6) + f4)) * 0.5f;
        canvas.drawRoundRect(f7, f2, f7 + f4, f2 + f3, 3.0f, 3.0f, paint);
        float f8 = f7 + f4 + f6;
        paint.setAlpha(140);
        canvas.drawRoundRect(f8, f2 + (0.15f * f3), f8 + f5, f2 + (0.85f * f3), 4.0f, 4.0f, paint);
        paint.setAlpha(255);
        float f9 = f8 + f5 + f6;
        canvas.drawRoundRect(f9, f2, f9 + f4, f2 + f3, 3.0f, 3.0f, paint);
        return new BitmapDrawable(getResources(), bitmapCreateBitmap);
    }

    private Drawable dockGlyphMosaic(int i, int i2) {
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(i, i, Bitmap.Config.ARGB_8888);
        bitmapCreateBitmap.setDensity(getResources().getDisplayMetrics().densityDpi);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        Paint paintDockGlyphStroke = dockGlyphStroke(i2, i, 0.05f);
        float f = i;
        float strokeWidth = (paintDockGlyphStroke.getStrokeWidth() * 0.5f) + 0.5f;
        float f2 = f * 0.12f;
        float f3 = f * 0.42f;
        float f4 = strokeWidth + f3 + f2;
        float f5 = f * 0.5f;
        float f6 = f * 0.08f;
        canvas.drawRoundRect(strokeWidth, strokeWidth, strokeWidth + f3, f - strokeWidth, f6, f6, paintDockGlyphStroke);
        canvas.drawRoundRect(f4, strokeWidth, f - strokeWidth, f5 - (f2 * 0.5f), f6, f6, paintDockGlyphStroke);
        canvas.drawRoundRect(f4, f5 + (0.5f * f2), f - strokeWidth, f - strokeWidth, f6, f6, paintDockGlyphStroke);
        return new BitmapDrawable(getResources(), bitmapCreateBitmap);
    }

    private Paint dockGlyphStroke(int i, int i2) {
        return dockGlyphStroke(i, i2, 0.055f);
    }

    private Paint dockGlyphStroke(int i, int i2, float f) {
        Paint paint = new Paint(1);
        paint.setColor(i);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(Math.max(1.75f, i2 * f));
        return paint;
    }

    private Drawable dockGlyphXray(int i) {
        try {
            if (this.dockXrayGlyphSrc == null) {
                InputStream inputStreamOpen = getAssets().open("www/assets/icons/xray-engine.png");
                try {
                    this.dockXrayGlyphSrc = BitmapFactory.decodeStream(inputStreamOpen);
                    if (inputStreamOpen != null) {
                        inputStreamOpen.close();
                    }
                } catch (Throwable th) {
                    if (inputStreamOpen != null) {
                        try {
                            inputStreamOpen.close();
                        } catch (Throwable th2) {
                            th.addSuppressed(th2);
                        }
                    }
                    throw th;
                }
            }
            if (this.dockXrayGlyphSrc == null) {
                return null;
            }
            Bitmap bitmapCreateBitmap = Bitmap.createBitmap(i, i, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmapCreateBitmap);
            Paint paint = new Paint(3);
            paint.setColorFilter(new PorterDuffColorFilter(dockGlyphColor(), PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(this.dockXrayGlyphSrc, (Rect) null, new Rect(0, 0, i, i), paint);
            return new BitmapDrawable(getResources(), bitmapCreateBitmap);
        } catch (Exception e) {
            Log.w("H6Viewer", "xray dock glyph load failed", e);
            return null;
        }
    }

    private int dockLabelColor() {
        return this.dockUiLight ? -12761776 : -1;
    }

    private int dockLabelColorMuted() {
        return this.dockUiLight ? -6642770 : -855638017;
    }

    private Integer dockOverridePlateColor(String str) {
        String strBg;
        if (str == null || this.dockAppOverrides == null || this.dockAppOverrides.icon(str) == null || (strBg = this.dockAppOverrides.bg(str)) == null) {
            return null;
        }
        return Integer.valueOf(DockAppOverrides.parseColor(strBg, -12761520));
    }

    private int[] dockPlateBorderGradientColors(boolean z) {
        return z ? new int[]{withAlpha(this.dockAccentColor, 208), -2003260740} : new int[]{-1598502700, 1621143744};
    }

    private int[] dockPlateGradientColors(boolean z) {
        if (this.dockUiLight) {
            return z ? new int[]{blendArgb(-460036, this.dockAccentColor, 0.13f), blendArgb(-1182986, this.dockAccentColor, 0.08f)} : new int[]{-328450, -854278};
        }
        return z ? new int[]{withAlpha(this.dockAccentColor, 114), 1493172223} : new int[]{1526726655, 1224736767};
    }

    private static int dockPlatePx(int i, float f) {
        return Math.round(10.0f * f) + i;
    }

    private int dockReservePx() {
        if ("appsOnly".equals(this.shellMode)) {
            return 0;
        }
        return launcherStripHeightPx() + launcherBottomGapPx() + Math.round(16.0f * getResources().getDisplayMetrics().density);
    }

    private Drawable dockToolGlyph(String str, int i) {
        return dockToolGlyph(str, i, null);
    }

    private Drawable dockToolGlyph(String str, int i, String str2) {
        Drawable drawableDockGlyphXray;
        if ("xray".equals(str) && (drawableDockGlyphXray = dockGlyphXray(i)) != null) {
            return drawableDockGlyphXray;
        }
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(i, i, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        Paint paint = new Paint(1);
        paint.setColor(dockGlyphColor());
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.6f, i * 0.075f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        float f = i;
        float f2 = f / 24.0f;
        String str3 = str == null ? "" : str;
        switch (str3) {
            case "camera":
                canvas.drawRoundRect(f2 * 3.0f, f2 * 7.0f, f2 * 21.0f, f2 * 20.0f, f2 * 2.0f, f2 * 2.0f, paint);
                canvas.drawCircle(f2 * 12.0f, 13.5f * f2, 3.2f * f2, paint);
                canvas.drawLine(f2 * 8.0f, f2 * 7.0f, f2 * 9.5f, f2 * 4.5f, paint);
                canvas.drawLine(f2 * 9.5f, f2 * 4.5f, f2 * 14.5f, f2 * 4.5f, paint);
                canvas.drawLine(f2 * 14.5f, f2 * 4.5f, f2 * 16.0f, f2 * 7.0f, paint);
                break;
            case "paint":
                int cssColor = parseCssColor(str2 != null ? str2 : this.dockPaintHex, -723465);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor((-16777216) | cssColor);
                canvas.drawCircle(f2 * 12.0f, f2 * 12.0f, f2 * 7.0f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(1509949439);
                canvas.drawCircle(f2 * 12.0f, f2 * 12.0f, f2 * 7.0f, paint);
                break;
            case "wheels":
                canvas.drawCircle(f2 * 12.0f, f2 * 12.0f, 10.0f * f2, paint);
                canvas.drawCircle(f2 * 12.0f, f2 * 12.0f, f2 * 3.0f, paint);
                canvas.drawLine(f2 * 12.0f, f2 * 2.0f, f2 * 12.0f, f2 * 9.0f, paint);
                canvas.drawLine(f2 * 12.0f, f2 * 15.0f, f2 * 12.0f, f2 * 22.0f, paint);
                canvas.drawLine(f2 * 2.0f, f2 * 12.0f, f2 * 9.0f, f2 * 12.0f, paint);
                canvas.drawLine(f2 * 15.0f, f2 * 12.0f, f2 * 22.0f, f2 * 12.0f, paint);
                canvas.drawLine(f2 * 4.93f, f2 * 4.93f, f2 * 9.88f, f2 * 9.88f, paint);
                canvas.drawLine(f2 * 14.12f, f2 * 14.12f, f2 * 19.07f, f2 * 19.07f, paint);
                canvas.drawLine(f2 * 4.93f, f2 * 19.07f, f2 * 9.88f, f2 * 14.12f, paint);
                canvas.drawLine(f2 * 14.12f, f2 * 9.88f, f2 * 19.07f, f2 * 4.93f, paint);
                break;
            case "speed":
                canvas.drawArc(new RectF(f2 * 3.0f, f2 * 5.0f, f2 * 21.0f, 23.0f * f2), 200.0f, 140.0f, false, paint);
                canvas.drawLine(f2 * 12.0f, f2 * 14.0f, f2 * 16.5f, f2 * 9.0f, paint);
                break;
            case "doors":
                canvas.drawRoundRect(f2 * 6.0f, f2 * 2.0f, f2 * 18.0f, f2 * 22.0f, f2 * 3.0f, f2 * 3.0f, paint);
                canvas.drawLine(f2 * 6.0f, f2 * 8.0f, f2 * 2.0f, f2 * 6.0f, paint);
                canvas.drawLine(f2 * 18.0f, f2 * 8.0f, f2 * 22.0f, f2 * 6.0f, paint);
                canvas.drawLine(f2 * 6.0f, f2 * 16.0f, f2 * 2.0f, f2 * 14.0f, paint);
                canvas.drawLine(f2 * 18.0f, f2 * 16.0f, f2 * 22.0f, f2 * 14.0f, paint);
                break;
            case "lights":
                canvas.drawLine(f2 * 2.0f, f2 * 8.0f, f2 * 8.0f, f2 * 8.0f, paint);
                canvas.drawLine(f2 * 2.0f, f2 * 12.0f, f2 * 9.0f, f2 * 12.0f, paint);
                canvas.drawLine(f2 * 2.0f, f2 * 16.0f, f2 * 8.0f, f2 * 16.0f, paint);
                canvas.drawLine(f2 * 13.0f, f2 * 5.0f, f2 * 13.0f, f2 * 19.0f, paint);
                Path path = new Path();
                path.moveTo(f2 * 13.0f, f2 * 5.0f);
                path.cubicTo(f2 * 20.0f, f2 * 7.0f, f2 * 20.0f, f2 * 17.0f, f2 * 13.0f, f2 * 19.0f);
                canvas.drawPath(path, paint);
                break;
            case "env":
                canvas.drawCircle(f2 * 12.0f, f2 * 12.0f, f2 * 9.0f, paint);
                canvas.drawLine(f2 * 3.0f, f2 * 12.0f, f2 * 21.0f, f2 * 12.0f, paint);
                Path path2 = new Path();
                path2.moveTo(f2 * 12.0f, f2 * 3.0f);
                path2.cubicTo(f2 * 16.0f, f2 * 7.0f, f2 * 16.0f, f2 * 17.0f, f2 * 12.0f, f2 * 21.0f);
                path2.moveTo(f2 * 12.0f, f2 * 3.0f);
                path2.cubicTo(f2 * 8.0f, f2 * 7.0f, f2 * 8.0f, f2 * 17.0f, f2 * 12.0f, f2 * 21.0f);
                canvas.drawPath(path2, paint);
                break;
            case "model":
                String str4 = str2 != null ? str2 : this.dockModelLabel;
                if (str4 == null || str4.isEmpty()) {
                    str4 = "PHEV34";
                }
                drawDockTextGlyph(canvas, paint, f, str4);
                break;
            case "perf":
                String str5 = str2 != null ? str2 : this.dockPerfLabel;
                if (str5 == null || str5.isEmpty()) {
                    str5 = "OFF";
                }
                drawDockTextGlyph(canvas, paint, f, str5);
                break;
            case "fps":
                drawDockTextGlyph(canvas, paint, f, "FPS");
                break;
            case "xray":
                canvas.drawRoundRect(f2 * 7.0f, f2 * 9.5f, f2 * 17.0f, f2 * 14.5f, 1.0f * f2, f2 * 1.0f, paint);
                canvas.drawCircle(f2 * 5.0f, f2 * 17.0f, 2.2f * f2, paint);
                canvas.drawCircle(f2 * 19.0f, f2 * 17.0f, 2.2f * f2, paint);
                canvas.drawLine(f2 * 3.0f, f2 * 12.0f, f2 * 5.0f, f2 * 12.0f, paint);
                canvas.drawLine(f2 * 19.0f, f2 * 12.0f, f2 * 21.0f, f2 * 12.0f, paint);
                break;
            case "time":
                String str6 = str2 != null ? str2 : this.dockTimeMode;
                if ("night".equals(str6)) {
                    paint.setStyle(Paint.Style.FILL);
                    Path path3 = new Path();
                    path3.addCircle(f2 * 12.0f, f2 * 12.0f, f2 * 8.0f, Path.Direction.CW);
                    Path path4 = new Path();
                    path4.addCircle(f2 * 16.0f, f2 * 9.0f, 6.5f * f2, Path.Direction.CW);
                    path3.op(path4, Path.Op.DIFFERENCE);
                    canvas.drawPath(path3, paint);
                } else if ("auto".equals(str6)) {
                    drawDockTextGlyph(canvas, paint, f, "AUTO");
                } else {
                    canvas.drawCircle(f2 * 12.0f, f2 * 12.0f, 4.2f * f2, paint);
                    for (int i2 = 0; i2 < 8; i2++) {
                        double d = (((double) i2) * 3.141592653589793d) / 4.0d;
                        canvas.drawLine((f2 * 12.0f) + (((float) Math.cos(d)) * 6.5f * f2), (f2 * 12.0f) + (((float) Math.sin(d)) * 6.5f * f2), (f2 * 12.0f) + (((float) Math.cos(d)) * 9.2f * f2), (f2 * 12.0f) + (((float) Math.sin(d)) * 9.2f * f2), paint);
                    }
                }
                break;
            case "save":
                Path path5 = new Path();
                path5.moveTo(f2 * 5.0f, f2 * 3.0f);
                path5.lineTo(f2 * 16.0f, f2 * 3.0f);
                path5.lineTo(f2 * 21.0f, f2 * 8.0f);
                path5.lineTo(f2 * 21.0f, f2 * 21.0f);
                path5.lineTo(f2 * 5.0f, f2 * 21.0f);
                path5.close();
                canvas.drawPath(path5, paint);
                canvas.drawRect(f2 * 8.0f, f2 * 13.0f, f2 * 16.0f, f2 * 21.0f, paint);
                canvas.drawRect(f2 * 8.0f, f2 * 3.0f, 15.0f * f2, f2 * 8.0f, paint);
                break;
            default:
                canvas.drawCircle(f2 * 12.0f, f2 * 12.0f, f2 * 6.0f, paint);
                break;
        }
        return new BitmapDrawable(getResources(), bitmapCreateBitmap);
    }

    private String dockToolGlyphFor(String str) {
        if ("model".equals(str)) {
            return this.dockModelLabel;
        }
        if ("perf".equals(str)) {
            return this.dockPerfLabel;
        }
        if ("paint".equals(str)) {
            return this.dockPaintHex;
        }
        if ("time".equals(str)) {
            return this.dockTimeMode;
        }
        if ("fps".equals(str)) {
            return this.dockFpsOn ? "ON" : "OFF";
        }
        if ("xray".equals(str)) {
            return this.dockXrayOn ? "ON" : "OFF";
        }
        return "";
    }

    private void drawDockTextGlyph(Canvas canvas, Paint paint, float f, String str) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(dockGlyphColor());
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif-medium", 0));
        paint.setTextSize(((str == null || str.length() <= 4) ? 0.34f : 0.28f) * f);
        paint.setStrokeWidth(0.0f);
        Paint.FontMetrics fontMetrics = paint.getFontMetrics();
        canvas.drawText(str, 0.5f * f, (f * 0.5f) - ((fontMetrics.ascent + fontMetrics.descent) * 0.5f), paint);
    }

    private int drivingWashColor(String str) {
        if (str == null) {
            return 0;
        }
        switch (str) {
            case "eco":
                return -11550870;
            case "normal":
                return -11894859;
            case "sport":
                return -2082516;
            case "snow":
                return -9455896;
            case "sand":
                return -2513328;
            case "mud":
                return -6655162;
            case "awd":
                return -10716524;
            case "hev":
                return -8620842;
            case "evp":
                return -11562022;
            case "ev":
                return -13256305;
            case "level1":
                return -9536877;
            case "level2":
                return -11761980;
            case "level3":
                return -12819520;
            case "onepedal":
                return -8630320;
            default:
                return 0;
        }
    }

    private void dropSplashFromShell() {
        endSplashOverlay();
        if (this.webView == null) {
            return;
        }
        this.webView.evaluateJavascript("(function(){try{if(window.HavalSplash&&window.HavalSplash.fadeOut)window.HavalSplash.fadeOut();}catch(e){}var e=document.getElementById('hv-splash');if(e){e.style.display='none';if(e.parentNode)e.parentNode.removeChild(e);}var v=document.getElementById('hv-splash-video');if(v){try{v.pause();}catch(e){}try{v.removeAttribute('src');v.src='';v.load();}catch(e){}if(v.parentNode)v.parentNode.removeChild(v);}var a=window.__app;if(!a)return;try{a._introWaitingForLoader=false;a._pendingIntro=false;}catch(e){}try{if(a._forceViewerLayout)a._forceViewerLayout();}catch(e){}try{if(a._setCarSceneVisible)a._setCarSceneVisible(true);}catch(e){}try{if(a._onResize)a._onResize();}catch(e){}try{if(a.requestRender)a.requestRender(30);}catch(e){}try{if(a.renderOnce)a.renderOnce();}catch(e){}try{if(a._startIntroAnimation&&!a._introPlayed)a._startIntroAnimation();}catch(e){}})()", null);
    }

    private String encodeIconDataUrl(Drawable drawable, int i, Integer num) {
        if (drawable == null || i <= 0) {
            return null;
        }
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(i, i, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        if (num != null) {
            Paint paint = new Paint(1);
            paint.setColor(num.intValue());
            float f = i * 0.22f;
            canvas.drawRoundRect(0.0f, 0.0f, i, i, f, f, paint);
            int iRound = Math.round(i * 0.16f);
            drawable.setBounds(iRound, iRound, i - iRound, i - iRound);
        } else {
            drawable.setBounds(0, 0, i, i);
        }
        drawable.draw(canvas);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmapCreateBitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        return "data:image/png;base64," + Base64.encodeToString(byteArrayOutputStream.toByteArray(), 2);
    }

    private void endSplashOverlay() {
        if (this.splashOverlayEnded) {
            return;
        }
        this.splashOverlayEnded = true;
        hideSplashSkip();
        if (this.webView != null) {
            this.webView.setBackgroundColor(getSharedPreferences("opengw_lancador", 0).getInt("shellBg", -16777216));
        }
    }

    private void ensureAppsFabBuilt() {
        if (this.appsFabRoot != null) {
            return;
        }
        float f = getResources().getDisplayMetrics().density;
        int iRound = Math.round(72.0f * f);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setGravity(1);
        linearLayout.setClipToPadding(false);
        linearLayout.setClipChildren(false);
        this.appsFabMenu = buildAppsFabMenu(f);
        this.appsFabMenu.setVisibility(8);
        linearLayout.addView(this.appsFabMenu);
        TextView textView = new TextView(this);
        textView.setText("☰");
        textView.setTextColor(-1379592);
        textView.setTextSize(26.0f);
        textView.setGravity(17);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(-351399398);
        gradientDrawable.setCornerRadius(iRound / 2.0f);
        gradientDrawable.setStroke(Math.max(1, Math.round(1.5f * f)), 872415231);
        textView.setBackground(gradientDrawable);
        textView.setElevation(8.0f * f);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(iRound, iRound);
        layoutParams.topMargin = Math.round(10.0f * f);
        textView.setLayoutParams(layoutParams);
        textView.setClickable(true);
        textView.setFocusable(true);
        this.appsFabButton = textView;
        linearLayout.addView(textView);
        textView.setOnTouchListener((view, motionEvent) -> {
            switch (motionEvent.getActionMasked()) {
                case 0:
                    this.appsFabDragging = false;
                    this.appsFabDownRawX = motionEvent.getRawX();
                    this.appsFabDownRawY = motionEvent.getRawY();
                    this.appsFabDownLpX = this.appsFabPosX;
                    this.appsFabDownLpY = this.appsFabPosY;
                    return true;
                case 1:
                case 3:
                    if (this.appsFabDragging) {
                        saveAppsFabPos();
                    } else if (motionEvent.getActionMasked() == 1) {
                        setAppsFabMenuOpen(!this.appsFabMenuOpen);
                    }
                    this.appsFabDragging = false;
                    return true;
                case 2:
                    float rawX = motionEvent.getRawX() - this.appsFabDownRawX;
                    float rawY = motionEvent.getRawY() - this.appsFabDownRawY;
                    if (!this.appsFabDragging && (rawX * rawX) + (rawY * rawY) > f * 8.0f * 8.0f * f) {
                        this.appsFabDragging = true;
                        if (this.appsFabMenuOpen) {
                            setAppsFabMenuOpen(false);
                        }
                    }
                    if (this.appsFabDragging) {
                        this.appsFabPosX = this.appsFabDownLpX + Math.round(rawX);
                        this.appsFabPosY = this.appsFabDownLpY + Math.round(rawY);
                        clampAppsFabPosition();
                        applyAppsFabPosition();
                    }
                    return true;
                default:
                    return false;
            }
        });
        if (this.appsFabPosX == Integer.MIN_VALUE) {
            loadAppsFabPos();
        }
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        if (this.appsFabPosX == Integer.MIN_VALUE || this.appsFabPosY == Integer.MIN_VALUE) {
            this.appsFabPosX = Math.max(0, (displayMetrics.widthPixels - iRound) / 2);
            this.appsFabPosY = Math.max(0, (displayMetrics.heightPixels - iRound) - Math.round(24.0f * f));
        }
        clampAppsFabPosition();
        this.appsFabLp = buildAppsFabWindowLp();
        this.appsFabRoot = linearLayout;
    }

    private boolean ensureAppsFabOverlay() {
        ensureAppsFabBuilt();
        return this.appsFabRoot != null;
    }

    private void ensureAppsFabScrimBuilt() {
        if (this.appsFabScrim != null) {
            return;
        }
        this.appsFabScrim = new View(this);
        this.appsFabScrim.setBackgroundColor(0);
        this.appsFabScrim.setClickable(true);
        this.appsFabScrim.setFocusable(true);
        this.appsFabScrim.setOnClickListener(view -> {
            setAppsFabMenuOpen(false);
        });
        this.appsFabScrimLp = new WindowManager.LayoutParams(-1, -1, Build.VERSION.SDK_INT >= 26 ? 2038 : 2002, 16777480, -3);
        this.appsFabScrimLp.gravity = 8388659;
    }

    private void ensureFreeformSettings() {
        try {
            Settings.Global.putInt(getContentResolver(), "enable_freeform_support", 1);
        } catch (SecurityException e) {
            Log.i("H6Viewer", "Cannot write enable_freeform_support (need shell / WRITE_SECURE_SETTINGS)");
        }
        try {
            Settings.Global.putInt(getContentResolver(), "force_resizable_activities", 1);
        } catch (SecurityException e2) {
        }
    }

    private void ensureMediaVisualizer() {
        if (checkSelfPermission("android.permission.RECORD_AUDIO") == 0) {
            startMediaVisualizerIfNeeded();
        } else {
            if (this.mediaVizPermissionLogged) {
                return;
            }
            this.mediaVizPermissionLogged = true;
            Log.w("H6Viz", "no RECORD_AUDIO grant — synthetic bars only");
        }
    }

    private void ensurePlaceLocationPermission() {
        if (this.placeLocationAsked) {
            return;
        }
        this.placeLocationAsked = true;
        if (checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") == 0 || checkSelfPermission("android.permission.ACCESS_COARSE_LOCATION") == 0) {
            return;
        }
        try {
            requestPermissions(new String[]{"android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"}, 7102);
        } catch (Throwable th) {
            Log.w("H6Viewer", "location permission request failed", th);
        }
    }

    private void enterImmersiveMode() {
        int i = laidOutFullBleed() ? 4354 | 1536 : 4354;
        if ("full".equals(this.uiMode)) {
            i |= 4;
        }
        getWindow().getDecorView().setSystemUiVisibility(i);
    }

    private boolean excludedFromAppRow(String str) {
        return str.equals(getPackageName()) || IGNORED_PACKAGES.contains(str) || isPinnedPackage(str) || ProjectionPresence.isProjectionPackage(str);
    }

    private TextView findDockChipLabel(View view) {
        if (view == null) {
            return null;
        }
        View viewFindViewWithTag = view.findViewWithTag("dockChipLabel");
        if (viewFindViewWithTag instanceof TextView) {
            return (TextView) viewFindViewWithTag;
        }
        return null;
    }

    private TextView findModeCellLabel(View view) {
        if (view == null) {
            return null;
        }
        View viewFindViewWithTag = view.findViewWithTag("modeLabel");
        if (viewFindViewWithTag instanceof TextView) {
            return (TextView) viewFindViewWithTag;
        }
        return null;
    }

    private int findTaskIdForPackage(String str) {
        if (str == null || str.isEmpty()) {
            return -1;
        }
        try {
            ActivityManager activityManager = (ActivityManager) getSystemService("activity");
            if (activityManager == null) {
                return -1;
            }
            List<ActivityManager.RunningTaskInfo> runningTasks = activityManager.getRunningTasks(32);
            if (runningTasks != null) {
                Iterator<ActivityManager.RunningTaskInfo> it = runningTasks.iterator();
                while (it.hasNext()) {
                    int iTaskIdIfPackage = taskIdIfPackage(it.next(), str);
                    if (iTaskIdIfPackage >= 0) {
                        return iTaskIdIfPackage;
                    }
                }
            }
            List<ActivityManager.RecentTaskInfo> recentTasks = activityManager.getRecentTasks(32, 1);
            if (recentTasks != null) {
                Iterator<ActivityManager.RecentTaskInfo> it2 = recentTasks.iterator();
                while (it2.hasNext()) {
                    int iRecentTaskIdIfPackage = recentTaskIdIfPackage(it2.next(), str);
                    if (iRecentTaskIdIfPackage >= 0) {
                        return iRecentTaskIdIfPackage;
                    }
                }
            }
            int iFindTaskIdViaSystemService = findTaskIdViaSystemService(str);
            if (iFindTaskIdViaSystemService >= 0) {
                return iFindTaskIdViaSystemService;
            }
        } catch (Exception e) {
            Log.w("H6Viewer", "findTaskIdForPackage failed", e);
        }
        return -1;
    }

    private int findTaskIdViaSystemService(String str) {
        int iTaskIdIfPackage;
        List<?> listSystemTaskList = systemTaskList();
        if (listSystemTaskList == null) {
            return -1;
        }
        for (Object obj : listSystemTaskList) {
            if ((obj instanceof ActivityManager.RunningTaskInfo) && (iTaskIdIfPackage = taskIdIfPackage((ActivityManager.RunningTaskInfo) obj, str)) >= 0) {
                return iTaskIdIfPackage;
            }
        }
        return -1;
    }

    private Drawable flyoutIconForPinned(String str) {
        Drawable drawableDrawableFor;
        if ("com.beantechs.energyassistant".equalsIgnoreCase(str)) {
            try {
                Drawable drawable = getDrawable(R.drawable.ic_energy_assistant);
                if (drawable != null) {
                    return drawable;
                }
            } catch (Exception e) {
            }
        }
        if ("com.beantechs.settings".equals(str) && (drawableDrawableFor = DockAppOverrides.drawableFor(this, "settings", "#FFFFFF")) != null) {
            return drawableDrawableFor;
        }
        try {
            Drawable drawable2 = getDrawable(R.drawable.ic_gwm_emblem);
            if (drawable2 != null) {
                return drawable2;
            }
        } catch (Exception e2) {
        }
        try {
            return getDrawable(R.drawable.ic_gwm_viewer);
        } catch (Exception e3) {
            return null;
        }
    }

    private void forceStopPackage(String str) {
        if (str == null || str.isEmpty()) {
            return;
        }
        try {
            ActivityManager activityManager = (ActivityManager) getSystemService("activity");
            if (activityManager != null) {
                activityManager.killBackgroundProcesses(str);
            }
        } catch (Exception e) {
        }
        try {
            new ProcessBuilder("am", "force-stop", str).redirectErrorStream(true).start();
        } catch (Exception e2) {
            Log.w("H6Viewer", "force-stop failed for " + str, e2);
        }
    }

    private Drawable glyphForModeCell(View view, int i, boolean z) {
        int iDockGlyphColor = dockGlyphColor(z);
        if (view == this.modeAppsBtn) {
            return dockGlyphApps(i, iDockGlyphColor);
        }
        if (view == this.modeLayoutBtn) {
            return dockGlyphLayout(i, iDockGlyphColor);
        }
        if (view == this.modeConfigBtn) {
            return dockGlyphConfig(i, iDockGlyphColor);
        }
        if (view == this.modeSurfaceBtn) {
            return "cards".equals(this.dockSurfaceMode) ? dockGlyphApps(i, iDockGlyphColor) : dockGlyphCards(i, iDockGlyphColor);
        }
        if (view == this.modeCollapsedBtn) {
            return dockGlyphCards(i, iDockGlyphColor);
        }
        return null;
    }

    private Drawable glyphForStripMode(String str) {
        int iRound = this.dockIconPx > 0 ? this.dockIconPx : Math.round(getResources().getDisplayMetrics().density * 60.0f);
        int iDockGlyphColor = dockGlyphColor(true);
        if ("layout".equals(str)) {
            return dockGlyphLayout(iRound, iDockGlyphColor);
        }
        return "config".equals(str) ? dockGlyphConfig(iRound, iDockGlyphColor) : dockGlyphApps(iRound, iDockGlyphColor);
    }

    private boolean hasOverlayWindow() {
        return ((this.activePopupPackage == null || this.activePopupPackage.isEmpty()) && (this.activeMediaPackage == null || this.activeMediaPackage.isEmpty())) ? false : true;
    }

    private void hideBootHud() {
        this.bootHudCenterMode = false;
        if (this.bootProgressTrack != null) {
            this.bootProgressTrack.setVisibility(8);
        }
        if (this.bootCenterLoader != null) {
            this.bootCenterLoader.setVisibility(8);
        }
    }

    private void hideGwmHub() {
        for (String str : PINNED_PACKAGES) {
            this.hiddenPackages.add(str);
        }
        saveHiddenApps();
        updatePinnedVisibility();
        refreshRecentSlots();
        notifyAppsCatalogChanged();
    }

    private void hideRecentDuplicatesFromStrip(Set<String> set) {
        for (Map.Entry<String, MotionTrailLayout> entry : this.dockItemsByPackage.entrySet()) {
            MotionTrailLayout value = entry.getValue();
            if (value != null) {
                if (isUserHidden(entry.getKey()) || (set != null && set.contains(entry.getKey()))) {
                    value.setVisibility(8);
                } else {
                    value.setVisibility(0);
                    if (this.launcherRevealed) {
                        value.setAlpha(1.0f);
                        value.setTranslationX(0.0f);
                        value.setTrailPx(0.0f);
                    }
                }
            }
        }
    }

    private void hideSplashSkip() {
        if (this.splashSkipBtn != null) {
            this.splashSkipBtn.setVisibility(8);
        }
    }

    private Bitmap iconBitmapForPackage(String str) {
        if (str == null || str.isEmpty()) {
            return null;
        }
        Bitmap bitmap = this.packageIconBitmaps.get(str);
        if (bitmap != null) {
            return bitmap;
        }
        if (this.packageIconMiss.contains(str)) {
            return null;
        }
        Bitmap bitmapProjectionIconBitmap = projectionIconBitmap(str);
        if (bitmapProjectionIconBitmap != null) {
            this.packageIconBitmaps.put(str, bitmapProjectionIconBitmap);
            return bitmapProjectionIconBitmap;
        }
        try {
            Drawable drawableNormalizeAdaptiveIcon = normalizeAdaptiveIcon(getPackageManager().getApplicationIcon(str), 96);
            if (drawableNormalizeAdaptiveIcon != null) {
                int iMax = Math.max(1, drawableNormalizeAdaptiveIcon.getIntrinsicWidth());
                int iMax2 = Math.max(1, drawableNormalizeAdaptiveIcon.getIntrinsicHeight());
                if (iMax > 128 || iMax2 > 128 || iMax <= 0 || iMax2 <= 0) {
                    iMax = 96;
                    iMax2 = 96;
                }
                Bitmap bitmapCreateBitmap = Bitmap.createBitmap(iMax, iMax2, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmapCreateBitmap);
                drawableNormalizeAdaptiveIcon.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawableNormalizeAdaptiveIcon.draw(canvas);
                this.packageIconBitmaps.put(str, bitmapCreateBitmap);
                return bitmapCreateBitmap;
            }
        } catch (Exception e) {
        }
        if (ProjectionPresence.isProjectionPackage(str) && !str.toLowerCase().contains("carplay")) {
            try {
                Drawable drawable = getDrawable(R.drawable.ic_android_auto);
                if (drawable != null) {
                    Bitmap bitmapCreateBitmap2 = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
                    Canvas canvas2 = new Canvas(bitmapCreateBitmap2);
                    drawable.setBounds(0, 0, 96, 96);
                    drawable.draw(canvas2);
                    this.packageIconBitmaps.put(str, bitmapCreateBitmap2);
                    return bitmapCreateBitmap2;
                }
            } catch (Exception e2) {
            }
        }
        this.packageIconMiss.add(str);
        return null;
    }

    private Drawable iconForLauncherApp(PackageManager packageManager, ResolveInfo resolveInfo) {
        if (resolveInfo == null || resolveInfo.activityInfo == null) {
            return null;
        }
        Drawable drawableLauncherIconForPackage = launcherIconForPackage(resolveInfo.activityInfo.packageName);
        return drawableLauncherIconForPackage != null ? drawableLauncherIconForPackage : resolveInfo.loadIcon(packageManager);
    }

    private static boolean isAndroidAutoMediaSource(String str, String str2) {
        if (str2 != null && str2.equalsIgnoreCase("ANDROID AUTO")) {
            return true;
        }
        if (str == null || str.isEmpty()) {
            return false;
        }
        String lowerCase = str.toLowerCase(Locale.US);
        return lowerCase.contains("androidauto") || lowerCase.contains("projection.gearhead");
    }

    private static boolean isCarPlayMediaSource(String str, String str2) {
        if (str2 != null) {
            String strTrim = str2.trim();
            if (strTrim.equalsIgnoreCase("CARPLAY") || strTrim.equalsIgnoreCase("CarPlay")) {
                return true;
            }
        }
        if (str == null || str.isEmpty()) {
            return false;
        }
        return str.toLowerCase(Locale.US).contains("carplay");
    }

    private boolean isCardActionCommand(String str) {
        return str != null && str.matches("cardAction:[A-Za-z0-9_]{1,32}:(popup|new|desktop:[A-Za-z0-9_-]{1,64})");
    }

    private boolean isDebuggableBuild() {
        return (getApplicationInfo().flags & 2) != 0;
    }

    private boolean isDrivingSetCommand(String str) {
        String strSubstring;
        int iIndexOf;
        if (!str.startsWith("drivingSet:") || (iIndexOf = (strSubstring = str.substring("drivingSet:".length())).indexOf(58)) <= 0 || iIndexOf >= strSubstring.length() - 1) {
            return false;
        }
        for (int i = 0; i < strSubstring.length(); i++) {
            char cCharAt = strSubstring.charAt(i);
            if (!(cCharAt == ':' || cCharAt == '_' || (cCharAt >= '0' && cCharAt <= '9') || ((cCharAt >= 'a' && cCharAt <= 'z') || (cCharAt >= 'A' && cCharAt <= 'Z')))) {
                return false;
            }
        }
        return true;
    }

    private boolean isEmulatorDevice() {
        if (this.emulatorDevice != null) {
            return this.emulatorDevice.booleanValue();
        }
        String str = Build.FINGERPRINT != null ? Build.FINGERPRINT : "";
        String str2 = Build.MODEL != null ? Build.MODEL : "";
        String str3 = Build.PRODUCT != null ? Build.PRODUCT : "";
        String str4 = Build.HARDWARE != null ? Build.HARDWARE : "";
        this.emulatorDevice = Boolean.valueOf(str.startsWith("generic") || str.contains("emulator") || str2.contains("sdk_gphone") || str2.contains("Android SDK built for") || str3.contains("sdk") || str3.contains("emulator") || str4.contains("goldfish") || str4.contains("ranchu"));
        return this.emulatorDevice.booleanValue();
    }

    private boolean isGwmApp(String str) {
        if (str == null || str.isEmpty() || str.equals(getPackageName()) || str.equals("br.com.opengw") || ProjectionPresence.isProjectionPackage(str)) {
            return false;
        }
        String lowerCase = str.toLowerCase();
        return lowerCase.contains("beantech") || lowerCase.contains("autolink") || lowerCase.startsWith("com.gwm") || lowerCase.contains(".gwm.");
    }

    private boolean isOverlayLaunching() {
        return SystemClock.uptimeMillis() < this.overlayLaunchUntilMs;
    }

    private boolean isPinnedPackage(String str) {
        if (str == null) {
            return false;
        }
        for (String str2 : PINNED_PACKAGES) {
            if (str2.equals(str)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isProbablyEmulator() {
        String lowerCase = String.valueOf(Build.FINGERPRINT).toLowerCase(Locale.US);
        String lowerCase2 = String.valueOf(Build.MODEL).toLowerCase(Locale.US);
        String lowerCase3 = String.valueOf(Build.PRODUCT).toLowerCase(Locale.US);
        return lowerCase.contains("generic") || lowerCase.contains("emulator") || lowerCase.contains("unknown") || lowerCase2.contains("sdk_gphone") || lowerCase2.contains("emulator") || lowerCase2.contains("android sdk built for") || lowerCase3.contains("sdk") || lowerCase3.contains("emulator") || lowerCase3.contains("simulator");
    }

    private boolean isUserHidden(String str) {
        return str != null && this.hiddenPackages.contains(str);
    }

    private static boolean isValidSplitRatio(String str) {
        return "1:1".equals(str) || "2:1".equals(str) || "1:2".equals(str);
    }

    private void keepOverlayTasksOnTop() {
        if (this.chromeOnTop || !hasOverlayWindow()) {
            return;
        }
        long jUptimeMillis = SystemClock.uptimeMillis();
        if (jUptimeMillis - this.lastOverlayRaiseMs < 250) {
            return;
        }
        this.lastOverlayRaiseMs = jUptimeMillis;
        pinOverlayAlwaysOnTop(this.activePopupPackage, this.activePopupTaskId, false);
        pinOverlayAlwaysOnTop(this.activeMediaPackage, this.activeMediaTaskId, true);
    }

    private String labelTirePair(String str, String str2, String str3) {
        if (str3 == null || str3.isEmpty()) {
            return "";
        }
        String[] strArrSplit = str3.split("\\s*[·/]\\s*", 2);
        return strArrSplit.length != 2 ? str3 : str + " " + strArrSplit[0] + "   " + str2 + " " + strArrSplit[1];
    }

    private boolean laidOutFullBleed() {
        return !"inset".equals(this.uiMode);
    }

    private View launchAnchorForBounds(Rect rect) {
        return (rect == null || rect.left < displayRect().width() / 2) ? this.launchAnchor : this.mediaLaunchAnchor;
    }

    private void launchAndroidSettingsRoot() {
        try {
            if (this.pinBoundsRunnable != null) {
                this.mainHandler.removeCallbacks(this.pinBoundsRunnable);
            }
            if (this.pinMediaBoundsRunnable != null) {
                this.mainHandler.removeCallbacks(this.pinMediaBoundsRunnable);
            }
            Intent intent = new Intent("android.intent.action.MAIN");
            intent.addCategory("android.intent.category.LAUNCHER");
            intent.setComponent(new ComponentName("com.android.settings", "com.android.settings.Settings"));
            intent.setPackage("com.android.settings");
            intent.addFlags(337674240);
            Rect rectTelaCheia = fullscreenAppBounds("com.android.settings");
            Bundle bundleBuildWindowOptions = buildWindowOptions(1, rectTelaCheia);
            Log.w("H6Viewer", "Fullscreen Android Settings root " + rectTelaCheia);
            startActivity(intent, bundleBuildWindowOptions);
            requestTaskBounds("com.android.settings", rectTelaCheia);
            notifyViewerShellLayout();
        } catch (Exception e) {
            Log.e("H6Viewer", "Error launching Android Settings root", e);
            launchAppFullscreen("com.android.settings");
        }
    }

    private void launchAppForPackage(String str, String str2) {
        if (str == null || str.isEmpty()) {
            return;
        }
        if (ProjectionPresence.isProjectionPackage(str)) {
            this.mainHandler.post(() -> {
                launchProjection(ProjectionPresence.kindForPackage(str));
            });
            return;
        }
        if (this.emulatorStubPackages.contains(str)) {
            Toast.makeText(this, ((str2 == null || str2.isEmpty()) ? str : str2) + " (emulator stub)", 0).show();
            return;
        }
        rememberRecentApp(str);
        if ("com.android.car.settings".equals(str)) {
            launchAndroidSettingsRoot();
            return;
        }
        if (isGwmApp(str) || isPinnedPackage(str)) {
            launchAppFullscreen(str);
            return;
        }
        if (!shouldLaunchAsWindow(str)) {
            launchAppFullscreen(str);
            return;
        }
        if (str.equals(this.activePopupPackage)) {
            dismissPopup(str);
            return;
        }
        if (str.equals(this.activeMediaPackage)) {
            dismissMediaPopup(str);
            return;
        }
        String strResolveLaunchSide = resolveLaunchSide();
        if (strResolveLaunchSide == null || strResolveLaunchSide.isEmpty()) {
            Log.w("H6Viewer", "No APP slot for " + str + ": mode=" + this.shellMode + " leftUse=" + this.leftSlotUse + " rightUse=" + this.rightSlotUse + " leftApp=" + this.activePopupPackage + " rightApp=" + this.activeMediaPackage);
            return;
        }
        applySlotUse(strResolveLaunchSide, "app");
        if ("right".equals(strResolveLaunchSide)) {
            launchAppInRightSlot(str);
        } else {
            launchAppInLeftSlot(str);
        }
    }

    private void launchAppFullscreen(String str) {
        Intent launchIntentForPackage;
        if (str != null) {
            try {
                if (!str.isEmpty() && (launchIntentForPackage = getPackageManager().getLaunchIntentForPackage(str)) != null && launchIntentForPackage.getComponent() != null) {
                    if (this.pinBoundsRunnable != null) {
                        this.mainHandler.removeCallbacks(this.pinBoundsRunnable);
                    }
                    if (this.pinMediaBoundsRunnable != null) {
                        this.mainHandler.removeCallbacks(this.pinMediaBoundsRunnable);
                    }
                    if (str.equals(this.activePopupPackage)) {
                        this.activePopupPackage = "";
                        this.activePopupTaskId = -1;
                        this.activePopupComponent = null;
                    }
                    if (str.equals(this.activeMediaPackage)) {
                        this.activeMediaPackage = "";
                        this.activeMediaTaskId = -1;
                        this.activeMediaComponent = null;
                    }
                    Intent intent = new Intent("android.intent.action.MAIN");
                    intent.addCategory("android.intent.category.LAUNCHER");
                    intent.setComponent(launchIntentForPackage.getComponent());
                    intent.setPackage(str);
                    intent.addFlags(337641472);
                    Rect rectTelaCheia = fullscreenAppBounds(str);
                    Bundle bundleBuildWindowOptions = buildWindowOptions(1, rectTelaCheia);
                    Log.w("H6Viewer", "Fullscreen launch " + launchIntentForPackage.getComponent() + " " + rectTelaCheia);
                    startActivity(intent, bundleBuildWindowOptions);
                    requestTaskBounds(str, rectTelaCheia);
                    notifyViewerShellLayout();
                }
            } catch (Exception e) {
                Log.e("H6Viewer", "Error launching fullscreen " + str, e);
            }
        }
    }

    private void launchAppInLeftSlot(String activePopupPackage) {
        Intent launchIntentForPackage;
        if (activePopupPackage != null) {
            try {
                if (!activePopupPackage.isEmpty() && (launchIntentForPackage = getPackageManager().getLaunchIntentForPackage(activePopupPackage)) != null && launchIntentForPackage.getComponent() != null) {
                    ensureFreeformSettings();
                    if (this.activePopupPackage != null && this.activePopupPackage.equals(activePopupPackage)) {
                        dismissPopup(activePopupPackage);
                        return;
                    }
                    if (this.activePopupPackage != null && !this.activePopupPackage.isEmpty()) {
                        dismissPopup(this.activePopupPackage);
                    }
                    closeExistingTasksForPackage(activePopupPackage);
                    this.activePopupPackage = activePopupPackage;
                    this.activePopupTaskId = -1;
                    this.activePopupComponent = launchIntentForPackage.getComponent();
                    markOverlayLaunch();
                    notifyViewerShellLayout();
                    Intent intent = new Intent("android.intent.action.MAIN");
                    intent.addCategory("android.intent.category.LAUNCHER");
                    intent.setComponent(launchIntentForPackage.getComponent());
                    intent.setPackage(activePopupPackage);
                    intent.addFlags(268582912);
                    Bundle bundleBuildWindowOptions = buildWindowOptions(5, leftFreeformBounds());
                    Log.w("H6Viewer", "Freeform launch " + launchIntentForPackage.getComponent() + " bounds=" + leftFreeformBounds() + " windowingMode=" + bundleBuildWindowOptions.getInt("android.activity.windowingMode", -1));
                    startActivity(intent, bundleBuildWindowOptions);
                    schedulePinPopupBounds(activePopupPackage, launchIntentForPackage.getComponent(), leftFreeformBounds(), false);
                    this.mainHandler.postDelayed(() -> {
                        requestTaskId(activePopupPackage, false);
                    }, 700L);
                    final Rect rect4 = leftFreeformBounds();
                    this.mainHandler.postDelayed(() -> requestTaskBounds(activePopupPackage, rect4), 1200L);
                }
            } catch (Exception e) {
                Log.e("H6Viewer", "Error launching app in left slot " + activePopupPackage, e);
            }
        }
    }

    private void launchAppInRightSlot(String activeMediaPackage) {
        Intent launchIntentForPackage;
        if (activeMediaPackage != null) {
            try {
                if (!activeMediaPackage.isEmpty() && (launchIntentForPackage = getPackageManager().getLaunchIntentForPackage(activeMediaPackage)) != null && launchIntentForPackage.getComponent() != null) {
                    ensureFreeformSettings();
                    if (activeMediaPackage.equals(this.activeMediaPackage)) {
                        dismissMediaPopup(activeMediaPackage);
                        return;
                    }
                    if (this.activeMediaPackage != null && !this.activeMediaPackage.isEmpty()) {
                        dismissMediaPopup(this.activeMediaPackage);
                    }
                    closeExistingTasksForPackage(activeMediaPackage);
                    this.activeMediaPackage = activeMediaPackage;
                    this.activeMediaTaskId = -1;
                    this.activeMediaComponent = launchIntentForPackage.getComponent();
                    markOverlayLaunch();
                    notifyViewerShellLayout();
                    Intent intent = new Intent("android.intent.action.MAIN");
                    intent.addCategory("android.intent.category.LAUNCHER");
                    intent.setComponent(launchIntentForPackage.getComponent());
                    intent.setPackage(activeMediaPackage);
                    intent.addFlags(268582912);
                    Bundle bundleBuildWindowOptions = buildWindowOptions(5, rightFreeformBounds());
                    Log.w("H6Viewer", "Media freeform launch " + launchIntentForPackage.getComponent() + " bounds=" + rightFreeformBounds());
                    startActivity(intent, bundleBuildWindowOptions);
                    schedulePinPopupBounds(activeMediaPackage, launchIntentForPackage.getComponent(), rightFreeformBounds(), true);
                    this.mainHandler.postDelayed(() -> {
                        requestTaskId(activeMediaPackage, true);
                    }, 700L);
                    final Rect rect5 = rightFreeformBounds();
                    this.mainHandler.postDelayed(() -> requestTaskBounds(activeMediaPackage, rect5), 1200L);
                }
            } catch (Exception e) {
                Log.e("H6Viewer", "Error launching app in right slot " + activeMediaPackage, e);
            }
        }
    }

    private void launchAppInSlotNative(String str, String str2) {
        if (str == null || str.isEmpty()) {
            return;
        }
        if ("com.android.car.settings".equals(str)) {
            launchAndroidSettingsRoot();
            return;
        }
        rememberRecentApp(str);
        if ("right".equals(str2)) {
            applySlotUse("right", "app");
            launchAppInRightSlot(str);
        } else {
            applySlotUse("left", "app");
            launchAppInLeftSlot(str);
        }
    }

    private void launchProjection(ProjectionPresence.Kind kind) {
        if (kind == null || kind == ProjectionPresence.Kind.NONE || this.projectionPresence == null) {
            return;
        }
        if (this.pinBoundsRunnable != null) {
            this.mainHandler.removeCallbacks(this.pinBoundsRunnable);
        }
        if (this.pinMediaBoundsRunnable != null) {
            this.mainHandler.removeCallbacks(this.pinMediaBoundsRunnable);
        }
        for (String str : ProjectionPresence.displayPackagesFor(kind)) {
            int iFindTaskIdForPackage = findTaskIdForPackage(str);
            Log.w("H6Viewer", "Projection lookup " + kind + " pkg=" + str + " task=" + iFindTaskIdForPackage);
            if (iFindTaskIdForPackage >= 0) {
                Log.w("H6Viewer", "Projection raise " + kind + " task=" + iFindTaskIdForPackage + " pkg=" + str);
                if (moveTaskToFrontNoAnim(iFindTaskIdForPackage)) {
                    requestTaskBounds(str, FULLSCREEN_BOUNDS);
                    notifyViewerShellLayout();
                    return;
                }
            }
        }
        beginProjectionResolve(kind);
    }

    private int launcherBottomGapPx() {
        return Math.round(60.0f * getResources().getDisplayMetrics().density);
    }

    private Drawable launcherIconForPackage(String str) {
        if (str == null || str.isEmpty()) {
            return null;
        }
        if ("com.beantechs.energyassistant".equalsIgnoreCase(str)) {
            try {
                Drawable drawable = getDrawable(R.drawable.ic_energy_assistant);
                if (drawable != null) {
                    return drawable;
                }
            } catch (Exception e) {
            }
        } else if (usesGwmEmblem(str)) {
            try {
                Drawable drawable2 = getDrawable(R.drawable.ic_gwm_viewer);
                if (drawable2 != null) {
                    return drawable2;
                }
            } catch (Exception e2) {
            }
        }
        return null;
    }

    private static float launcherIconInsetFrac() {
        return 0.0f;
    }

    private String launcherLabel(PackageManager packageManager, ResolveInfo resolveInfo) {
        if (resolveInfo == null || resolveInfo.activityInfo == null) {
            return "";
        }
        if ("com.beantechs.energyassistant".equalsIgnoreCase(resolveInfo.activityInfo.packageName)) {
            return "Energy Assistant";
        }
        CharSequence charSequenceLoadLabel = resolveInfo.loadLabel(packageManager);
        return charSequenceLoadLabel != null ? charSequenceLoadLabel.toString() : "";
    }

    private int launcherStripHeightPx() {
        return Math.round(getResources().getDisplayMetrics().density * 148.0f);
    }

    private void layoutBootHud(String str) {
        this.bootHudCenterMode = "center".equals(str);
        if (this.bootHudCenterMode) {
            if (this.bootProgressTrack != null) {
                this.bootProgressTrack.setVisibility(8);
            }
            if (this.bootCenterLoader != null) {
                this.bootCenterLoader.setVisibility(0);
                this.bootCenterLoader.bringToFront();
                return;
            }
            return;
        }
        if (this.bootCenterLoader != null) {
            this.bootCenterLoader.setVisibility(8);
        }
        if (this.bootProgressTrack == null || this.bootProgressTrack.getVisibility() != 0) {
            return;
        }
        this.bootProgressTrack.bringToFront();
    }

    private void layoutBootProgressLine() {
        if (this.bootProgressTrack == null) {
            return;
        }
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) this.bootProgressTrack.getLayoutParams();
        int iBootProgressLineTopMarginPx = bootProgressLineTopMarginPx();
        if (layoutParams.topMargin == iBootProgressLineTopMarginPx && layoutParams.gravity == 8388659) {
            return;
        }
        layoutParams.gravity = 8388659;
        layoutParams.topMargin = iBootProgressLineTopMarginPx;
        layoutParams.bottomMargin = 0;
        this.bootProgressTrack.setLayoutParams(layoutParams);
    }

    private Rect leftFreeformBounds() {
        Rect rectUsableDisplayRect = usableDisplayRect();
        int iSlotBandTopPx = slotBandTopPx();
        int iSlotBandBottomPx = slotBandBottomPx();
        if (!"appsOnly".equals(this.shellMode)) {
            return "appCar".equals(this.shellMode) ? clampSlot(new Rect(rectUsableDisplayRect.left + Math.round(rectUsableDisplayRect.width() * 0.016f), iSlotBandTopPx, rectUsableDisplayRect.left + Math.round(rectUsableDisplayRect.width() * 0.54f), iSlotBandBottomPx), rectUsableDisplayRect) : clampSlot(scaleToUsable(LEFT_POPUP_BOUNDS, rectUsableDisplayRect), rectUsableDisplayRect);
        }
        return clampSlot(new Rect(rectUsableDisplayRect.left + Math.max(6, Math.round(rectUsableDisplayRect.width() * 0.004f)), iSlotBandTopPx, splitDividerX(rectUsableDisplayRect) - (Math.max(6, Math.round(rectUsableDisplayRect.width() * 0.004f)) / 2), iSlotBandBottomPx), rectUsableDisplayRect);
    }

    private void loadAppsFabPos() {
        SharedPreferences sharedPreferences = getSharedPreferences("opengw_lancador", 0);
        this.appsFabPosX = sharedPreferences.getInt("fabX", Integer.MIN_VALUE);
        this.appsFabPosY = sharedPreferences.getInt("fabY", Integer.MIN_VALUE);
    }

    private void loadHiddenApps() {
        this.hiddenPackages.clear();
        String string = getSharedPreferences("opengw_lancador", 0).getString("hidden_apps", "");
        if (string == null || string.isEmpty()) {
            return;
        }
        for (String str : string.split(",")) {
            if (str != null) {
                String strTrim = str.trim();
                if (!strTrim.isEmpty()) {
                    this.hiddenPackages.add(strTrim);
                }
            }
        }
    }

    private void loadRecentApps() {
        this.recentPackages.clear();
        String string = getSharedPreferences("opengw_lancador", 0).getString("recent_apps", "");
        if (string == null || string.isEmpty()) {
            return;
        }
        for (String str : string.split(",")) {
            if (str != null) {
                String strTrim = str.trim();
                if (!strTrim.isEmpty() && !this.recentPackages.contains(strTrim)) {
                    this.recentPackages.add(strTrim);
                }
            }
        }
    }

    private void loadShellPrefs() {
        SharedPreferences sharedPreferences = getSharedPreferences("opengw_lancador", 0);
        String string = sharedPreferences.getString("mode", "appCar");
        if (!"appsOnly".equals(string)) {
            string = "appCar";
        }
        this.lastNonAppsShellMode = "appCar";
        this.shellMode = string;
        this.launchSidePref = sharedPreferences.getString("launchSide", "auto");
        this.nextLaunchLeft = sharedPreferences.getBoolean("nextLeft", true);
        String string2 = sharedPreferences.getString("splitRatio", "1:1");
        this.splitRatio = isValidSplitRatio(string2) ? string2 : "1:1";
        this.pendingRestoreLeft = sharedPreferences.getString("leftApp", "");
        this.pendingRestoreRight = sharedPreferences.getString("rightApp", "");
        this.dockSurfaceMode = normalizeDockSurfaceMode(sharedPreferences.getString("dockSurfaceMode", "launcher"));
        this.appsOnlyRestoreDone = false;
        loadAppsFabPos();
        this.uiMode = readUiModePref();
        loadSlotUsesForMode();
        refreshDockSurfaceUi(false);
    }

    private void loadSlotUsesForMode() {
        getSharedPreferences("opengw_lancador", 0);
        this.leftSlotUse = "widgets";
        this.rightSlotUse = "widgets";
    }

    private void loadWindowApps() {
        this.windowPackages.clear();
        String string = getSharedPreferences("opengw_lancador", 0).getString("window_apps", "");
        if (string == null || string.isEmpty()) {
            return;
        }
        for (String str : string.split(",")) {
            if (str != null) {
                String strTrim = str.trim();
                if (!strTrim.isEmpty()) {
                    this.windowPackages.add(strTrim);
                }
            }
        }
    }

    private View makeAppsFabLayoutTextBtn(String str, String str2, float f) {
        TextView textView = new TextView(this);
        textView.setText(str2);
        textView.setTextColor(-1379592);
        textView.setTextSize(13.0f);
        textView.setGravity(17);
        textView.setPadding(Math.round(f * 12.0f), Math.round(f * 12.0f), Math.round(f * 12.0f), Math.round(12.0f * f));
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.topMargin = Math.round(f * 4.0f);
        layoutParams.bottomMargin = Math.round(4.0f * f);
        textView.setLayoutParams(layoutParams);
        styleAppsFabIconCell(textView, str.equals(this.shellMode), f);
        textView.setClickable(true);
        textView.setOnClickListener(view -> {
            setAppsFabMenuOpen(false);
            if (str.equals(this.shellMode)) {
                return;
            }
            applyShellMode(str);
        });
        return textView;
    }

    private View makeAppsFabRatioBtn(String str, float f) {
        FrameLayout frameLayout = new FrameLayout(this);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(Math.round(76.0f * f), Math.round(58.0f * f));
        layoutParams.setMargins(Math.round(f * 4.0f), 0, Math.round(4.0f * f), 0);
        frameLayout.setLayoutParams(layoutParams);
        frameLayout.setTag(str);
        frameLayout.setClickable(true);
        frameLayout.setOnClickListener(view -> {
            applySplitRatio(str);
            refreshAppsFabMenu();
        });
        frameLayout.addView(makeRatioGlyphView(str, f));
        styleAppsFabIconCell(frameLayout, str.equals(this.splitRatio), f);
        return frameLayout;
    }

    private TextView makeAppsFabSectionLabel(String str, float f) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextColor(-2132086024);
        textView.setTextSize(12.0f);
        textView.setLetterSpacing(0.06f);
        textView.setGravity(17);
        textView.setSingleLine(true);
        textView.setPadding(0, Math.round(f * 6.0f), 0, Math.round(6.0f * f));
        textView.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return textView;
    }

    private TextView makeAppsFabTextBtn(String str, float f, Runnable runnable) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextColor(-1379592);
        textView.setTextSize(14.0f);
        textView.setGravity(17);
        textView.setPadding(Math.round(f * 18.0f), Math.round(f * 14.0f), Math.round(18.0f * f), Math.round(14.0f * f));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams.setMargins(Math.round(f * 4.0f), 0, Math.round(4.0f * f), 0);
        textView.setLayoutParams(layoutParams);
        styleAppsFabIconCell(textView, false, f);
        textView.setClickable(true);
        textView.setOnClickListener(view -> runnable.run());
        return textView;
    }

    private View makeCenterFillChip(float f, int i, int i2) {
        FrameLayout frameLayout = new FrameLayout(this);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(i, -1);
        layoutParams.rightMargin = Math.round(f * 8.0f);
        frameLayout.setLayoutParams(layoutParams);
        frameLayout.setContentDescription("Center background");
        int iRound = Math.round(i2 + (f * 10.0f));
        View view = new View(this);
        FrameLayout.LayoutParams layoutParams2 = new FrameLayout.LayoutParams(i - Math.round(f * 4.0f), iRound);
        layoutParams2.gravity = 17;
        view.setLayoutParams(layoutParams2);
        view.setTag("modePlate");
        view.setBackground(makeDockPlateDrawable(false, f));
        frameLayout.addView(view);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        FrameLayout.LayoutParams layoutParams3 = new FrameLayout.LayoutParams(-1, iRound);
        layoutParams3.gravity = 17;
        linearLayout.setLayoutParams(layoutParams3);
        linearLayout.setPadding(Math.round(f * 10.0f), 0, Math.round(f * 8.0f), 0);
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(0);
        linearLayout2.setGravity(16);
        linearLayout2.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1.0f));
        linearLayout2.setClickable(true);
        linearLayout2.setFocusable(true);
        linearLayout2.setOnClickListener(view2 -> {
            callViewerDock("toggleCenterFill");
        });
        int iRound2 = Math.round(i2 * 0.44f);
        this.layoutCenterFillIcon = new ImageView(this);
        LinearLayout.LayoutParams layoutParams4 = new LinearLayout.LayoutParams(iRound2, iRound2);
        layoutParams4.rightMargin = Math.round(8.0f * f);
        this.layoutCenterFillIcon.setLayoutParams(layoutParams4);
        this.layoutCenterFillIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        linearLayout2.addView(this.layoutCenterFillIcon);
        this.layoutCenterFillLabel = new TextView(this);
        this.layoutCenterFillLabel.setTextColor(dockLabelColor());
        this.layoutCenterFillLabel.setTextSize(11.0f);
        this.layoutCenterFillLabel.setTypeface(Typeface.create("sans-serif-medium", 0));
        this.layoutCenterFillLabel.setLetterSpacing(0.04f);
        this.layoutCenterFillLabel.setMaxLines(1);
        this.layoutCenterFillLabel.setEllipsize(TextUtils.TruncateAt.END);
        linearLayout2.addView(this.layoutCenterFillLabel);
        linearLayout.addView(linearLayout2);
        this.layoutCenterFillGear = new ImageView(this);
        int iRound3 = Math.round(i2 * 0.38f);
        LinearLayout.LayoutParams layoutParams5 = new LinearLayout.LayoutParams(iRound3, iRound3);
        layoutParams5.leftMargin = Math.round(f * 4.0f);
        this.layoutCenterFillGear.setLayoutParams(layoutParams5);
        this.layoutCenterFillGear.setImageDrawable(systemDockIcon(17301577));
        this.layoutCenterFillGear.setScaleType(ImageView.ScaleType.FIT_CENTER);
        this.layoutCenterFillGear.setClickable(true);
        this.layoutCenterFillGear.setFocusable(true);
        this.layoutCenterFillGear.setContentDescription("Configure wallpaper");
        this.layoutCenterFillGear.setOnClickListener(view3 -> {
            callViewerDock("configureWallpaper");
        });
        this.layoutCenterFillGear.setVisibility(8);
        linearLayout.addView(this.layoutCenterFillGear);
        frameLayout.addView(linearLayout);
        return frameLayout;
    }

    private Drawable makeCircleFrostDrawable(boolean z, float f) {
        int[] iArr;
        int[] iArr2;
        int iMax = Math.max(1, Math.round(f));
        GradientDrawable.Orientation orientation = GradientDrawable.Orientation.TOP_BOTTOM;
        if (z) {
            iArr = new int[]{withAlpha(this.dockAccentColor, 216), withAlpha(this.dockAccentColor, 96)};
        } else {
            iArr = this.dockUiLight ? new int[]{-1191182337, 1334483368} : new int[]{1895825407, 553648127};
        }
        GradientDrawable gradientDrawable = new GradientDrawable(orientation, iArr);
        gradientDrawable.setShape(1);
        if (this.dockUiLight) {
            iArr2 = z ? new int[]{blendArgb(-117900548, this.dockAccentColor, 0.1f), blendArgb(-253104397, this.dockAccentColor, 0.06f)} : new int[]{-218629637, -370544652};
        } else {
            iArr2 = z ? new int[]{blendArgb(-350739667, this.dockAccentColor, 0.08f), -535948261} : new int[]{-535158227, -653388773};
        }
        GradientDrawable gradientDrawable2 = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, iArr2);
        gradientDrawable2.setShape(1);
        LayerDrawable layerDrawable = new LayerDrawable(new Drawable[]{gradientDrawable, gradientDrawable2});
        layerDrawable.setLayerInset(1, iMax, iMax, iMax, iMax);
        return layerDrawable;
    }

    private View makeDesktopStatusChip(float f, int i, int i2) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setGravity(17);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(i, Math.round(i2 + (f * 10.0f)));
        layoutParams.leftMargin = Math.round(4.0f * f);
        linearLayout.setLayoutParams(layoutParams);
        linearLayout.setPadding(Math.round(f * 12.0f), 0, Math.round(12.0f * f), 0);
        linearLayout.setClickable(true);
        linearLayout.setFocusable(true);
        linearLayout.setBackground(makeFrostStateDrawable(true, f));
        this.activeDesktopTitle = new TextView(this);
        this.activeDesktopTitle.setText(this.activeDesktopName);
        this.activeDesktopTitle.setTextColor(dockLabelColor());
        this.activeDesktopTitle.setTextSize(13.0f);
        this.activeDesktopTitle.setTypeface(Typeface.create("sans-serif-medium", 0));
        this.activeDesktopTitle.setMaxLines(1);
        this.activeDesktopTitle.setEllipsize(TextUtils.TruncateAt.END);
        this.activeDesktopTitle.setGravity(17);
        linearLayout.addView(this.activeDesktopTitle);
        this.activeDesktopMeta = new TextView(this);
        this.activeDesktopMeta.setText("1 of 1");
        this.activeDesktopMeta.setTextColor(dockLabelColorMuted());
        this.activeDesktopMeta.setTextSize(10.0f);
        this.activeDesktopMeta.setLetterSpacing(0.08f);
        this.activeDesktopMeta.setGravity(17);
        linearLayout.addView(this.activeDesktopMeta);
        refreshDesktopIndicator();
        return linearLayout;
    }

    private MotionTrailLayout makeDockItem(int i, int i2, float f) {
        MotionTrailLayout motionTrailLayout = new MotionTrailLayout(this);
        motionTrailLayout.setOrientation(1);
        motionTrailLayout.setGravity(49);
        motionTrailLayout.setAlpha(0.0f);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(i, -2);
        layoutParams.rightMargin = Math.round(12.0f * f);
        motionTrailLayout.setLayoutParams(layoutParams);
        motionTrailLayout.setBackgroundColor(0);
        motionTrailLayout.setClickable(true);
        motionTrailLayout.setFocusable(true);
        ImageView imageView = new ImageView(this);
        imageView.setTag("icon");
        FrameLayout frameLayoutWrapLauncherIconPlate = wrapLauncherIconPlate(imageView, i2, f);
        frameLayoutWrapLauncherIconPlate.setLayoutParams(new LinearLayout.LayoutParams(-1, dockPlatePx(i2, f)));
        motionTrailLayout.addView(frameLayoutWrapLauncherIconPlate);
        TextView textView = new TextView(this);
        textView.setTag("label");
        styleDockCaption(textView, f);
        motionTrailLayout.addView(textView);
        return motionTrailLayout;
    }

    private TextView makeDockMenuRow(String str, Runnable runnable) {
        float f = getResources().getDisplayMetrics().density;
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextColor(-1);
        textView.setTextSize(16.0f);
        textView.setMinHeight(Math.round(48.0f * f));
        textView.setGravity(16);
        textView.setPadding(Math.round(f * 10.0f), Math.round(f * 12.0f), Math.round(10.0f * f), Math.round(12.0f * f));
        textView.setClickable(true);
        textView.setFocusable(true);
        textView.setOnClickListener(view -> runnable.run());
        return textView;
    }

    private Drawable makeDockPlateDrawable(boolean z, float f) {
        float f2 = 14.0f * f;
        int iMax = Math.max(1, Math.round((z ? 1.5f : 1.0f) * f));
        if (!this.dockUiLight) {
            GradientDrawable gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, dockPlateGradientColors(z));
            gradientDrawable.setShape(0);
            gradientDrawable.setCornerRadius(f2);
            gradientDrawable.setStroke(iMax, z ? -1459617793 : 1358954495);
            return gradientDrawable;
        }
        GradientDrawable gradientDrawable2 = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, dockPlateBorderGradientColors(z));
        gradientDrawable2.setShape(0);
        gradientDrawable2.setCornerRadius(f2);
        GradientDrawable gradientDrawable3 = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, dockPlateGradientColors(z));
        gradientDrawable3.setShape(0);
        gradientDrawable3.setCornerRadius(Math.max(0.0f, f2 - iMax));
        LayerDrawable layerDrawable = new LayerDrawable(new Drawable[]{gradientDrawable2, gradientDrawable3});
        layerDrawable.setLayerInset(1, iMax, iMax, iMax, iMax);
        return layerDrawable;
    }

    private TextView makeDockPopupAction(String str, boolean z, float f, Runnable runnable) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextSize(14.0f);
        textView.setGravity(17);
        textView.setPadding(Math.round(f * 16.0f), Math.round(f * 12.0f), Math.round(16.0f * f), Math.round(12.0f * f));
        textView.setTextColor(z ? this.dockAccentColor : dockLabelColor());
        textView.setBackground(makeQuickMenuItemBackground(z, !z, f));
        textView.setClickable(true);
        textView.setFocusable(true);
        textView.setOnClickListener(view -> runnable.run());
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams.setMargins(Math.round(f * 4.0f), 0, Math.round(4.0f * f), 0);
        textView.setLayoutParams(layoutParams);
        return textView;
    }

    private Drawable makeDockPopupFieldBackground(float f) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setCornerRadius(10.0f * f);
        gradientDrawable.setColor(this.dockUiLight ? 254095419 : 352321535);
        gradientDrawable.setStroke(Math.max(1, Math.round(f)), this.dockUiLight ? 438644795 : 452984831);
        return gradientDrawable;
    }

    private TextView makeDockPopupKicker(String str, float f) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextColor(dockLabelColorMuted());
        textView.setTextSize(11.0f);
        textView.setLetterSpacing(0.08f);
        textView.setTypeface(Typeface.create("sans-serif-medium", 0));
        textView.setPadding(0, 0, 0, Math.round(6.0f * f));
        return textView;
    }

    private GradientDrawable makeDockPopupPanel(float f) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setCornerRadius(14.0f * f);
        gradientDrawable.setColor(this.dockUiLight ? -50857220 : -99938282);
        gradientDrawable.setStroke(Math.max(1, Math.round(f)), this.dockUiLight ? 572862523 : 654311423);
        return gradientDrawable;
    }

    private View makeDockTipChip(float f, int i, int i2, String str) {
        FrameLayout frameLayout = new FrameLayout(this);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(i, -1);
        layoutParams.leftMargin = Math.round(6.0f * f);
        frameLayout.setLayoutParams(layoutParams);
        frameLayout.setClickable(false);
        frameLayout.setFocusable(false);
        frameLayout.setImportantForAccessibility(1);
        frameLayout.setContentDescription(str);
        View view = new View(this);
        FrameLayout.LayoutParams layoutParams2 = new FrameLayout.LayoutParams(i - Math.round(8.0f * f), Math.round(i2 + (f * 10.0f)));
        layoutParams2.gravity = 17;
        view.setLayoutParams(layoutParams2);
        view.setTag("modePlate");
        view.setBackground(makeDockPlateDrawable(false, f));
        frameLayout.addView(view);
        TextView textView = new TextView(this);
        FrameLayout.LayoutParams layoutParams3 = new FrameLayout.LayoutParams(-2, -2);
        layoutParams3.gravity = 17;
        textView.setLayoutParams(layoutParams3);
        textView.setTag("dockChipLabel");
        textView.setText(str);
        textView.setTextColor(dockLabelColorMuted());
        textView.setTextSize(10.5f);
        textView.setTypeface(Typeface.create("sans-serif-medium", 0));
        textView.setLetterSpacing(0.03f);
        textView.setMaxLines(2);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity(17);
        textView.setPadding(Math.round(f * 10.0f), 0, Math.round(10.0f * f), 0);
        frameLayout.addView(textView);
        return frameLayout;
    }

    private Drawable makeFrostLayer(boolean z, float f) {
        return makeFrostLayer(z, f, 0);
    }

    private Drawable makeFrostLayer(boolean z, float f, int i) {
        int[] iArr;
        int[] iArr2;
        int[] iArr3;
        int iMax = Math.max(1, Math.round(f));
        float f2 = 16.0f * f;
        GradientDrawable.Orientation orientation = GradientDrawable.Orientation.TOP_BOTTOM;
        if (z) {
            iArr = new int[]{withAlpha(this.dockAccentColor, 216), withAlpha(this.dockAccentColor, 96)};
        } else {
            iArr = this.dockUiLight ? new int[]{-1191182337, 1334483368} : new int[]{1895825407, 553648127};
        }
        GradientDrawable gradientDrawable = new GradientDrawable(orientation, iArr);
        gradientDrawable.setCornerRadius(f2);
        if (this.dockUiLight) {
            iArr2 = z ? new int[]{blendArgb(-117900548, this.dockAccentColor, 0.1f), blendArgb(-253104397, this.dockAccentColor, 0.06f)} : new int[]{-218629637, -370544652};
        } else {
            iArr2 = z ? new int[]{blendArgb(-350739667, this.dockAccentColor, 0.08f), -535948261} : new int[]{-535158227, -653388773};
        }
        if (i != 0) {
            iArr3 = new int[]{blendArgb(iArr2[0], i, this.dockUiLight ? 0.22f : 0.34f), blendArgb(iArr2[iArr2.length - 1], i, this.dockUiLight ? 0.02f : 0.04f)};
        } else {
            iArr3 = iArr2;
        }
        GradientDrawable gradientDrawable2 = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, iArr3);
        gradientDrawable2.setCornerRadius(Math.max(0.0f, f2 - iMax));
        if (i == 0) {
            LayerDrawable layerDrawable = new LayerDrawable(new Drawable[]{gradientDrawable, gradientDrawable2});
            layerDrawable.setLayerInset(1, iMax, iMax, iMax, iMax);
            return layerDrawable;
        }
        GradientDrawable gradientDrawable3 = new GradientDrawable();
        gradientDrawable3.setShape(0);
        gradientDrawable3.setCornerRadius(Math.max(0.0f, f2 - iMax));
        gradientDrawable3.setGradientType(1);
        gradientDrawable3.setGradientCenter(0.22f, 0.34f);
        gradientDrawable3.setGradientRadius(118.0f * f);
        gradientDrawable3.setColors(new int[]{withAlpha(i, this.dockUiLight ? 90 : 134), withAlpha(i, this.dockUiLight ? 24 : 36), withAlpha(i, 0)});
        LayerDrawable layerDrawable2 = new LayerDrawable(new Drawable[]{gradientDrawable, gradientDrawable2, gradientDrawable3});
        layerDrawable2.setLayerInset(1, iMax, iMax, iMax, iMax);
        layerDrawable2.setLayerInset(2, iMax, iMax, iMax, iMax);
        return layerDrawable2;
    }

    private Drawable makeFrostStateDrawable(boolean z, float f) {
        return makeFrostStateDrawable(z, f, 0);
    }

    private Drawable makeFrostStateDrawable(boolean z, float f, int i) {
        StateListDrawable stateListDrawable = new StateListDrawable();
        stateListDrawable.addState(new int[]{16842919}, makeFrostLayer(true, f, i));
        stateListDrawable.addState(new int[]{16842913}, makeFrostLayer(true, f, i));
        stateListDrawable.addState(new int[0], makeFrostLayer(z, f, i));
        return stateListDrawable;
    }

    private View makeGlyphBar(float f, float f2) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(0, -1, f2));
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(-638913800);
        gradientDrawable.setCornerRadius(2.0f * f);
        view.setBackground(gradientDrawable);
        return view;
    }

    private Drawable makeLauncherIconPlateDrawable(float f, int i) {
        int iMax = Math.max(1, Math.round(f));
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setCornerRadius(14.0f * f);
        gradientDrawable.setColor(i);
        gradientDrawable.setStroke(iMax, 956301311);
        return gradientDrawable;
    }

    private Drawable makeLauncherIconPlateDrawable(float f, boolean z) {
        int[] iArr;
        int[] iArr2;
        int iMax = Math.max(1, Math.round(f));
        float f2 = 14.0f * f;
        GradientDrawable.Orientation orientation = GradientDrawable.Orientation.TOP_BOTTOM;
        if (z) {
            iArr = new int[]{2063597567, 536870911};
        } else {
            iArr = this.dockUiLight ? new int[]{1728053247, 613655218} : new int[]{-1996488705, 889192447};
        }
        GradientDrawable gradientDrawable = new GradientDrawable(orientation, iArr);
        gradientDrawable.setCornerRadius(f2);
        GradientDrawable.Orientation orientation2 = GradientDrawable.Orientation.TOP_BOTTOM;
        boolean z2 = this.dockUiLight;
        if (z) {
            iArr2 = z2 ? new int[]{-14670029, -15723236} : new int[]{-234222318, -335544320};
        } else {
            iArr2 = z2 ? new int[]{-197122, -985865} : new int[]{-118229254, -387979540};
        }
        GradientDrawable gradientDrawable2 = new GradientDrawable(orientation2, iArr2);
        gradientDrawable2.setCornerRadius(Math.max(0.0f, f2 - iMax));
        LayerDrawable layerDrawable = new LayerDrawable(new Drawable[]{gradientDrawable, gradientDrawable2});
        layerDrawable.setLayerInset(1, iMax, iMax, iMax, iMax);
        return layerDrawable;
    }

    private View makeLayoutDivider(float f) {
        View view = new View(this);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(Math.max(1, Math.round(f)), Math.round(76.0f * f));
        layoutParams.leftMargin = Math.round(f * 12.0f);
        layoutParams.rightMargin = Math.round(12.0f * f);
        view.setLayoutParams(layoutParams);
        view.setBackgroundColor(this.dockUiLight ? 813727872 : 855638015);
        return view;
    }

    private View makeLayoutGroup(float f, String str, View view) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setGravity(16);
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(-2, -1));
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextSize(9.0f);
        textView.setTextColor(dockLabelColorMuted());
        textView.setLetterSpacing(0.12f);
        textView.setTypeface(Typeface.create("sans-serif-medium", 0));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, Math.round(18.0f * f));
        layoutParams.leftMargin = Math.round(8.0f * f);
        textView.setLayoutParams(layoutParams);
        linearLayout.addView(textView);
        linearLayout.addView(view, new LinearLayout.LayoutParams(-2, 0, 1.0f));
        return linearLayout;
    }

    private Drawable makeMediaButtonBackground(float f, boolean z, int i) {
        StateListDrawable stateListDrawable = new StateListDrawable();
        stateListDrawable.addState(new int[]{16842919}, mediaButtonFace(f, z, true));
        stateListDrawable.addState(new int[0], mediaButtonFace(f, z, false));
        return stateListDrawable;
    }

    private View makeModeCell(float f, int i, int i2, int i3, Drawable drawable, String str, View.OnClickListener onClickListener) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setGravity(49);
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(i, -2));
        linearLayout.setPadding(0, i3, 0, 0);
        linearLayout.setClickable(true);
        linearLayout.setFocusable(true);
        linearLayout.setOnClickListener(onClickListener);
        if (str != null && !str.isEmpty()) {
            linearLayout.setContentDescription(str);
        }
        FrameLayout frameLayout = new FrameLayout(this);
        int iDockPlatePx = dockPlatePx(i2, f);
        frameLayout.setLayoutParams(new LinearLayout.LayoutParams(iDockPlatePx, iDockPlatePx));
        frameLayout.setBackgroundColor(0);
        View view = new View(this);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(iDockPlatePx, iDockPlatePx);
        layoutParams.gravity = 17;
        view.setLayoutParams(layoutParams);
        view.setTag("modePlate");
        view.setBackground(makeDockPlateDrawable(false, f));
        frameLayout.addView(view);
        int iRound = Math.round(i2 * 0.68f);
        ImageView imageView = new ImageView(this);
        imageView.setTag("modeIcon");
        FrameLayout.LayoutParams layoutParams2 = new FrameLayout.LayoutParams(iRound, iRound);
        layoutParams2.gravity = 17;
        imageView.setLayoutParams(layoutParams2);
        imageView.setImageDrawable(drawable);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setAdjustViewBounds(false);
        frameLayout.addView(imageView);
        linearLayout.addView(frameLayout);
        TextView textView = new TextView(this);
        textView.setTag("modeLabel");
        textView.setText(str != null ? str : "");
        styleDockCaption(textView, f);
        textView.setTextColor(dockLabelColorMuted());
        textView.setLetterSpacing(0.02f);
        if (this.dockUiLight) {
            textView.setShadowLayer(0.0f, 0.0f, 0.0f, 0);
        }
        textView.setVisibility(4);
        linearLayout.addView(textView);
        return linearLayout;
    }

    private View makeModeCell(float f, int i, int i2, Drawable drawable, String str, View.OnClickListener onClickListener) {
        return makeModeCell(f, i, i2, this.dockIconRowTopPadPx > 0 ? this.dockIconRowTopPadPx : Math.round(8.0f * f), drawable, str, onClickListener);
    }

    private View makeQuickClockCard(float f, BottomCardDescriptor bottomCardDescriptor, View.OnClickListener onClickListener) {
        FrameLayout frameLayout = new FrameLayout(this);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(Math.round(224.0f * f), Math.round(124.0f * f));
        layoutParams.rightMargin = Math.round(10.0f * f);
        frameLayout.setLayoutParams(layoutParams);
        frameLayout.setMinimumHeight(Math.round(112.0f * f));
        frameLayout.setClickable(true);
        frameLayout.setFocusable(true);
        frameLayout.setContentDescription(bottomCardAccessibilityDescription(bottomCardDescriptor));
        frameLayout.setOnClickListener(onClickListener);
        if (!bottomCardDescriptor.longAction.isEmpty()) {
            String str = bottomCardDescriptor.longAction;
            frameLayout.setLongClickable(true);
            frameLayout.setOnLongClickListener(view -> {
                view.performHapticFeedback(0);
                callViewerDock(str);
                return true;
            });
        }
        frameLayout.setBackground(makeFrostStateDrawable(false, f, drivingWashColor(bottomCardDescriptor.state)));
        frameLayout.setElevation(3.0f * f);
        this.quickCardViews.add(frameLayout);
        this.quickCardHosts.put(bottomCardDescriptor.id, frameLayout);
        this.lastDrivingWash.put(bottomCardDescriptor.id, Integer.valueOf(drivingWashColor(bottomCardDescriptor.state)));
        QuickClockCardView quickClockCardView = new QuickClockCardView(this, bottomCardDescriptor);
        quickClockCardView.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        if (this.quickClockFaceBitmap != null) {
            quickClockCardView.setRenderedFace(this.quickClockFaceBitmap);
        }
        frameLayout.addView(quickClockCardView);
        this.quickClockCards.put(bottomCardDescriptor.id, quickClockCardView);
        return frameLayout;
    }

    private View makeQuickMediaAppChip(float f) {
        SourceChip sourceChipMakeSourceChip = makeSourceChip(f);
        this.quickMediaAppIcon = sourceChipMakeSourceChip.icon;
        this.quickMediaChip = sourceChipMakeSourceChip;
        return sourceChipMakeSourceChip.row;
    }

    private ImageView makeQuickMediaButton(float f, boolean z, String str, String str2, View.OnClickListener onClickListener) {
        int iRound = Math.round((z ? 48 : 40) * f);
        ImageView imageView = new ImageView(this);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(iRound, iRound);
        layoutParams.rightMargin = Math.round(8.0f * f);
        imageView.setLayoutParams(layoutParams);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setTag(z ? "mediaBtnPrimary" : "mediaBtn");
        imageView.setContentDescription(str2);
        imageView.setClickable(true);
        imageView.setFocusable(true);
        imageView.setImageDrawable(mediaGlyph(f, z, str));
        imageView.setBackground(makeMediaButtonBackground(f, z, iRound));
        imageView.setOnClickListener(onClickListener);
        imageView.setEnabled(this.quickMediaAvailable);
        imageView.setAlpha(this.quickMediaAvailable ? 1.0f : 0.35f);
        this.quickMediaButtons.add(imageView);
        return imageView;
    }

    private View makeQuickMediaCard(float f, BottomCardDescriptor bottomCardDescriptor) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        int iMax = Math.max(1, Math.round(f));
        linearLayout.setPadding(iMax, iMax, iMax, iMax);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(Math.round(380.0f * f), Math.round(f * 124.0f));
        layoutParams.rightMargin = Math.round(10.0f * f);
        linearLayout.setLayoutParams(layoutParams);
        linearLayout.setBackground(makeFrostStateDrawable(false, f));
        linearLayout.setElevation(3.0f * f);
        linearLayout.setContentDescription(bottomCardDescriptor != null ? bottomCardAccessibilityDescription(bottomCardDescriptor) : "Media quick controls");
        linearLayout.setClickable(true);
        linearLayout.setFocusable(true);
        final String script3 = bottomCardDescriptor != null ? bottomCardDescriptor.action : "";
        linearLayout.setOnClickListener(view2 -> {
            if (this.quickMediaCanLaunch && this.quickMediaPackage != null && !this.quickMediaPackage.isEmpty()) {
                launchAppForPackage(this.quickMediaPackage, this.quickMediaTitle != null ? this.quickMediaTitle.getText().toString() : "Media");
            } else {
                if (script3.isEmpty()) {
                    return;
                }
                callViewerDock(script3);
            }
        });
        if (bottomCardDescriptor != null && !bottomCardDescriptor.longAction.isEmpty()) {
            String str = bottomCardDescriptor.longAction;
            linearLayout.setLongClickable(true);
            linearLayout.setOnLongClickListener(view3 -> {
                view3.performHapticFeedback(0);
                callViewerDock(str);
                return true;
            });
        }
        this.quickCardViews.add(linearLayout);
        this.quickMediaArt = new ImageView(this);
        this.quickMediaArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        this.quickMediaArt.setBackground(makeDockPlateDrawable(false, f));
        this.quickMediaArt.setPadding(Math.round(f * 32.0f), Math.round(f * 32.0f), Math.round(f * 32.0f), Math.round(32.0f * f));
        this.quickMediaArt.setImageDrawable(mediaArtPlaceholder(f));
        this.quickMediaArt.setClipToOutline(true);
        this.quickMediaArt.setOutlineProvider(new ViewOutlineProvider() {
            @Override // android.view.ViewOutlineProvider
            public void getOutline(View view, Outline outline) {
                float f = MainActivity.this.getResources().getDisplayMetrics().density;
                float fMax = (16.0f * f) - Math.max(1.0f, f);
                outline.setRoundRect(0, 0, view.getWidth() + Math.round(fMax), view.getHeight(), fMax);
            }
        });
        this.quickMediaArt.setLayoutParams(new LinearLayout.LayoutParams(Math.round(f * 124.0f) - (iMax * 2), -1));
        linearLayout.addView(this.quickMediaArt);
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(1);
        linearLayout2.setGravity(16);
        linearLayout2.setPadding(Math.round(13.0f * f), Math.round(f * 4.0f), Math.round(11.0f * f), Math.round(f * 4.0f));
        linearLayout2.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        LinearLayout linearLayout3 = new LinearLayout(this);
        linearLayout3.setOrientation(0);
        linearLayout3.setGravity(16);
        linearLayout3.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        TextView textView = new TextView(this);
        textView.setTag("frostSecondary");
        textView.setText("MEDIA");
        textView.setTextSize(10.5f);
        textView.setLetterSpacing(0.11f);
        textView.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
        linearLayout3.addView(textView);
        this.quickMediaAppRow = makeQuickMediaAppChip(f);
        linearLayout3.addView(this.quickMediaAppRow);
        linearLayout2.addView(linearLayout3);
        this.quickMediaTitle = new TextView(this);
        this.quickMediaTitle.setTag("quickValue");
        this.quickMediaTitle.setText("Nothing playing");
        this.quickMediaTitle.setTextSize(15.0f);
        this.quickMediaTitle.setTypeface(Typeface.create("sans-serif-medium", 0));
        this.quickMediaTitle.setMaxLines(1);
        this.quickMediaTitle.setEllipsize(TextUtils.TruncateAt.END);
        linearLayout2.addView(this.quickMediaTitle);
        this.quickMediaArtist = new TextView(this);
        this.quickMediaArtist.setTag("frostSecondary");
        this.quickMediaArtist.setText("Choose a media app");
        this.quickMediaArtist.setTextSize(9.5f);
        this.quickMediaArtist.setMaxLines(1);
        this.quickMediaArtist.setEllipsize(TextUtils.TruncateAt.END);
        linearLayout2.addView(this.quickMediaArtist);
        LinearLayout linearLayout4 = new LinearLayout(this);
        linearLayout4.setOrientation(0);
        linearLayout4.setGravity(8388627);
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-2, -2);
        layoutParams2.topMargin = 0;
        linearLayout4.setLayoutParams(layoutParams2);
        linearLayout4.addView(makeQuickMediaButton(f, false, "prev", "Previous track", view4 -> {
            this.mediaNowPlaying.prev();
        }));
        this.quickMediaPlayPause = makeQuickMediaButton(f, true, "play", "Play", view5 -> {
            this.mediaNowPlaying.playPause();
        });
        linearLayout4.addView(this.quickMediaPlayPause);
        linearLayout4.addView(makeQuickMediaButton(f, false, "next", "Next track", view -> {
            this.mediaNowPlaying.next();
        }));
        linearLayout2.addView(linearLayout4);
        FrameLayout frameLayout = new FrameLayout(this);
        frameLayout.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1.0f));
        this.quickMediaBars = new QuickMediaBarsView(this);
        frameLayout.addView(this.quickMediaBars, new FrameLayout.LayoutParams(-1, -1));
        frameLayout.addView(linearLayout2);
        linearLayout.addView(frameLayout);
        return linearLayout;
    }

    private Drawable makeQuickMenuItemBackground(boolean z, boolean z2, float f) {
        float f2 = 10.0f * f;
        int iMax = Math.max(1, Math.round(f));
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setCornerRadius(f2);
        if (z) {
            gradientDrawable.setColor(withAlpha(this.dockAccentColor, 46));
            gradientDrawable.setStroke(iMax, this.dockAccentColor);
        } else if (z2) {
            gradientDrawable.setColor(0);
            gradientDrawable.setStroke(iMax, this.dockUiLight ? 572862523 : 587202559);
        } else {
            gradientDrawable.setColor(this.dockUiLight ? 254095419 : 352321535);
            gradientDrawable.setStroke(iMax, this.dockUiLight ? 438644795 : 452984831);
        }
        GradientDrawable gradientDrawable2 = new GradientDrawable();
        gradientDrawable2.setCornerRadius(f2);
        gradientDrawable2.setColor(withAlpha(this.dockAccentColor, 64));
        gradientDrawable2.setStroke(iMax, this.dockAccentColor);
        StateListDrawable stateListDrawable = new StateListDrawable();
        stateListDrawable.addState(new int[]{16842919}, gradientDrawable2);
        stateListDrawable.addState(new int[0], gradientDrawable);
        return stateListDrawable;
    }

    private View makeQuickTextCard(float f, int i, String str, String str2, String str3, View.OnClickListener onClickListener) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setGravity(16);
        int iRound = Math.round(16.0f * f);
        linearLayout.setPadding(iRound, Math.round(f * 10.0f), iRound, Math.round(9.0f * f));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(Math.round(i * f), Math.round(124.0f * f));
        layoutParams.rightMargin = Math.round(10.0f * f);
        linearLayout.setLayoutParams(layoutParams);
        linearLayout.setMinimumHeight(Math.round(112.0f * f));
        linearLayout.setClickable(true);
        linearLayout.setFocusable(true);
        linearLayout.setContentDescription(str3);
        linearLayout.setOnClickListener(onClickListener);
        linearLayout.setBackground(makeFrostStateDrawable(false, f));
        linearLayout.setElevation(3.0f * f);
        this.quickCardViews.add(linearLayout);
        TextView textView = new TextView(this);
        textView.setTag("frostSecondary");
        textView.setText(str);
        textView.setTextSize(10.5f);
        textView.setTypeface(Typeface.create("sans-serif-medium", 0));
        textView.setLetterSpacing(0.11f);
        linearLayout.addView(textView);
        TextView textView2 = new TextView(this);
        textView2.setTag("quickValue");
        textView2.setText(str2);
        textView2.setTextSize(18.0f);
        textView2.setTypeface(Typeface.create("sans-serif-medium", 0));
        textView2.setMaxLines(1);
        textView2.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, -2);
        layoutParams2.topMargin = Math.round(4.0f * f);
        textView2.setLayoutParams(layoutParams2);
        linearLayout.addView(textView2);
        return linearLayout;
    }

    private View makeQuickVisualCard(float f, BottomCardDescriptor bottomCardDescriptor, View.OnClickListener onClickListener) {
        if ("clock".equals(bottomCardDescriptor.id)) {
            return makeQuickClockCard(f, bottomCardDescriptor, onClickListener);
        }
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setGravity(48);
        int iRound = Math.round(13.0f * f);
        linearLayout.setPadding(iRound, Math.round(f * 9.0f), iRound, Math.round(9.0f * f));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(Math.round(quickVisualCardWidthDp(bottomCardDescriptor.id) * f), Math.round(124.0f * f));
        layoutParams.rightMargin = Math.round(f * 10.0f);
        linearLayout.setLayoutParams(layoutParams);
        linearLayout.setMinimumHeight(Math.round(112.0f * f));
        linearLayout.setClickable(true);
        linearLayout.setFocusable(true);
        linearLayout.setContentDescription(bottomCardAccessibilityDescription(bottomCardDescriptor));
        linearLayout.setOnClickListener(onClickListener);
        if (!bottomCardDescriptor.longAction.isEmpty()) {
            String str = bottomCardDescriptor.longAction;
            linearLayout.setLongClickable(true);
            linearLayout.setOnLongClickListener(view2 -> {
                view2.performHapticFeedback(0);
                callViewerDock(str);
                return true;
            });
        }
        linearLayout.setBackground(makeFrostStateDrawable(false, f, drivingWashColor(bottomCardDescriptor.state)));
        linearLayout.setElevation(f * 3.0f);
        this.quickCardViews.add(linearLayout);
        this.quickCardHosts.put(bottomCardDescriptor.id, linearLayout);
        this.lastDrivingWash.put(bottomCardDescriptor.id, Integer.valueOf(drivingWashColor(bottomCardDescriptor.state)));
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(0);
        linearLayout2.setGravity(16);
        linearLayout2.setClipChildren(false);
        linearLayout2.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.round(22.0f * f)));
        TextView textView = new TextView(this);
        textView.setTag("frostAccent");
        textView.setText("DEMO");
        textView.setTextSize(8.0f);
        textView.setTypeface(Typeface.create("sans-serif-medium", 1));
        textView.setLetterSpacing(0.1f);
        textView.setVisibility(bottomCardDescriptor.demo ? 0 : 8);
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-2, -2);
        layoutParams2.rightMargin = Math.round(7.0f * f);
        linearLayout2.addView(textView, layoutParams2);
        this.quickCardDemoBadges.put(bottomCardDescriptor.id, textView);
        TextView textView2 = new TextView(this);
        textView2.setTag("frostSecondary");
        textView2.setText(bottomCardDescriptor.title.toUpperCase(Locale.US));
        textView2.setTextSize(9.5f);
        textView2.setTypeface(Typeface.create("sans-serif-medium", 0));
        textView2.setLetterSpacing(0.1f);
        textView2.setMaxLines(1);
        textView2.setEllipsize(TextUtils.TruncateAt.END);
        textView2.setTextColor(dockLabelColorMuted());
        textView2.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
        linearLayout2.addView(textView2);
        boolean zEquals = "navigation".equals(bottomCardDescriptor.id);
        if (zEquals) {
            SourceChip sourceChipMakeSourceChip = makeSourceChip(f);
            LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(-2, -2);
            layoutParams3.gravity = 80;
            linearLayout2.addView(sourceChipMakeSourceChip.row, layoutParams3);
            this.quickCardSourceChips.put(bottomCardDescriptor.id, sourceChipMakeSourceChip);
            applyNavigationSourceChip(bottomCardDescriptor);
        }
        if (!zEquals) {
            TextView textView3 = new TextView(this);
            textView3.setTag("frostSecondary");
            textView3.setText("›");
            textView3.setTextSize(18.0f);
            textView3.setGravity(17);
            textView3.setTextColor(dockLabelColorMuted());
            linearLayout2.addView(textView3, new LinearLayout.LayoutParams(Math.round(18.0f * f), -2));
        }
        linearLayout.addView(linearLayout2);
        LinearLayout linearLayout3 = new LinearLayout(this);
        linearLayout3.setOrientation(0);
        linearLayout3.setGravity(16);
        LinearLayout.LayoutParams layoutParams4 = new LinearLayout.LayoutParams(-1, 0, 1.0f);
        layoutParams4.topMargin = Math.round(2.0f * f);
        linearLayout3.setLayoutParams(layoutParams4);
        QuickCardGraphicView quickCardGraphicView = new QuickCardGraphicView(this, bottomCardDescriptor);
        if ("tires".equals(bottomCardDescriptor.id) || "status".equals(bottomCardDescriptor.id) || "navigation".equals(bottomCardDescriptor.id)) {
            quickCardGraphicView.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1.0f));
            linearLayout3.addView(quickCardGraphicView);
            linearLayout.addView(linearLayout3);
            this.quickCardGraphics.put(bottomCardDescriptor.id, quickCardGraphicView);
            return linearLayout;
        }
        LinearLayout.LayoutParams layoutParams5 = new LinearLayout.LayoutParams(Math.round(("power".equals(bottomCardDescriptor.id) ? 156 : 76) * f), -1);
        layoutParams5.rightMargin = Math.round(f * 10.0f);
        quickCardGraphicView.setLayoutParams(layoutParams5);
        if (!bottomCardDescriptor.iconAction.isEmpty()) {
            String str2 = bottomCardDescriptor.iconAction;
            quickCardGraphicView.setClickable(true);
            quickCardGraphicView.setFocusable(true);
            quickCardGraphicView.setContentDescription(bottomCardDescriptor.title + ". Change to the next setting");
            quickCardGraphicView.setOnClickListener(view -> {
                callViewerDock(str2);
            });
        }
        linearLayout3.addView(quickCardGraphicView);
        LinearLayout linearLayout4 = new LinearLayout(this);
        linearLayout4.setOrientation(1);
        linearLayout4.setGravity(16);
        linearLayout4.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1.0f));
        TextView textView4 = new TextView(this);
        textView4.setTag("quickValue");
        textView4.setText(quickVisualPrimary(bottomCardDescriptor));
        textView4.setTextSize("clock".equals(bottomCardDescriptor.id) ? 24.0f : 21.0f);
        textView4.setTypeface(Typeface.create("sans-serif-medium", 0));
        textView4.setTextColor(dockLabelColor());
        textView4.setMaxLines(1);
        textView4.setEllipsize(TextUtils.TruncateAt.END);
        linearLayout4.addView(textView4);
        TextView textView5 = new TextView(this);
        textView5.setTag("frostSecondary");
        textView5.setText(quickVisualDetail(bottomCardDescriptor));
        textView5.setTextSize(9.5f);
        textView5.setLetterSpacing(0.025f);
        textView5.setLineSpacing(0.0f, 1.02f);
        textView5.setMaxLines("power".equals(bottomCardDescriptor.id) ? 4 : 3);
        textView5.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams layoutParams6 = new LinearLayout.LayoutParams(-1, -2);
        layoutParams6.topMargin = Math.round(f * 3.0f);
        textView5.setLayoutParams(layoutParams6);
        linearLayout4.addView(textView5);
        linearLayout3.addView(linearLayout4);
        linearLayout.addView(linearLayout3);
        this.quickCardValues.put(bottomCardDescriptor.id, textView4);
        this.quickCardDetails.put(bottomCardDescriptor.id, textView5);
        this.quickCardGraphics.put(bottomCardDescriptor.id, quickCardGraphicView);
        return linearLayout;
    }

    private View makeQuickWorkspaceCard(float f) {
        FrameLayout frameLayout = new FrameLayout(this);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(Math.round(242.0f * f), Math.round(124.0f * f));
        layoutParams.rightMargin = Math.round(f * 10.0f);
        frameLayout.setLayoutParams(layoutParams);
        frameLayout.setContentDescription("Workspace: app launcher and layout manager");
        frameLayout.setBackground(makeFrostStateDrawable(false, f));
        frameLayout.setElevation(3.0f * f);
        this.quickCardViews.add(frameLayout);
        TextView textView = new TextView(this);
        textView.setText(this.workspaceLayoutMode ? "LAYOUT" : "WORKSPACE");
        textView.setTag("frostSecondary");
        textView.setTextSize(10.5f);
        textView.setTypeface(Typeface.create("sans-serif-medium", 0));
        textView.setLetterSpacing(0.11f);
        textView.setTextColor(dockLabelColorMuted());
        textView.setImportantForAccessibility(2);
        FrameLayout.LayoutParams layoutParams2 = new FrameLayout.LayoutParams(-2, -2);
        layoutParams2.leftMargin = Math.round(f * 14.0f);
        layoutParams2.topMargin = Math.round(10.0f * f);
        textView.setLayoutParams(layoutParams2);
        textView.setVisibility(this.workspaceLayoutMode ? 8 : 0);
        frameLayout.addView(textView);
        this.workspaceHeading = textView;
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        int iRound = Math.round(28.0f * f);
        linearLayout.addView(makeWorkspaceAction(f, true, "APPS", "Show app launcher", view2 -> {
            chooseDockSurface("launcher", true);
        }));
        View view = new View(this);
        view.setTag("workspaceSplit");
        LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(Math.max(1, Math.round(f)), -1);
        layoutParams3.topMargin = iRound;
        layoutParams3.bottomMargin = Math.round(f * 14.0f);
        view.setLayoutParams(layoutParams3);
        view.setBackgroundColor(this.dockUiLight ? 813727872 : 855638015);
        linearLayout.addView(view);
        linearLayout.addView(makeWorkspaceAction(f, false, "LAYOUT", "Show layout shortcuts", view3 -> {
            setWorkspaceLayoutMode(true);
        }));
        frameLayout.addView(linearLayout);
        this.workspaceActionsFace = linearLayout;
        View viewMakeWorkspaceLayoutFace = makeWorkspaceLayoutFace(f);
        frameLayout.addView(viewMakeWorkspaceLayoutFace);
        this.workspaceLayoutFace = viewMakeWorkspaceLayoutFace;
        linearLayout.setVisibility(this.workspaceLayoutMode ? 8 : 0);
        viewMakeWorkspaceLayoutFace.setVisibility(this.workspaceLayoutMode ? 0 : 8);
        return frameLayout;
    }

    private View makeRatioGlyphView(String str, float f) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(Math.round(44.0f * f), Math.round(26.0f * f));
        layoutParams.gravity = 17;
        linearLayout.setLayoutParams(layoutParams);
        float f2 = 1.0f;
        float f3 = 1.0f;
        if ("2:1".equals(str)) {
            f2 = 2.0f;
            f3 = 1.0f;
        } else if ("1:2".equals(str)) {
            f2 = 1.0f;
            f3 = 2.0f;
        }
        linearLayout.addView(makeGlyphBar(f, f2));
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(Math.round(4.0f * f), 1));
        linearLayout.addView(view);
        linearLayout.addView(makeGlyphBar(f, f3));
        return linearLayout;
    }

    private Drawable makeSideBySideGlyph(int i) {
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(i, i, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        Paint paint = new Paint(1);
        paint.setColor(-1379592);
        float f = i;
        float f2 = 0.2f * f;
        float f3 = 0.27f * f;
        float f4 = f * 0.07f;
        float f5 = ((f - (f2 * 2.0f)) - f4) / 2.0f;
        float f6 = f * 0.05f;
        canvas.drawRoundRect(f2, f3, f2 + f5, f - f3, f6, f6, paint);
        paint.setAlpha(190);
        canvas.drawRoundRect(f2 + f5 + f4, f3, f - f2, f - f3, f6, f6, paint);
        return new BitmapDrawable(getResources(), bitmapCreateBitmap);
    }

    private Drawable makeSimpleLayoutGlyph(int i, int i2) {
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(i, i, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        Paint paint = new Paint(1);
        paint.setColor(dockGlyphColor());
        float f = i;
        float f2 = f * 0.18f;
        float f3 = f - (2.0f * f2);
        float f4 = f * 0.07f;
        if (i2 == 2) {
            float f5 = f * 0.28f;
            float f6 = f * 0.34f;
            float f7 = (f - ((f5 + f4) + f6)) * 0.5f;
            canvas.drawRoundRect(f7, f2, f7 + f5, f2 + f3, 3.0f, 3.0f, paint);
            float f8 = f7 + f5 + f4;
            paint.setAlpha(140);
            canvas.drawRoundRect(f8, f2 + (0.12f * f3), f8 + f6, f2 + (0.88f * f3), 4.0f, 4.0f, paint);
        } else {
            float f9 = (f - f4) * 0.38f;
            float f10 = (f - ((f9 + f4) + f9)) * 0.5f;
            canvas.drawRoundRect(f10, f2, f10 + f9, f2 + f3, 3.0f, 3.0f, paint);
            canvas.drawRoundRect(f10 + f9 + f4, f2, f10 + f9 + f4 + f9, f2 + f3, 3.0f, 3.0f, paint);
        }
        return new BitmapDrawable(getResources(), bitmapCreateBitmap);
    }

    private SourceChip makeSourceChip(float f) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        linearLayout.setPadding(0, 0, 0, 0);
        linearLayout.setVisibility(8);
        ImageView imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setLayoutParams(new LinearLayout.LayoutParams(Math.round(f * 30.0f), Math.round(30.0f * f)));
        linearLayout.addView(imageView);
        return new SourceChip(linearLayout, imageView);
    }

    private View makeToolDockChip(float f, int i, int i2, Drawable drawable, String str, String str2, View.OnClickListener onClickListener) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setGravity(1);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(i, -2);
        layoutParams.rightMargin = Math.round(f * 4.0f);
        linearLayout.setLayoutParams(layoutParams);
        linearLayout.setClickable(true);
        linearLayout.setFocusable(true);
        linearLayout.setOnClickListener(onClickListener);
        FrameLayout frameLayout = new FrameLayout(this);
        frameLayout.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.round(i2 + (f * 10.0f))));
        frameLayout.setBackgroundColor(0);
        View view = new View(this);
        int iRound = Math.round(i2 + (4.0f * f));
        FrameLayout.LayoutParams layoutParams2 = new FrameLayout.LayoutParams(iRound, iRound);
        layoutParams2.gravity = 17;
        view.setLayoutParams(layoutParams2);
        view.setTag("modePlate");
        view.setBackground(makeDockPlateDrawable(false, f));
        frameLayout.addView(view);
        ImageView imageView = new ImageView(this);
        int iRound2 = Math.round(i2 * ("model".equals(str) || "perf".equals(str) || "fps".equals(str) ? 0.92f : 0.55f));
        FrameLayout.LayoutParams layoutParams3 = new FrameLayout.LayoutParams(iRound2, iRound2);
        layoutParams3.gravity = 17;
        imageView.setLayoutParams(layoutParams3);
        imageView.setImageDrawable(drawable);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setTag(str);
        frameLayout.addView(imageView);
        if (str != null) {
            this.dockToolIcons.put(str, imageView);
        }
        linearLayout.addView(frameLayout);
        TextView textView = new TextView(this);
        textView.setTag("dockToolCaption");
        textView.setText(str2.toUpperCase());
        textView.setTextColor(dockLabelColorMuted());
        textView.setTextSize(10.0f);
        textView.setGravity(17);
        textView.setLetterSpacing(0.06f);
        if (this.dockUiLight) {
            textView.setShadowLayer(0.0f, 0.0f, 0.0f, 0);
        } else {
            textView.setShadowLayer(3.0f, 0.0f, 1.0f, -1728053248);
        }
        textView.setMaxLines(2);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        linearLayout.addView(textView);
        return linearLayout;
    }

    private View makeWideDockChip(float f, int i, int i2, String str, View.OnClickListener onClickListener) {
        FrameLayout frameLayout = new FrameLayout(this);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(i, -1);
        layoutParams.leftMargin = Math.round(6.0f * f);
        frameLayout.setLayoutParams(layoutParams);
        frameLayout.setClickable(true);
        frameLayout.setFocusable(true);
        frameLayout.setOnClickListener(onClickListener);
        frameLayout.setContentDescription(str);
        View view = new View(this);
        FrameLayout.LayoutParams layoutParams2 = new FrameLayout.LayoutParams(i - Math.round(8.0f * f), Math.round(i2 + (10.0f * f)));
        layoutParams2.gravity = 17;
        view.setLayoutParams(layoutParams2);
        view.setTag("modePlate");
        view.setBackground(makeDockPlateDrawable(false, f));
        frameLayout.addView(view);
        TextView textView = new TextView(this);
        textView.setTag("dockChipLabel");
        FrameLayout.LayoutParams layoutParams3 = new FrameLayout.LayoutParams(-2, -2);
        layoutParams3.gravity = 17;
        textView.setLayoutParams(layoutParams3);
        textView.setText(str);
        textView.setTextColor(dockLabelColor());
        textView.setTextSize(12.0f);
        textView.setTypeface(Typeface.create("sans-serif-medium", 0));
        textView.setLetterSpacing(0.04f);
        frameLayout.addView(textView);
        return frameLayout;
    }

    private View makeWorkspaceAction(float f, boolean z, String str, String str2, View.OnClickListener onClickListener) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setGravity(17);
        linearLayout.setTag(z ? "workspaceApps" : "workspaceLayout");
        linearLayout.setContentDescription(str2);
        linearLayout.setClickable(true);
        linearLayout.setFocusable(true);
        linearLayout.setOnClickListener(onClickListener);
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1.0f));
        linearLayout.setPadding(0, Math.round(28.0f * f), 0, 0);
        ImageView imageView = new ImageView(this);
        imageView.setTag("workspaceGlyph");
        int iRound = Math.round(32.0f * f);
        imageView.setLayoutParams(new LinearLayout.LayoutParams(iRound, iRound));
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        linearLayout.addView(imageView);
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTag("workspaceLabel");
        textView.setGravity(17);
        textView.setTextSize(10.5f);
        textView.setMaxLines(1);
        textView.setTypeface(Typeface.create("sans-serif-medium", 0));
        textView.setLetterSpacing(0.11f);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, -2);
        layoutParams.topMargin = Math.round(6.0f * f);
        textView.setLayoutParams(layoutParams);
        linearLayout.addView(textView);
        applyWorkspaceActionGlyph(linearLayout);
        return linearLayout;
    }

    private View makeWorkspaceLayoutFace(float f) {
        this.workspaceLayoutCells.clear();
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        int iRound = Math.round(6.0f * f);
        linearLayout.setPadding(iRound, iRound, iRound, iRound);
        String[][] strArr = {new String[]{"desktops", "Desktops", "openLayoutDesktops"}, new String[]{"layout", "Cards & widgets", "openLayoutCards"}, new String[]{"appearance", "Appearance", "openLayoutAppearance"}, new String[]{"return", "Return", ""}};
        for (int row = 0; row < 2; row++) {
            LinearLayout linearLayout2 = new LinearLayout(this);
            linearLayout2.setOrientation(0);
            linearLayout2.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1.0f));
            for (int col = 0; col < 2; col++) {
                String[] strArr2 = strArr[(row * 2) + col];
                TextView textView = new TextView(this);
                textView.setText(strArr2[1]);
                textView.setTag("workspaceLayoutCell:" + strArr2[0]);
                textView.setGravity(17);
                textView.setTextSize(13.0f);
                textView.setMaxLines(1);
                textView.setEllipsize(TextUtils.TruncateAt.END);
                textView.setTypeface(Typeface.create("sans-serif-medium", 0));
                textView.setContentDescription("return".equals(strArr2[0]) ? "Return to workspace" : "Open " + strArr2[1]);
                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -1, 1.0f);
                int iRound2 = Math.round(2.0f * f);
                layoutParams.setMargins(iRound2, iRound2, iRound2, iRound2);
                textView.setLayoutParams(layoutParams);
                textView.setClickable(true);
                textView.setFocusable(true);
                textView.setOnClickListener(view -> {
                    if (!"return".equals(strArr2[0])) {
                        callViewerDock(strArr2[2]);
                        return;
                    }
                    setWorkspaceLayoutMode(false);
                    if (this.desktopStudioOpen) {
                        callViewerDock("closeDesktopStudio");
                    }
                });
                this.workspaceLayoutCells.add(textView);
                linearLayout2.addView(textView);
            }
            linearLayout.addView(linearLayout2);
        }
        styleWorkspaceLayoutCells();
        return linearLayout;
    }

    private void markOverlayLaunch() {
        this.overlayLaunchUntilMs = SystemClock.uptimeMillis() + 2000;
    }

    private void maximizePopupApp(String str) {
        boolean z;
        String str2;
        Intent launchIntentForPackage;
        int iFindTaskIdForPackage;
        if (str != null) {
            try {
                if (str.equals(this.activeMediaPackage)) {
                    z = true;
                } else {
                    z = false;
                }
            } catch (Exception e) {
                Log.e("H6Viewer", "Error maximizing app " + str, e);
                return;
            }
        } else {
            z = false;
        }
        if (str == null || str.isEmpty()) {
            str2 = z ? this.activeMediaPackage : this.activePopupPackage;
        } else {
            str2 = str;
        }
        if (str2 != null && !str2.isEmpty() && (launchIntentForPackage = getPackageManager().getLaunchIntentForPackage(str2)) != null && launchIntentForPackage.getComponent() != null) {
            if (this.pinBoundsRunnable != null) {
                this.mainHandler.removeCallbacks(this.pinBoundsRunnable);
            }
            if (this.pinMediaBoundsRunnable != null) {
                this.mainHandler.removeCallbacks(this.pinMediaBoundsRunnable);
            }
            Intent intent = new Intent("android.intent.action.MAIN");
            intent.addCategory("android.intent.category.LAUNCHER");
            intent.setComponent(launchIntentForPackage.getComponent());
            intent.setPackage(str2);
            intent.addFlags(337641472);
            Rect rectTelaCheia = fullscreenAppBounds(str2);
            startActivity(intent, buildWindowOptions(1, rectTelaCheia));
            if (z) {
                iFindTaskIdForPackage = this.activeMediaTaskId >= 0 ? this.activeMediaTaskId : findTaskIdForPackage(str2);
            } else {
                iFindTaskIdForPackage = this.activePopupTaskId >= 0 ? this.activePopupTaskId : findTaskIdForPackage(str2);
            }
            if (iFindTaskIdForPackage >= 0) {
                resizeTaskToBounds(iFindTaskIdForPackage, rectTelaCheia);
            }
            requestTaskBounds(str2, rectTelaCheia);
            if (str2.equals(this.activePopupPackage)) {
                this.activePopupPackage = "";
                this.activePopupTaskId = -1;
                this.activePopupComponent = null;
            }
            if (str2.equals(this.activeMediaPackage)) {
                this.activeMediaPackage = "";
                this.activeMediaTaskId = -1;
                this.activeMediaComponent = null;
            }
            notifyViewerShellLayout();
        }
    }

    private void maybeLaunchFreeformFromIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        String stringExtra = intent.getStringExtra("ui_mode");
        if (stringExtra != null && !stringExtra.isEmpty()) {
            intent.removeExtra("ui_mode");
            if ("inset".equals(stringExtra) || "floating".equals(stringExtra) || "full".equals(stringExtra)) {
                this.uiMode = stringExtra;
                Log.w("H6Viewer", "UI mode: " + this.uiMode);
                saveShellPrefs();
                enterImmersiveMode();
                this.mainHandler.postDelayed(() -> this.notifyViewerShellLayout(), 250L);
            }
        }
        maybeRaiseTaskFromIntent(intent);
        String stringExtra2 = intent.getStringExtra("launch_projection");
        if (stringExtra2 != null && !stringExtra2.isEmpty()) {
            intent.removeExtra("launch_projection");
            if ("AA".equalsIgnoreCase(stringExtra2) || "ANDROID_AUTO".equalsIgnoreCase(stringExtra2)) {
                launchProjection(ProjectionPresence.Kind.ANDROID_AUTO);
            } else if ("CP".equalsIgnoreCase(stringExtra2) || "CARPLAY".equalsIgnoreCase(stringExtra2)) {
                launchProjection(ProjectionPresence.Kind.CARPLAY);
            }
        }
        String stringExtra3 = intent.getStringExtra("launch_freeform");
        if (stringExtra3 == null || stringExtra3.isEmpty()) {
            return;
        }
        intent.removeExtra("launch_freeform");
        launchAppForPackage(stringExtra3, "");
    }

    private void maybeRaiseTaskFromIntent(Intent intent) {
        int intExtra;
        if (intent != null && (intExtra = intent.getIntExtra("raise_task_id", -1)) >= 0) {
            intent.removeExtra("raise_task_id");
            Log.w("H6Viewer", "Probe: raising task " + intExtra + " -> " + moveTaskToFrontNoAnim(intExtra));
        }
    }

    private void maybeRestoreAppsOnlySlots() {
        if (!"appsOnly".equals(this.shellMode) || this.appsOnlyRestoreDone) {
            return;
        }
        this.appsOnlyRestoreDone = true;
        String str = this.pendingRestoreLeft != null ? this.pendingRestoreLeft : "";
        String str2 = this.pendingRestoreRight != null ? this.pendingRestoreRight : "";
        this.pendingRestoreLeft = "";
        this.pendingRestoreRight = "";
        if (str.isEmpty() && str2.isEmpty()) {
            return;
        }
        Log.w("H6Viewer", "Restoring APP+APP slots left=" + str + " right=" + str2);
        if (!str.isEmpty()) {
            this.mainHandler.postDelayed(() -> {
                if ("appsOnly".equals(this.shellMode)) {
                    launchAppInLeftSlot(str);
                }
            }, 500L);
        }
        if (str2.isEmpty()) {
            return;
        }
        this.mainHandler.postDelayed(() -> {
            if ("appsOnly".equals(this.shellMode)) {
                launchAppInRightSlot(str2);
            }
        }, 900L);
    }

    private float measureForegroundContentFraction(Drawable drawable) {
        if (drawable == null) {
            return 1.0f;
        }
        Bitmap bitmap = null;
        try {
            Drawable.ConstantState constantState = drawable.getConstantState();
            Drawable drawableMutate = constantState != null ? constantState.newDrawable().mutate() : drawable;
            Bitmap bitmapCreateBitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
            try {
                Canvas canvas = new Canvas(bitmapCreateBitmap);
                drawableMutate.setBounds(0, 0, 96, 96);
                drawableMutate.draw(canvas);
                int[] iArr = new int[96];
                int i = 96;
                int i2 = 96;
                int i3 = -1;
                int i4 = -1;
                int i5 = 0;
                while (i5 < 96) {
                    int[] iArr2 = iArr;
                    bitmapCreateBitmap.getPixels(iArr2, 0, 96, 0, i5, 96, 1);
                    for (int i6 = 0; i6 < 96; i6++) {
                        if ((iArr2[i6] >>> 24) > 16) {
                            if (i6 < i) {
                                i = i6;
                            }
                            if (i6 > i3) {
                                i3 = i6;
                            }
                            if (i5 < i2) {
                                i2 = i5;
                            }
                            if (i5 > i4) {
                                i4 = i5;
                            }
                        }
                    }
                    i5++;
                    iArr = iArr2;
                }
                if (i3 >= i && i4 >= i2) {
                    float fMax = Math.max(((i3 - i) + 1) / 96, ((i4 - i2) + 1) / 96);
                    if (bitmapCreateBitmap != null) {
                        bitmapCreateBitmap.recycle();
                    }
                    return fMax;
                }
                if (bitmapCreateBitmap != null) {
                    bitmapCreateBitmap.recycle();
                }
                return 1.0f;
            } catch (Exception e) {
                bitmap = bitmapCreateBitmap;
                if (bitmap != null) {
                    bitmap.recycle();
                }
                return 1.0f;
            } catch (Throwable th) {
                bitmap = bitmapCreateBitmap;
                if (bitmap != null) {
                    bitmap.recycle();
                }
                throw th;
            }
        } catch (Exception e2) {
            return 1.0f;
        } catch (Throwable th2) {
            if (bitmap != null) {
                bitmap.recycle();
            }
            return 1.0f;
        }
    }

    private Drawable mediaArtPlaceholder(float f) {
        int iMax = Math.max(16, Math.round(48.0f * f));
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(iMax, iMax, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        Paint paint = new Paint(1);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, iMax * 0.062f));
        paint.setColor(withAlpha(dockLabelColor(), 102));
        float f2 = iMax / 2.0f;
        canvas.drawCircle(f2, f2, iMax * 0.375f, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(f2, f2, iMax * 0.1f, paint);
        return new BitmapDrawable(getResources(), bitmapCreateBitmap);
    }

    private boolean mediaAudioWanted() {
        if (this.quickMediaPlaying) {
            return true;
        }
        try {
            AudioManager audioManager = (AudioManager) getSystemService("audio");
            return audioManager != null && audioManager.isMusicActive();
        } catch (Throwable th) {
            return false;
        }
    }

    private Drawable mediaButtonFace(float f, boolean z, boolean z2) {
        int i;
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setShape(1);
        if (z) {
            gradientDrawable.setColor(withAlpha(this.dockAccentColor, z2 ? 192 : 255));
            gradientDrawable.setStroke(Math.max(1, Math.round(f)), this.dockAccentColor);
        } else {
            if (this.dockUiLight) {
                i = z2 ? 605561388 : 253239852;
            } else {
                i = z2 ? 788529151 : 352321535;
            }
            gradientDrawable.setColor(i);
            gradientDrawable.setStroke(Math.max(1, Math.round(f)), this.dockUiLight ? 864721320 : 788529151);
        }
        return gradientDrawable;
    }

    private Drawable mediaGlyph(float f, boolean z, String str) {
        int iMax = Math.max(8, Math.round((z ? 48 : 40) * 0.46f * f));
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(iMax, iMax, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        Paint paint = new Paint(1);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(z ? -16379110 : dockLabelColor());
        float f2 = iMax / 24.0f;
        if ("prev".equals(str)) {
            canvas.drawRect(f2 * 6.0f, f2 * 6.0f, 8.0f * f2, f2 * 18.0f, paint);
            Path path = new Path();
            path.moveTo(f2 * 18.0f, 6.0f * f2);
            path.lineTo(f2 * 18.0f, 18.0f * f2);
            path.lineTo(9.5f * f2, 12.0f * f2);
            path.close();
            canvas.drawPath(path, paint);
        } else if ("next".equals(str)) {
            canvas.drawRect(16.0f * f2, f2 * 6.0f, f2 * 18.0f, f2 * 18.0f, paint);
            Path path2 = new Path();
            path2.moveTo(f2 * 6.0f, f2 * 6.0f);
            path2.lineTo(6.0f * f2, 18.0f * f2);
            path2.lineTo(14.5f * f2, 12.0f * f2);
            path2.close();
            canvas.drawPath(path2, paint);
        } else if ("pause".equals(str)) {
            canvas.drawRect(f2 * 6.0f, f2 * 5.0f, 10.0f * f2, f2 * 19.0f, paint);
            canvas.drawRect(14.0f * f2, f2 * 5.0f, f2 * 18.0f, f2 * 19.0f, paint);
        } else {
            Path path3 = new Path();
            path3.moveTo(f2 * 8.0f, 5.0f * f2);
            path3.lineTo(f2 * 8.0f, f2 * 19.0f);
            path3.lineTo(19.0f * f2, 12.0f * f2);
            path3.close();
            canvas.drawPath(path3, paint);
        }
        return new BitmapDrawable(getResources(), bitmapCreateBitmap);
    }

    private static String mimeType(String str) {
        String lowerCase = str.toLowerCase();
        if (lowerCase.endsWith(".html")) {
            return "text/html";
        }
        if (lowerCase.endsWith(".js")) {
            return "application/javascript";
        }
        if (lowerCase.endsWith(".css")) {
            return "text/css";
        }
        if (lowerCase.endsWith(".json")) {
            return "application/json";
        }
        if (lowerCase.endsWith(".wasm")) {
            return "application/wasm";
        }
        if (lowerCase.endsWith(".woff2")) {
            return "font/woff2";
        }
        if (lowerCase.endsWith(".png")) {
            return "image/png";
        }
        if (lowerCase.endsWith(".jpg") || lowerCase.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lowerCase.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lowerCase.endsWith(".glb")) {
            return "model/gltf-binary";
        }
        if (lowerCase.endsWith(".mp4")) {
            return "video/mp4";
        }
        return lowerCase.endsWith(".hdr") ? "application/octet-stream" : "application/octet-stream";
    }

    private void minimizeAppsOnlySlots() {
        String str = this.activePopupPackage;
        String str2 = this.activeMediaPackage;
        if (str != null && !str.isEmpty()) {
            int iFindTaskIdForPackage = this.activePopupTaskId >= 0 ? this.activePopupTaskId : findTaskIdForPackage(str);
            if (this.pinBoundsRunnable != null) {
                this.mainHandler.removeCallbacks(this.pinBoundsRunnable);
            }
            removeTaskById(iFindTaskIdForPackage);
            clearLeftSlot();
        }
        if (str2 != null && !str2.isEmpty()) {
            int iFindTaskIdForPackage2 = this.activeMediaTaskId >= 0 ? this.activeMediaTaskId : findTaskIdForPackage(str2);
            if (this.pinMediaBoundsRunnable != null) {
                this.mainHandler.removeCallbacks(this.pinMediaBoundsRunnable);
            }
            removeTaskById(iFindTaskIdForPackage2);
            clearRightSlot();
        }
        Log.w("H6Viewer", "Minimized Dual App slots");
    }

    private boolean moveTaskToFrontNoAnim(int lastRaisedTaskId) {
        if (lastRaisedTaskId < 0) {
            return false;
        }
        long jUptimeMillis = SystemClock.uptimeMillis();
        if (lastRaisedTaskId == this.lastRaisedTaskId && jUptimeMillis - this.lastRaiseCompletedMs < 350) {
            return true;
        }
        this.lastRaisedTaskId = lastRaisedTaskId;
        this.lastRaiseCompletedMs = jUptimeMillis;
        try {
            ActivityManager activityManager = (ActivityManager) getSystemService("activity");
            if (activityManager == null) {
                return false;
            }
            activityManager.moveTaskToFront(lastRaisedTaskId, 2, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle());
            Log.w("H6Viewer", "Raise: moveTaskToFront task=" + lastRaisedTaskId + " ok");
            return true;
        } catch (Exception e) {
            Log.w("H6Viewer", "Raise: moveTaskToFront task=" + lastRaisedTaskId + " refused (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
            return false;
        }
    }

    private int navRailReservePx() {
        int identifier = getResources().getIdentifier("navigation_bar_width", "dimen", "android");
        int dimensionPixelSize = identifier > 0 ? getResources().getDimensionPixelSize(identifier) : 0;
        float f = getResources().getDisplayMetrics().density;
        int iMax = Math.max(Math.max(dimensionPixelSize, this.widestNavInsetPx), Math.round((f <= 0.0f ? 1.0f : f) * 128.0f));
        if (iMax != this.loggedNavReservePx) {
            this.loggedNavReservePx = iMax;
            Log.w("H6Viewer", "Nav rail reserve=" + iMax + " (res=" + dimensionPixelSize + " observed=" + this.widestNavInsetPx + ")");
        }
        return iMax;
    }

    private static boolean needsUtf8(String str) {
        String lowerCase = str.toLowerCase();
        return lowerCase.endsWith(".html") || lowerCase.endsWith(".js") || lowerCase.endsWith(".css") || lowerCase.endsWith(".json") || lowerCase.endsWith(".svg");
    }

    private Drawable normalizeAdaptiveIcon(Drawable drawable, int i) {
        if (drawable == null || i <= 0 || Build.VERSION.SDK_INT < 26 || !(drawable instanceof AdaptiveIconDrawable)) {
            return drawable;
        }
        try {
            AdaptiveIconDrawable adaptiveIconDrawable = (AdaptiveIconDrawable) drawable;
            Drawable background = adaptiveIconDrawable.getBackground();
            Drawable foreground = adaptiveIconDrawable.getForeground();
            float fAdaptiveIconFillScale = adaptiveIconFillScale(foreground);
            Bitmap bitmapCreateBitmap = Bitmap.createBitmap(i, i, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmapCreateBitmap);
            int i2 = -Math.round((i * (fAdaptiveIconFillScale - 1.0f)) / 2.0f);
            int i3 = i - i2;
            if (background != null) {
                background.setBounds(i2, i2, i3, i3);
                background.draw(canvas);
            }
            if (foreground != null) {
                foreground.setBounds(i2, i2, i3, i3);
                foreground.draw(canvas);
            }
            return new BitmapDrawable(getResources(), bitmapCreateBitmap);
        } catch (Exception e) {
            Log.e("H6Viewer", "Error normalizing adaptive icon", e);
            return drawable;
        }
    }

    private String normalizeDockSurfaceMode(String str) {
        return "cards".equals(str) ? "cards" : "launcher";
    }

    private static String normalizeVehicleLevel(String str) {
        if (str == null || str.isEmpty()) {
            return null;
        }
        for (int i = 0; i < str.length(); i++) {
            char cCharAt = str.charAt(i);
            if (cCharAt < '0' || cCharAt > '9') {
                return null;
            }
        }
        try {
            int i2 = Integer.parseInt(str);
            if (i2 < 0 || i2 > 100) {
                return null;
            }
            return Integer.toString(i2);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void notifyAppsCatalogChanged() {
        if (this.webView == null) {
            return;
        }
        this.webView.post(() -> {
            this.webView.evaluateJavascript("try{if(window.__app&&typeof window.__app._invalidateAppsCatalog==='function'){window.__app._invalidateAppsCatalog();}}catch(e){}", null);
        });
    }

    private void notifyMediaNowPlaying(JSONObject jSONObject) {
        if (this.webView == null || jSONObject == null) {
            return;
        }
        final String script4 = "try{if(window.onMediaNowPlaying){window.onMediaNowPlaying(" + jSONObject.toString() + ");}}catch(e){}";
        this.webView.post(() -> {
            this.webView.evaluateJavascript(script4, null);
        });
    }

    private void notifyMediaPosition(long j) {
        if (this.webView == null) {
            return;
        }
        final String script5 = "try{if(window.onMediaPosition){window.onMediaPosition(" + j + ");}}catch(e){}";
        this.webView.post(() -> {
            this.webView.evaluateJavascript(script5, null);
        });
    }

    private void notifyViewerShellLayout() {
        if (this.webView == null) {
            return;
        }
        if (this.rootLayout == null || this.rootLayout.getWidth() <= 0 || this.rootLayout.getHeight() <= 0) {
            this.mainHandler.postDelayed(() -> this.notifyViewerShellLayout(), 300L);
            return;
        }
        applyChromeSideInsets();
        applyLauncherStripBottomMargin();
        boolean z = (this.activePopupPackage == null || this.activePopupPackage.isEmpty()) ? false : true;
        String str = (this.activeMediaPackage == null || this.activeMediaPackage.isEmpty()) ? "\"idle\"" : "\"app\"";
        applyLauncherFocusPolicy();
        String str2 = this.nextLaunchLeft ? "left" : "right";
        if ("left".equals(this.launchSidePref) || "right".equals(this.launchSidePref)) {
            str2 = this.launchSidePref;
        }
        final String script6 = "try{if(window.onAndroidShellLayout){window.onAndroidShellLayout({left:" + z + ",right:" + str + ",mode:\"" + this.shellMode + "\",launchSide:\"" + this.launchSidePref + "\",nextSide:\"" + str2 + "\",lastSide:\"" + ((this.lastLaunchSide == null || this.lastLaunchSide.isEmpty()) ? str2 : this.lastLaunchSide) + "\",leftUse:\"" + this.leftSlotUse + "\",rightUse:\"" + this.rightSlotUse + "\",leftBounds:" + boundsToCssJson(leftFreeformBounds()) + ",rightBounds:" + boundsToCssJson(rightFreeformBounds()) + ",safeLeft:" + cssPx(pageSideInsetPx(false)) + ",safeRight:" + cssPx(pageSideInsetPx(true)) + ",safeTop:" + (cssPx(pageTopInsetPx()) + 22.0f) + ",safeBottom:" + cssPx(compensatedBottomMarginPx(dockReservePx())) + ",launcherBottom:" + cssPx(compensatedBottomMarginPx(chromeBottomGapPx())) + "," + popupVerticalBoundsToCssFragment() + ",uiMode:\"" + this.uiMode + "\",splitRatio:\"" + this.splitRatio + "\"});}else if(window.onAndroidLauncherPopup){window.onAndroidLauncherPopup(" + z + ");}else if(window.__app&&window.__app.applyLauncherPopupLayout){window.__app.applyLauncherPopupLayout(" + z + ");}}catch(e){}";
        this.webView.post(() -> {
            this.webView.evaluateJavascript(script6, null);
        });
    }

    private void onAppsFabAttached(String str) {
        refreshAppsFabMenu();
        updateSlotAnchors();
        if (this.activePopupPackage != null && !this.activePopupPackage.isEmpty()) {
            pinSlotToBounds(this.activePopupPackage, this.activePopupComponent, this.activePopupTaskId, leftFreeformBounds(), false);
        }
        if (this.activeMediaPackage != null && !this.activeMediaPackage.isEmpty()) {
            pinSlotToBounds(this.activeMediaPackage, this.activeMediaComponent, this.activeMediaTaskId, rightFreeformBounds(), true);
        }
        notifyViewerShellLayout();
        Log.w("H6Viewer", "APP+APP FAB attached (" + str + ") at " + this.appsFabPosX + "," + this.appsFabPosY);
    }

    private int overlayImportance(String str) {
        List<ActivityManager.RunningAppProcessInfo> runningAppProcesses;
        try {
            ActivityManager activityManager = (ActivityManager) getSystemService("activity");
            if (activityManager != null && (runningAppProcesses = activityManager.getRunningAppProcesses()) != null && !runningAppProcesses.isEmpty()) {
                boolean z = false;
                for (ActivityManager.RunningAppProcessInfo runningAppProcessInfo : runningAppProcesses) {
                    if (runningAppProcessInfo.pkgList != null) {
                        for (String str2 : runningAppProcessInfo.pkgList) {
                            if (str2 != null) {
                                if (!getPackageName().equals(str2)) {
                                    z = true;
                                }
                                if (str.equals(str2)) {
                                    return runningAppProcessInfo.importance <= 200 ? 1 : 0;
                                }
                            }
                        }
                    }
                }
                return !z ? -1 : 0;
            }
            return -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private int overlayTaskState(String str, int i) {
        int i2;
        if (str != null && !str.isEmpty()) {
            int iOverlayVisibleInDumpsys = overlayVisibleInDumpsys(str);
            if (iOverlayVisibleInDumpsys >= 0) {
                return iOverlayVisibleInDumpsys;
            }
            int iOverlayVisibleViaSystemService = overlayVisibleViaSystemService(str);
            if (iOverlayVisibleViaSystemService >= 0) {
                return iOverlayVisibleViaSystemService;
            }
            int iOverlayImportance = overlayImportance(str);
            if (iOverlayImportance >= 0) {
                return iOverlayImportance;
            }
            boolean z = false;
            try {
                ActivityManager activityManager = (ActivityManager) getSystemService("activity");
                if (activityManager == null) {
                    return -1;
                }
                List<ActivityManager.RunningTaskInfo> runningTasks = activityManager.getRunningTasks(32);
                if (runningTasks != null) {
                    for (ActivityManager.RunningTaskInfo runningTaskInfo : runningTasks) {
                        if (runningTaskInfo != null) {
                            ComponentName componentName = runningTaskInfo.topActivity;
                            ComponentName componentName2 = runningTaskInfo.baseActivity;
                            String packageName = componentName != null ? componentName.getPackageName() : componentName2 != null ? componentName2.getPackageName() : null;
                            if (packageName != null) {
                                if (!getPackageName().equals(packageName)) {
                                    z = true;
                                }
                                if (str.equals(packageName) || (i >= 0 && runningTaskInfo.id == i)) {
                                    return runningTaskIsVisible(runningTaskInfo) ? 1 : 0;
                                }
                            }
                        }
                    }
                    i2 = 0;
                } else {
                    i2 = 0;
                }
                if (z) {
                    return i2;
                }
                return -1;
            } catch (Exception e) {
                Log.w("H6Viewer", "overlayTaskState failed", e);
                return -1;
            }
        }
        return 0;
    }

    private int overlayVisibleInDumpsys(String str) {
        Process processStart = null;
        try {
            try {
                processStart = new ProcessBuilder("/system/bin/dumpsys", "activity", "activities").redirectErrorStream(true).start();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(processStart.getInputStream()), 8192);
                StringBuilder sb = new StringBuilder();
                long jCurrentTimeMillis = System.currentTimeMillis() + 900;
                do {
                    String line = bufferedReader.readLine();
                    if (line == null) {
                        break;
                    }
                    sb.append(line).append('\n');
                    if (sb.length() > 350000) {
                        break;
                    }
                } while (System.currentTimeMillis() <= jCurrentTimeMillis);
                try {
                    processStart.destroy();
                } catch (Exception e) {
                }
                String string = sb.toString();
                if (string.length() < 40 || string.contains("Permission Denial")) {
                    if (processStart != null) {
                        try {
                            processStart.destroy();
                        } catch (Exception e2) {
                        }
                    }
                    return -1;
                }
                boolean z = false;
                int length = 0;
                while (true) {
                    try {
                        try {
                            int iIndexOf = string.indexOf(str, length);
                            if (iIndexOf < 0) {
                                if (z) {
                                    if (processStart != null) {
                                        try {
                                            processStart.destroy();
                                        } catch (Exception e3) {
                                        }
                                    }
                                    return 0;
                                }
                                if (processStart != null) {
                                    try {
                                        processStart.destroy();
                                    } catch (Exception e4) {
                                    }
                                }
                                return -1;
                            }
                            z = true;
                            if (string.substring(Math.max(0, iIndexOf - 400), Math.min(string.length(), iIndexOf + 600)).contains("visible=true")) {
                                if (processStart != null) {
                                    try {
                                        processStart.destroy();
                                    } catch (Exception e5) {
                                    }
                                }
                                return 1;
                            }
                            length = str.length() + iIndexOf;
                        } catch (Exception e6) {
                            Log.w("H6Viewer", "dumpsys overlay probe failed", e6);
                            if (processStart != null) {
                                try {
                                    processStart.destroy();
                                } catch (Exception e7) {
                                }
                            }
                            return -1;
                        }
                    } catch (Throwable th) {
                        Process process = processStart;
                        if (process != null) {
                            try {
                                process.destroy();
                            } catch (Exception e8) {
                            }
                        }
                        throw th;
                    }
                }
            } catch (Exception e9) {
                Log.w("H6Viewer", "dumpsys overlay probe failed", e9);
                return -1;
            }
        } catch (Throwable th3) {
            Log.w("H6Viewer", "dumpsys overlay probe failed", th3);
            return -1;
        }
    }

    private int overlayVisibleViaSystemService(String str) {
        List<?> listSystemTaskList = systemTaskList();
        if (listSystemTaskList == null) {
            return -1;
        }
        boolean z = false;
        for (Object obj : listSystemTaskList) {
            if (obj instanceof ActivityManager.RunningTaskInfo) {
                ActivityManager.RunningTaskInfo runningTaskInfo = (ActivityManager.RunningTaskInfo) obj;
                ComponentName componentName = runningTaskInfo.topActivity;
                ComponentName componentName2 = runningTaskInfo.baseActivity;
                String packageName = componentName != null ? componentName.getPackageName() : componentName2 != null ? componentName2.getPackageName() : null;
                if (packageName != null) {
                    if (!getPackageName().equals(packageName)) {
                        z = true;
                    }
                    if (str.equals(packageName)) {
                        return runningTaskIsVisible(runningTaskInfo) ? 1 : 0;
                    }
                } else {
                    continue;
                }
            }
        }
        return !z ? -1 : 0;
    }

    private int overscanBottomPx() {
        Point point = new Point();
        try {
            getWindowManager().getDefaultDisplay().getRealSize(point);
        } catch (Exception e) {
        }
        if (point.y <= 0) {
            return 0;
        }
        return Math.max(0, point.y - pageOriginRect().bottom);
    }

    private Rect pageOriginRect() {
        Point point = new Point();
        try {
            getWindowManager().getDefaultDisplay().getRealSize(point);
        } catch (Exception e) {
        }
        if (point.x <= 0 || point.y <= 0) {
            return usableDisplayRect();
        }
        if (this.rootLayout == null || this.rootLayout.getWidth() <= 0 || this.rootLayout.getHeight() <= 0) {
            return !laidOutFullBleed() ? usableDisplayRect() : new Rect(0, 0, point.x, point.y);
        }
        int[] iArr = new int[2];
        this.rootLayout.getLocationOnScreen(iArr);
        return new Rect(iArr[0], iArr[1], iArr[0] + this.rootLayout.getWidth(), iArr[1] + this.rootLayout.getHeight());
    }

    private int pageSideInsetPx(boolean z) {
        Point point = new Point();
        try {
            getWindowManager().getDefaultDisplay().getRealSize(point);
        } catch (Exception e) {
        }
        int iNavRailReservePx = navRailReservePx();
        Rect rectPageOriginRect = pageOriginRect();
        return Math.max(0, iNavRailReservePx - (z ? Math.max(0, point.x - rectPageOriginRect.right) : Math.max(0, rectPageOriginRect.left)));
    }

    private int pageTopInsetPx() {
        if (statusBarShown()) {
            return Math.max(0, statusBarHeightPx() - Math.max(0, pageOriginRect().top));
        }
        return 0;
    }

    private int panelHeightPx() {
        Point point = new Point();
        try {
            getWindowManager().getDefaultDisplay().getRealSize(point);
        } catch (Exception e) {
        }
        return point.y > 0 ? point.y : getResources().getDisplayMetrics().heightPixels;
    }

    private static int parseCssColor(String str, int i) {
        if (str == null) {
            return i;
        }
        String strTrim = str.trim();
        if (!strTrim.startsWith("#")) {
            return i;
        }
        String strSubstring = strTrim.substring(1);
        if (strSubstring.length() == 3) {
            StringBuilder sb = new StringBuilder();
            for (int i2 = 0; i2 < 3; i2++) {
                sb.append(strSubstring.charAt(i2)).append(strSubstring.charAt(i2));
            }
            strSubstring = sb.toString();
        }
        if (strSubstring.length() != 6) {
            return i;
        }
        try {
            return Integer.parseInt(strSubstring, 16) | (-16777216);
        } catch (NumberFormatException e) {
            return i;
        }
    }

    private List<QuickMenuRow> parseEditMenu(JSONArray jSONArray) {
        if (jSONArray == null || jSONArray.length() == 0) {
            return Collections.emptyList();
        }
        ArrayList arrayList = new ArrayList();
        int iMin = Math.min(jSONArray.length(), 10);
        for (int i = 0; i < iMin; i++) {
            JSONObject jSONObjectOptJSONObject = jSONArray.optJSONObject(i);
            if (jSONObjectOptJSONObject != null) {
                String strCleanBottomCardText = cleanBottomCardText(jSONObjectOptJSONObject.optString("label", ""), 28);
                String strTrim = jSONObjectOptJSONObject.optString("command", "").trim();
                if (!strCleanBottomCardText.isEmpty() && (isCardActionCommand(strTrim) || "openClockSettings".equals(strTrim))) {
                    arrayList.add(new QuickMenuRow(strCleanBottomCardText, strTrim, jSONObjectOptJSONObject.optBoolean("selected", false)));
                }
            }
        }
        return arrayList;
    }

    private static int[] parseEnergyBars(String str) {
        if (str == null || str.trim().isEmpty()) {
            return new int[0];
        }
        String[] strArrSplit = str.split(",");
        int[] iArr = new int[Math.min(strArrSplit.length, 31)];
        for (int i = 0; i < iArr.length; i++) {
            try {
                iArr[i] = Math.max(0, Math.min(100, Integer.parseInt(strArrSplit[i].trim())));
            } catch (NumberFormatException e) {
                iArr[i] = 0;
            }
        }
        return iArr;
    }

    private List<QuickMenuRow> parseQuickMenu(JSONArray jSONArray) {
        if (jSONArray == null || jSONArray.length() == 0) {
            return Collections.emptyList();
        }
        ArrayList arrayList = new ArrayList();
        int iMin = Math.min(jSONArray.length(), 12);
        for (int i = 0; i < iMin; i++) {
            JSONObject jSONObjectOptJSONObject = jSONArray.optJSONObject(i);
            if (jSONObjectOptJSONObject != null) {
                String strCleanBottomCardText = cleanBottomCardText(jSONObjectOptJSONObject.optString("label", ""), 28);
                String strTrim = jSONObjectOptJSONObject.optString("command", "").trim();
                if (!strCleanBottomCardText.isEmpty() && !strTrim.isEmpty() && (BOTTOM_CARD_ACTIONS.contains(strTrim) || isDrivingSetCommand(strTrim))) {
                    arrayList.add(new QuickMenuRow(strCleanBottomCardText, strTrim, jSONObjectOptJSONObject.optBoolean("selected", false)));
                }
            }
        }
        return arrayList;
    }

    private void persistDockSurfaceMode() {
        getSharedPreferences("opengw_lancador", 0).edit().putString("dockSurfaceMode", this.dockSurfaceMode).apply();
    }

    private void pinOverlayAlwaysOnTop(String str, int i, boolean z) {
        if (str == null || str.isEmpty()) {
            return;
        }
        int iFindTaskIdForPackage = i >= 0 ? i : findTaskIdForPackage(str);
        if (iFindTaskIdForPackage < 0) {
            requestTaskId(str, z);
            return;
        }
        if (z) {
            this.activeMediaTaskId = iFindTaskIdForPackage;
        } else {
            this.activePopupTaskId = iFindTaskIdForPackage;
        }
        if (setTaskAlwaysOnTop(iFindTaskIdForPackage, true)) {
            return;
        }
        moveTaskToFrontNoAnim(iFindTaskIdForPackage);
    }

    private void pinSlotToBounds(String str, ComponentName componentName, int i, Rect rect, boolean z) {
        if (str == null || str.isEmpty() || rect == null) {
            return;
        }
        Rect rect2 = new Rect(rect);
        int iFindTaskIdForPackage = i;
        if (iFindTaskIdForPackage < 0) {
            iFindTaskIdForPackage = findTaskIdForPackage(str);
        }
        if (iFindTaskIdForPackage >= 0) {
            if (z) {
                this.activeMediaTaskId = iFindTaskIdForPackage;
            } else {
                this.activePopupTaskId = iFindTaskIdForPackage;
            }
            resizeTaskToBounds(iFindTaskIdForPackage, rect2);
            setTaskAlwaysOnTop(iFindTaskIdForPackage, true);
        }
        schedulePinPopupBounds(str, componentName, rect2, z);
        requestTaskBounds(str, rect2);
        this.mainHandler.postDelayed(() -> {
            if (str.equals(z ? this.activeMediaPackage : this.activePopupPackage)) {
                requestTaskBounds(str, rect2);
            }
        }, 400L);
        this.mainHandler.postDelayed(() -> {
            if (str.equals(z ? this.activeMediaPackage : this.activePopupPackage)) {
                requestTaskBounds(str, rect2);
            }
        }, 1000L);
    }

    private String pinnedLabel(String str, PackageManager packageManager, ResolveInfo resolveInfo) {
        if ("com.beantechs.vehiclecenter".equals(str)) {
            return "Configurações do Veículo";
        }
        if ("com.beantechs.settings".equals(str)) {
            return "Configurações do Sistema";
        }
        if ("com.beantechs.energyassistant".equals(str)) {
            return "Energy Assistant";
        }
        return "com.beantechs.launcher".equals(str) ? "GWM Home" : launcherLabel(packageManager, resolveInfo);
    }

    private void populateQuickCardsRow(LinearLayout linearLayout, float f) {
        linearLayout.removeAllViews();
        this.quickCardViews.clear();
        this.quickCardHosts.clear();
        this.lastDrivingWash.clear();
        this.quickClimateValue = null;
        this.quickConsumptionValue = null;
        this.quickCardValues.clear();
        this.quickCardDetails.clear();
        this.quickCardGraphics.clear();
        this.quickClockCards.clear();
        this.quickCardDemoBadges.clear();
        this.quickMediaArt = null;
        this.quickMediaHasArt = false;
        this.quickMediaTitle = null;
        this.quickMediaArtist = null;
        this.quickMediaPlayPause = null;
        this.quickMediaAppIcon = null;
        this.quickMediaAppRow = null;
        this.quickMediaChip = null;
        if (this.quickMediaBars != null) {
            this.quickMediaBars.setPlaying(false);
        }
        this.quickMediaBars = null;
        this.quickMediaButtons.clear();
        linearLayout.addView(makeQuickWorkspaceCard(f));
        if (!this.bottomCardsConfigured) {
            View viewMakeQuickTextCard = makeQuickTextCard(f, 218, "CLIMATE", "— °C  ·  Fan —  ·  AUTO —", "Climate summary. Opens the climate page when available", view2 -> {
                callViewerDock("openClimate");
            });
            this.quickClimateValue = (TextView) viewMakeQuickTextCard.findViewWithTag("quickValue");
            linearLayout.addView(viewMakeQuickTextCard);
            View viewMakeQuickTextCard2 = makeQuickTextCard(f, 196, "ENERGIA", "—", "Resumo de energia. Abre a tela de energia", view3 -> {
                callViewerDock("openConsumption");
            });
            this.quickConsumptionValue = (TextView) viewMakeQuickTextCard2.findViewWithTag("quickValue");
            linearLayout.addView(viewMakeQuickTextCard2);
            linearLayout.addView(makeQuickMediaCard(f, null));
            replayMediaPayload();
            return;
        }
        int iMin = Math.min(this.bottomCards.size(), this.bottomCardLimit);
        for (int i = 0; i < iMin; i++) {
            BottomCardDescriptor bottomCardDescriptor = this.bottomCards.get(i);
            if ("media".equals(bottomCardDescriptor.id)) {
                View viewMakeQuickMediaCard = makeQuickMediaCard(f, bottomCardDescriptor);
                viewMakeQuickMediaCard.setTag("bottomCard:" + bottomCardDescriptor.id);
                this.quickCardHosts.put(bottomCardDescriptor.id, viewMakeQuickMediaCard);
                linearLayout.addView(viewMakeQuickMediaCard);
            } else {
                View viewMakeQuickVisualCard = makeQuickVisualCard(f, bottomCardDescriptor, view -> {
                    if (bottomCardDescriptor.menu.isEmpty()) {
                        callViewerDock(bottomCardDescriptor.action);
                    } else {
                        showQuickMenu(view, bottomCardDescriptor);
                    }
                });
                viewMakeQuickVisualCard.setTag("bottomCard:" + bottomCardDescriptor.id);
                TextView textView = (TextView) viewMakeQuickVisualCard.findViewWithTag("quickValue");
                if (textView != null) {
                    this.quickCardValues.put(bottomCardDescriptor.id, textView);
                }
                linearLayout.addView(viewMakeQuickVisualCard);
            }
        }
        replayMediaPayload();
    }

    private String popupVerticalBoundsToCssFragment() {
        Rect rectPageOriginRect = pageOriginRect();
        int iMax = Math.max(0, Math.max(statusBarHeightPx(), this.widestTopInsetPx) - rectPageOriginRect.top);
        int iHeight = rectPageOriginRect.height();
        if (this.stripContainer != null && this.stripContainer.getHeight() > 0) {
            int[] iArr = new int[2];
            this.stripContainer.getLocationOnScreen(iArr);
            iHeight = Math.max(iMax, (iArr[1] - rectPageOriginRect.top) - Math.round(getResources().getDisplayMetrics().density * 12.0f));
        }
        return "popupTop:" + cssPx(iMax) + ",popupBottom:" + cssPx(iHeight);
    }

    private boolean prefersWindow(String str) {
        return str != null && this.windowPackages.contains(str);
    }

    private void preloadSplashHoldFrame() {
        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        try {
            try {
                File file = new File(getCacheDir(), "app-splash.mp4");
                if (!file.exists() || file.length() == 0) {
                    InputStream inputStreamOpen = getAssets().open("www/assets/app-splash.mp4");
                    try {
                        FileOutputStream fileOutputStream = new FileOutputStream(file);
                        try {
                            byte[] bArr = new byte[8192];
                            while (true) {
                                int i = inputStreamOpen.read(bArr);
                                if (i <= 0) {
                                    break;
                                } else {
                                    fileOutputStream.write(bArr, 0, i);
                                }
                            }
                            fileOutputStream.close();
                            if (inputStreamOpen != null) {
                                inputStreamOpen.close();
                            }
                        } catch (Throwable th2) {
                            try {
                                fileOutputStream.close();
                            } catch (Throwable th3) {
                                th2.addSuppressed(th3);
                            }
                            throw th2;
                        }
                    } catch (Throwable th4) {
                        if (inputStreamOpen != null) {
                            inputStreamOpen.close();
                        }
                        throw th4;
                    }
                }
                mediaMetadataRetriever.setDataSource(file.getAbsolutePath());
                long jMax = 15000000;
                String strExtractMetadata = mediaMetadataRetriever.extractMetadata(9);
                if (strExtractMetadata != null) {
                    try {
                        jMax = Math.max(0L, Long.parseLong(strExtractMetadata) - 80) * 1000;
                    } catch (NumberFormatException e) {
                    }
                }
                Bitmap frameAtTime = mediaMetadataRetriever.getFrameAtTime(jMax, 3);
                if (frameAtTime != null) {
                    this.splashHoldFrame = frameAtTime;
                }
            } catch (Exception e2) {
                Log.w("H6Viewer", "Could not extract splash hold frame", e2);
            }
            try {
                mediaMetadataRetriever.release();
            } catch (Exception e3) {
            }
        } catch (Throwable th5) {
            try {
                mediaMetadataRetriever.release();
            } catch (Exception e4) {
            }
            throw th5;
        }
    }

    private Bitmap projectionIconBitmap(String str) {
        if (!ProjectionPresence.isProjectionPackage(str)) {
            return null;
        }
        boolean zContains = str.toLowerCase().contains("carplay");
        Drawable drawable = null;
        if (this.projectionPresence != null) {
            drawable = this.projectionPresence.iconFor(zContains ? ProjectionPresence.Kind.CARPLAY : ProjectionPresence.Kind.ANDROID_AUTO);
        }
        if (drawable == null) {
            try {
                drawable = getDrawable(zContains ? R.drawable.ic_carplay_default : R.drawable.ic_android_auto_default);
            } catch (Exception e) {
            }
        }
        if (drawable == null) {
            return null;
        }
        try {
            Bitmap bitmapCreateBitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmapCreateBitmap);
            drawable.setBounds(0, 0, 96, 96);
            drawable.draw(canvas);
            return bitmapCreateBitmap;
        } catch (Exception e2) {
            return null;
        }
    }

    private int quickVisualCardWidthDp(String str) {
        if ("power".equals(str)) {
            return 318;
        }
        if ("navigation".equals(str) || "roof".equals(str)) {
            return 286;
        }
        if ("status".equals(str) || "tires".equals(str)) {
            return 270;
        }
        if ("range".equals(str) || "consumption".equals(str)) {
            return 258;
        }
        return "clock".equals(str) ? 224 : 238;
    }

    private String quickVisualDetail(BottomCardDescriptor bottomCardDescriptor) {
        if ("power".equals(bottomCardDescriptor.id)) {
            return bottomCardDescriptor.metricA + "\n" + bottomCardDescriptor.metricB + "\n" + bottomCardDescriptor.secondary;
        }
        if ("tires".equals(bottomCardDescriptor.id)) {
            String strLabelTirePair = labelTirePair("RL", "RR", bottomCardDescriptor.metricA);
            if (bottomCardDescriptor.secondary.isEmpty()) {
                return strLabelTirePair;
            }
            return strLabelTirePair + (strLabelTirePair.isEmpty() ? "" : "\n") + bottomCardDescriptor.secondary;
        }
        String str = bottomCardDescriptor.metricA;
        if (!bottomCardDescriptor.metricB.isEmpty()) {
            str = str + (str.isEmpty() ? "" : "  ·  ") + bottomCardDescriptor.metricB;
        }
        if (bottomCardDescriptor.secondary.isEmpty()) {
            return str;
        }
        return bottomCardDescriptor.secondary + (str.isEmpty() ? "" : "\n" + str);
    }

    private String quickVisualPrimary(BottomCardDescriptor bottomCardDescriptor) {
        String str = bottomCardDescriptor.primary.isEmpty() ? bottomCardDescriptor.value : bottomCardDescriptor.primary;
        return "tires".equals(bottomCardDescriptor.id) ? labelTirePair("FL", "FR", str) : str;
    }

    private List<View> railCardViews() {
        ArrayList arrayList = new ArrayList();
        if (this.quickCardsRow == null) {
            return arrayList;
        }
        for (int i = 0; i < this.quickCardsRow.getChildCount(); i++) {
            View childAt = this.quickCardsRow.getChildAt(i);
            Object tag = childAt.getTag();
            if ((tag instanceof String) && ((String) tag).startsWith("bottomCard:")) {
                arrayList.add(childAt);
            }
        }
        return arrayList;
    }

    private int railDropIndex(View view) {
        float left = view.getLeft() + view.getTranslationX() + (view.getWidth() / 2.0f);
        int i = 0;
        for (View view2 : railCardViews()) {
            if (view2 != view && left > view2.getLeft() + (view2.getWidth() / 2.0f)) {
                i++;
            }
        }
        return i;
    }

    private void raiseOverlayTask(String str, int i, ComponentName componentName, boolean z) {
        if (str == null || str.isEmpty()) {
            return;
        }
        int iFindTaskIdForPackage = i >= 0 ? i : findTaskIdForPackage(str);
        if (iFindTaskIdForPackage >= 0) {
            if (z) {
                this.activeMediaTaskId = iFindTaskIdForPackage;
            } else {
                this.activePopupTaskId = iFindTaskIdForPackage;
            }
            if (moveTaskToFrontNoAnim(iFindTaskIdForPackage)) {
                return;
            }
            if (z) {
                this.activeMediaTaskId = -1;
            } else {
                this.activePopupTaskId = -1;
            }
        }
        requestTaskId(str, z);
        restartIntoSlot(str, componentName, z);
    }

    private void raiseOverlayTasksNow() {
        if (this.chromeOnTop || !hasOverlayWindow()) {
            return;
        }
        raiseOverlayTask(this.activePopupPackage, this.activePopupTaskId, this.activePopupComponent, false);
        if (this.activeMediaPackage != null && !this.activeMediaPackage.equals(this.activePopupPackage)) {
            raiseOverlayTask(this.activeMediaPackage, this.activeMediaTaskId, this.activeMediaComponent, true);
        }
        this.overlayRaiseGraceUntilMs = SystemClock.uptimeMillis() + 1200;
    }

    private void raiseOverlayTasksSoon() {
        raiseOverlayTasksSoon(0L);
    }

    private void raiseOverlayTasksSoon(long j) {
        if (this.chromeOnTop) {
            return;
        }
        if (this.raiseOverlayRunnable == null) {
            this.raiseOverlayRunnable = () -> this.raiseOverlayTasksNow();
        }
        this.mainHandler.removeCallbacks(this.raiseOverlayRunnable);
        this.overlayRaiseGraceUntilMs = SystemClock.uptimeMillis() + 1600 + j;
        this.mainHandler.postDelayed(this.raiseOverlayRunnable, 60 + j);
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] bArr = new byte[16384];
        while (true) {
            int i = inputStream.read(bArr);
            if (i == -1) {
                return byteArrayOutputStream.toByteArray();
            }
            byteArrayOutputStream.write(bArr, 0, i);
        }
    }

    private String readUiModePref() {
        String string = getSharedPreferences("opengw_lancador", 0).getString("uiMode", "floating");
        return ("inset".equals(string) || "floating".equals(string) || "full".equals(string)) ? string : "floating";
    }

    private void rebindDockPackage(String str) {
        ResolveInfo resolveInfo;
        if ("__gwm_hub".equals(str)) {
            bindGwmHub();
            updatePinnedVisibility();
            notifyAppsCatalogChanged();
            return;
        }
        MotionTrailLayout motionTrailLayout = this.dockItemsByPackage.get(str);
        if (motionTrailLayout != null) {
            PackageManager packageManager = getPackageManager();
            Intent intent = new Intent("android.intent.action.MAIN", (Uri) null);
            intent.addCategory("android.intent.category.LAUNCHER");
            List<ResolveInfo> listQueryIntentActivities = packageManager.queryIntentActivities(intent, 0);
            Iterator<ResolveInfo> it = (listQueryIntentActivities == null ? java.util.Collections.<ResolveInfo>emptyList() : listQueryIntentActivities).iterator();
            while (true) {
                if (!it.hasNext()) {
                    resolveInfo = null;
                    break;
                }
                ResolveInfo next = it.next();
                if (next != null && next.activityInfo != null && str.equals(next.activityInfo.packageName)) {
                    resolveInfo = next;
                    break;
                }
            }
            String strLauncherLabel = resolveInfo != null ? launcherLabel(packageManager, resolveInfo) : pinnedLabel(str, packageManager, null);
            bindDockItem(motionTrailLayout, resolveInfo != null ? iconForLauncherApp(packageManager, resolveInfo) : launcherIconForPackage(str), strLauncherLabel, view -> {
                launchAppForPackage(str, strLauncherLabel);
            }, str);
            if (isUserHidden(str)) {
                motionTrailLayout.setVisibility(8);
            }
        }
        refreshRecentSlots();
        if (str != null && str.equals(this.quickMediaPackage)) {
            replayMediaPayload();
        }
        notifyAppsCatalogChanged();
    }

    private void rebuildQuickCardsRow() {
        if (this.quickCardsRow == null) {
            return;
        }
        dismissQuickMenu();
        populateQuickCardsRow(this.quickCardsRow, getResources().getDisplayMetrics().density);
        bindRailEditLongPress();
        refreshQuickCardsTheme();
        if (this.railEditMode) {
            applyRailEdit(true);
        }
    }

    private static int recentTaskIdIfPackage(ActivityManager.RecentTaskInfo recentTaskInfo, String str) {
        if (recentTaskInfo == null) {
            return -1;
        }
        ComponentName componentName = recentTaskInfo.origActivity;
        ComponentName component = recentTaskInfo.baseIntent != null ? recentTaskInfo.baseIntent.getComponent() : null;
        ComponentName componentNameRecentTaskRealActivity = recentTaskRealActivity(recentTaskInfo);
        if ((componentName == null || !str.equals(componentName.getPackageName())) && ((componentNameRecentTaskRealActivity == null || !str.equals(componentNameRecentTaskRealActivity.getPackageName())) && (component == null || !str.equals(component.getPackageName())))) {
            return -1;
        }
        return recentTaskInfo.persistentId > 0 ? recentTaskInfo.persistentId : recentTaskInfo.id;
    }

    private static ComponentName recentTaskRealActivity(ActivityManager.RecentTaskInfo recentTaskInfo) {
        try {
            Object obj = ActivityManager.RecentTaskInfo.class.getField("realActivity").get(recentTaskInfo);
            if (obj instanceof ComponentName) {
                return (ComponentName) obj;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private void refreshAllDockPlates() {
        if (this.modeCollapsedBtn != null && this.modeCollapsedBtn.getVisibility() == 0) {
            setModeCellSelected(this.modeCollapsedBtn, false);
        }
        if (this.modeAppsBtn != null && this.modeAppsBtn.getVisibility() == 0) {
            setModeCellSelected(this.modeAppsBtn, "apps".equals(this.stripMode));
        }
        if (this.modeLayoutBtn != null && this.modeLayoutBtn.getVisibility() == 0) {
            setModeCellSelected(this.modeLayoutBtn, "layout".equals(this.stripMode));
        }
        if (this.modeConfigBtn != null && this.modeConfigBtn.getVisibility() == 0) {
            setModeCellSelected(this.modeConfigBtn, "config".equals(this.stripMode));
        }
        if (this.modeSurfaceBtn != null && this.modeSurfaceBtn.getVisibility() == 0) {
            setModeCellSelected(this.modeSurfaceBtn, "cards".equals(this.dockSurfaceMode));
        }
        refreshDockSurfaceUi(false);
        refreshConfigToolPlates();
        refreshLayoutTipPlate();
        refreshQuickCardsTheme();
    }

    private void refreshAppsFabMenu() {
        if (this.appsFabMenu == null) {
            return;
        }
        View viewFindViewWithTag = this.appsFabMenu.findViewWithTag("ratioRow");
        if (viewFindViewWithTag instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) viewFindViewWithTag;
            float f = getResources().getDisplayMetrics().density;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View childAt = viewGroup.getChildAt(i);
                Object tag = childAt.getTag();
                if (tag instanceof String) {
                    styleAppsFabIconCell(childAt, tag.equals(this.splitRatio), f);
                }
            }
        }
    }

    private void refreshCenterFillChip() {
        String str;
        if (this.layoutCenterFillChip == null) {
            return;
        }
        boolean zEquals = "appsOnly".equals(this.shellMode);
        View view = this.layoutCenterFillChip;
        if (zEquals) {
            view.setVisibility(8);
            return;
        }
        if (view.getVisibility() != 0) {
            this.layoutCenterFillChip.setVisibility(0);
        }
        boolean zEquals2 = "mixed".equals(this.centerFillMode);
        boolean zEquals3 = "wallpaper".equals(this.centerFillMode);
        boolean z = zEquals3 || zEquals2;
        if (this.layoutCenterFillIcon != null) {
            this.layoutCenterFillIcon.setImageDrawable(systemDockIcon(z ? 17301567 : 17301565));
        }
        if (this.layoutCenterFillLabel != null) {
            TextView textView = this.layoutCenterFillLabel;
            if (zEquals2) {
                str = "Wallpaper + 3D";
            } else {
                str = zEquals3 ? "Wallpaper" : "3D Car";
            }
            textView.setText(str);
            styleDockLabel(this.layoutCenterFillLabel, false);
        }
        if (this.layoutCenterFillGear != null) {
            this.layoutCenterFillGear.setVisibility(z ? 0 : 8);
        }
        setModeCellSelected(this.layoutCenterFillChip, z);
    }

    private void refreshConfigToolPlates() {
        View viewFindViewWithTag;
        float f = getResources().getDisplayMetrics().density;
        for (ImageView imageView : this.dockToolIcons.values()) {
            if (imageView != null && imageView.getParent() != null && (viewFindViewWithTag = ((View) imageView.getParent()).findViewWithTag("modePlate")) != null) {
                viewFindViewWithTag.setBackground(makeDockPlateDrawable(false, f));
            }
        }
    }

    private void refreshDesktopIndicator() {
        int iMax = Math.max(1, this.desktopCount);
        int iMax2 = Math.max(0, Math.min(this.activeDesktopIndex, iMax - 1));
        String strTrim = (this.activeDesktopName == null || this.activeDesktopName.trim().isEmpty()) ? "Desktop" : this.activeDesktopName.trim();
        if (this.activeDesktopTitle != null) {
            this.activeDesktopTitle.setText(strTrim);
        }
        if (this.activeDesktopMeta != null) {
            this.activeDesktopMeta.setText((iMax2 + 1) + " of " + iMax);
        }
        if (this.activeDesktopTitle == null || !(this.activeDesktopTitle.getParent() instanceof View)) {
            return;
        }
        View view = (View) this.activeDesktopTitle.getParent();
        view.setBackground(makeFrostStateDrawable(true, getResources().getDisplayMetrics().density));
        this.activeDesktopTitle.setTextColor(dockLabelColor());
        if (this.activeDesktopMeta != null) {
            this.activeDesktopMeta.setTextColor(dockLabelColorMuted());
        }
        view.setContentDescription(strTrim + ", desktop " + (iMax2 + 1) + " of " + iMax + ". Tap or long press to customize");
    }

    private void refreshDockChromeTheme() {
        applyDockPanelBackground();
        refreshAllDockPlates();
        refreshLayoutChipSelection();
        refreshLayoutThemeChip();
        styleDockLabel(this.layoutTipLabel, true);
        refreshModeDrawerGlyphs();
        refreshDockToolCaptions();
        refreshLauncherIconPlates();
        for (String str : this.dockToolIcons.keySet()) {
            refreshDockToolGlyph(str, dockToolGlyphFor(str));
        }
    }

    private void refreshDockSurfaceUi(boolean z) {
        boolean zEquals = "cards".equals(this.dockSurfaceMode);
        alignQuickCardsToWidgetBoard();
        setModeCellSelected(this.layoutLauncherSurfaceChip, !zEquals);
        setModeCellSelected(this.layoutCardsSurfaceChip, zEquals);
        if (this.layoutLauncherSurfaceChip != null) {
            this.layoutLauncherSurfaceChip.setSelected(!zEquals);
        }
        if (this.layoutCardsSurfaceChip != null) {
            this.layoutCardsSurfaceChip.setSelected(zEquals);
        }
        if (this.modeSurfaceBtn != null) {
            TextView textViewFindModeCellLabel = findModeCellLabel(this.modeSurfaceBtn);
            if (textViewFindModeCellLabel != null) {
                textViewFindModeCellLabel.setText(zEquals ? "Launcher" : "Cards");
            }
            this.modeSurfaceBtn.setContentDescription(zEquals ? "Show launcher. Long press to customize" : "Show quick cards. Long press to customize");
            if (this.modeSurfaceBtn.getVisibility() == 0) {
                setModeCellSelected(this.modeSurfaceBtn, zEquals);
                setModeCellLabelVisible(this.modeSurfaceBtn, true);
            }
        }
        if ("apps".equals(this.stripMode) && this.appsScrollView != null && this.cardsScrollView != null) {
            showStripContent("apps", z);
        }
        if (!this.drawerExpanded) {
            applyDrawerCollapsedUi(false);
        }
        refreshQuickCardsTheme();
        refreshDesktopIndicator();
    }

    private void refreshDockToolCaptions() {
        for (ImageView imageView : this.dockToolIcons.values()) {
            if (imageView != null && imageView.getParent() != null) {
                ViewParent parent = ((View) imageView.getParent()).getParent();
                if (parent instanceof LinearLayout) {
                    LinearLayout linearLayout = (LinearLayout) parent;
                    for (int i = 0; i < linearLayout.getChildCount(); i++) {
                        View childAt = linearLayout.getChildAt(i);
                        if ((childAt instanceof TextView) && "dockToolCaption".equals(childAt.getTag())) {
                            TextView textView = (TextView) childAt;
                            styleDockLabel(textView, true);
                            if (this.dockUiLight) {
                                textView.setShadowLayer(0.0f, 0.0f, 0.0f, 0);
                            } else {
                                textView.setShadowLayer(3.0f, 0.0f, 1.0f, -1728053248);
                            }
                        }
                    }
                }
            }
        }
    }

    private void refreshDockToolGlyph(String str, String str2) {
        ImageView imageView = this.dockToolIcons.get(str);
        if (imageView == null) {
            return;
        }
        int i = imageView.getLayoutParams() != null ? imageView.getLayoutParams().width : 0;
        if (i <= 0) {
            i = this.dockToolGlyphPx > 0 ? this.dockToolGlyphPx : 48;
        }
        imageView.setImageDrawable(dockToolGlyph(str, i, str2));
    }

    private void refreshLauncherIconPlates() {
        View viewFindViewWithTag;
        float f = getResources().getDisplayMetrics().density;
        for (MotionTrailLayout motionTrailLayout : this.launcherItems) {
            if (motionTrailLayout != null && (viewFindViewWithTag = motionTrailLayout.findViewWithTag("iconPlate")) != null) {
                Object tag = motionTrailLayout.getTag();
                if (tag instanceof Integer) {
                    viewFindViewWithTag.setBackground(makeLauncherIconPlateDrawable(f, ((Integer) tag).intValue()));
                } else {
                    viewFindViewWithTag.setBackground(makeLauncherIconPlateDrawable(f, Boolean.TRUE.equals(tag)));
                }
            }
        }
    }

    private void refreshLayoutChipSelection() {
        refreshCenterFillChip();
        refreshLayoutThemeChip();
        refreshDockSurfaceUi(false);
        refreshDesktopIndicator();
    }

    private void refreshLayoutThemeChip() {
        String str;
        if (this.layoutThemeChip == null) {
            return;
        }
        String str2 = this.dockWidgetThemeMode != null ? this.dockWidgetThemeMode : "dark";
        String str3 = this.dockWidgetThemeEffective != null ? this.dockWidgetThemeEffective : "dark";
        if ("auto".equals(str2)) {
            str = "Appearance · Auto";
        } else {
            str = "light".equals(str2) ? "Appearance · Light" : "Appearance · Dark";
        }
        if (this.layoutThemeLabel != null) {
            this.layoutThemeLabel.setText(str);
        }
        styleDockLabel(this.layoutThemeLabel, false);
        setModeCellSelected(this.layoutThemeChip, "light".equals(str3));
    }

    private void refreshLayoutTipPlate() {
        View view;
        View viewFindViewWithTag;
        if (this.layoutTipLabel == null || (view = (View) this.layoutTipLabel.getParent()) == null || (viewFindViewWithTag = view.findViewWithTag("modePlate")) == null) {
            return;
        }
        viewFindViewWithTag.setBackground(makeDockPlateDrawable(false, getResources().getDisplayMetrics().density));
    }

    private void refreshModeDrawerGlyphs() {
        if (this.modeAppsBtn != null && this.modeAppsBtn.getVisibility() == 0) {
            setModeCellSelected(this.modeAppsBtn, "apps".equals(this.stripMode));
        }
        if (this.modeLayoutBtn != null && this.modeLayoutBtn.getVisibility() == 0) {
            setModeCellSelected(this.modeLayoutBtn, "layout".equals(this.stripMode));
        }
        if (this.modeConfigBtn != null && this.modeConfigBtn.getVisibility() == 0) {
            setModeCellSelected(this.modeConfigBtn, "config".equals(this.stripMode));
        }
        if (this.modeCollapsedBtn != null && this.modeCollapsedBtn.getVisibility() == 0) {
            setModeCellSelected(this.modeCollapsedBtn, false);
        }
        if (this.modeSurfaceBtn == null || this.modeSurfaceBtn.getVisibility() != 0) {
            return;
        }
        setModeCellSelected(this.modeSurfaceBtn, "cards".equals(this.dockSurfaceMode));
    }

    private void refreshQuickCardsTheme() {
        float f = getResources().getDisplayMetrics().density;
        for (View view : this.quickCardViews) {
            if (view != null) {
                view.setBackground(makeFrostStateDrawable(view.isSelected(), f, washForCardView(view)));
                tintFrostText(view);
            }
        }
        for (ImageView imageView : this.quickMediaButtons) {
            if (imageView != null) {
                boolean zEquals = "mediaBtnPrimary".equals(imageView.getTag());
                imageView.setBackground(makeMediaButtonBackground(f, zEquals, Math.round((zEquals ? 48 : 40) * f)));
                if (!zEquals) {
                    imageView.setImageDrawable(mediaGlyph(f, false, "Previous track".contentEquals(imageView.getContentDescription() == null ? "" : imageView.getContentDescription()) ? "prev" : "next"));
                }
            }
        }
        if (this.quickMediaPlayPause != null) {
            this.quickMediaPlayPause.setImageDrawable(mediaGlyph(f, true, this.quickMediaPlaying ? "pause" : "play"));
        }
        if (this.quickMediaBars != null) {
            this.quickMediaBars.retint();
        }
        if (this.quickMediaArt != null) {
            this.quickMediaArt.setBackground(makeDockPlateDrawable(false, f));
            if (!this.quickMediaHasArt) {
                this.quickMediaArt.setImageDrawable(mediaArtPlaceholder(f));
            }
        }
        String str = this.dockUiLight + "|" + this.dockAccentColor;
        if (!str.equals(this.quickMenuThemeSig)) {
            this.quickMenuThemeSig = str;
            dismissQuickMenu();
        }
        for (QuickCardGraphicView quickCardGraphicView : this.quickCardGraphics.values()) {
            if (quickCardGraphicView != null) {
                quickCardGraphicView.invalidate();
            }
        }
        for (QuickClockCardView quickClockCardView : this.quickClockCards.values()) {
            if (quickClockCardView != null) {
                quickClockCardView.invalidate();
            }
        }
    }

    private void refreshRecentSlots() {
        PackageManager packageManager = getPackageManager();
        Intent intent = new Intent("android.intent.action.MAIN", (Uri) null);
        intent.addCategory("android.intent.category.LAUNCHER");
        bindRecentSlots(packageManager, packageManager.queryIntentActivities(intent, 0));
    }

    private void registerOverlayTaskListener() {
        allowHiddenApis();
        try {
            Class<?> cls = Class.forName("android.app.ITaskStackListener");
            Object objNewProxyInstance = Proxy.newProxyInstance(cls.getClassLoader(), new Class[]{cls}, (obj, method, objArr) -> {
                String name = method.getName();
                if ("onTaskStackChanged".equals(name) || "onTaskRemoved".equals(name) || "onTaskRemovalStarted".equals(name) || "onRecentTaskListUpdated".equals(name) || "onTaskMovedToFront".equals(name) || "onActivityUnpinned".equals(name)) {
                    this.mainHandler.post(() -> this.syncOverlaySlots());
                }
                Class<?> returnType = method.getReturnType();
                if (returnType == Boolean.TYPE) {
                    return false;
                }
                if (returnType == Integer.TYPE) {
                    return 0;
                }
                return returnType == Long.TYPE ? 0L : null;
            });
            boolean z = false;
            try {
                ActivityManager activityManager = (ActivityManager) getSystemService("activity");
                activityManager.getClass().getMethod("registerTaskStackListener", cls).invoke(activityManager, objNewProxyInstance);
                z = true;
            } catch (Exception e) {
            }
            if (!z) {
                Object objSystemActivityService = systemActivityService();
                if (objSystemActivityService == null) {
                    return;
                } else {
                    objSystemActivityService.getClass().getMethod("registerTaskStackListener", cls).invoke(objSystemActivityService, objNewProxyInstance);
                }
            }
            this.taskStackListener = objNewProxyInstance;
            Log.w("H6Viewer", "Registered TaskStackListener");
        } catch (Throwable th) {
            Log.w("H6Viewer", "TaskStackListener unavailable", th);
        }
    }

    private void remeasureAppsFabWindow() {
        if (!this.appsFabOverlayAttached || this.appsFabRoot == null) {
            return;
        }
        this.appsFabRoot.measure(View.MeasureSpec.makeMeasureSpec(0, 0), View.MeasureSpec.makeMeasureSpec(0, 0));
        float f = getResources().getDisplayMetrics().density;
        int iMax = Math.max(this.appsFabRoot.getMeasuredWidth(), Math.round(f * 72.0f));
        int iMax2 = Math.max(this.appsFabRoot.getMeasuredHeight(), Math.round(f * 72.0f));
        if (this.appsFabMenuOpen && this.appsFabBtnAnchorY != Integer.MIN_VALUE) {
            int measuredHeight = this.appsFabMenu != null ? this.appsFabMenu.getMeasuredHeight() : 0;
            int measuredHeight2 = this.appsFabButton != null ? this.appsFabButton.getMeasuredHeight() : Math.round(72.0f * f);
            if (measuredHeight > 0) {
                int iRound = Math.round(10.0f * f);
                this.appsFabPosY = (this.appsFabBtnAnchorY - measuredHeight) - iRound;
                iMax2 = measuredHeight + iRound + measuredHeight2;
            }
        }
        clampAppsFabPosition();
        try {
            if (!this.appsFabUsesSystemOverlay || this.appsFabLp == null) {
                applyAppsFabPosition();
                this.appsFabRoot.requestLayout();
            } else {
                this.appsFabLp.width = iMax;
                this.appsFabLp.height = iMax2;
                this.appsFabLp.x = this.appsFabPosX;
                this.appsFabLp.y = this.appsFabPosY;
                getWindowManager().updateViewLayout(this.appsFabRoot, this.appsFabLp);
            }
        } catch (Exception e) {
        }
    }

    private void rememberRecentApp(String str) {
        if (str == null || str.isEmpty() || skipInRecents(str)) {
            return;
        }
        this.recentPackages.remove(str);
        this.recentPackages.add(0, str);
        while (this.recentPackages.size() > 12) {
            this.recentPackages.remove(this.recentPackages.size() - 1);
        }
        saveRecentApps();
        refreshRecentSlots();
    }

    private void removeDockPackage(String str, boolean z) {
        if (str == null || str.isEmpty()) {
            return;
        }
        if (z) {
            this.hiddenPackages.add(str);
            saveHiddenApps();
        }
        this.recentPackages.remove(str);
        saveRecentApps();
        MotionTrailLayout motionTrailLayout = this.dockItemsByPackage.get(str);
        if (motionTrailLayout != null) {
            motionTrailLayout.setVisibility(8);
        }
        if (!z) {
            if (motionTrailLayout != null) {
                this.dockItemsByPackage.remove(str);
                this.launcherItems.remove(motionTrailLayout);
                if (motionTrailLayout.getParent() instanceof ViewGroup) {
                    ((ViewGroup) motionTrailLayout.getParent()).removeView(motionTrailLayout);
                }
            }
            for (int i = 0; i < PINNED_PACKAGES.length; i++) {
                if (PINNED_PACKAGES[i].equals(str)) {
                    this.pinnedBound[i] = false;
                }
            }
            if (this.hiddenPackages.remove(str)) {
                saveHiddenApps();
            }
            if (this.windowPackages.remove(str)) {
                saveWindowApps();
            }
        }
        updatePinnedVisibility();
        refreshRecentSlots();
        notifyAppsCatalogChanged();
    }

    private void removeTaskById(int i) {
        if (i < 0) {
            return;
        }
        allowHiddenApis();
        try {
            ActivityManager.class.getMethod("removeTask", Integer.TYPE).invoke((ActivityManager) getSystemService("activity"), Integer.valueOf(i));
            Log.w("H6Viewer", "Removed task " + i);
        } catch (Exception e) {
            Log.w("H6Viewer", "ActivityManager.removeTask failed", e);
            try {
                Object objSystemActivityService = systemActivityService();
                if (objSystemActivityService == null) {
                    return;
                }
                objSystemActivityService.getClass().getMethod("removeTask", Integer.TYPE).invoke(objSystemActivityService, Integer.valueOf(i));
                Log.w("H6Viewer", "Removed task " + i + " via system service");
            } catch (Exception e2) {
                Log.w("H6Viewer", "removeTask failed for " + i + " (" + e2.getClass().getSimpleName() + ")");
            }
        }
    }

    private void replayMediaPayload() {
        if (this.lastMediaPayload != null) {
            updateQuickMediaCard(this.lastMediaPayload);
        }
    }

    private void requestProjectionTaskResolve(String str) {
        if (str == null || str.isEmpty()) {
            return;
        }
        try {
            Intent intent = new Intent("br.com.opengw.ACTION_RESOLVE_TASK");
            intent.setPackage("br.com.opengw");
            intent.putExtra("caller", apiCallerToken());
            intent.putExtra("package", str);
            intent.putExtra("slot", "projection");
            sendBroadcast(intent);
            Log.w("H6Viewer", "Projection task resolve requested for " + str);
        } catch (Exception e) {
            Log.w("H6Viewer", "Projection task resolve failed for " + str + " (" + e.getClass().getSimpleName() + ")");
        }
    }

    private void requestTaskBounds(String str, Rect rect) {
        if (str == null || str.isEmpty() || rect == null) {
            return;
        }
        try {
            Intent intent = new Intent("br.com.opengw.ACTION_SET_TASK_BOUNDS");
            intent.setPackage("br.com.opengw");
            intent.putExtra("caller", apiCallerToken());
            intent.putExtra("package", str);
            intent.putExtra("l", rect.left);
            intent.putExtra("t", rect.top);
            intent.putExtra("r", rect.right);
            intent.putExtra("b", rect.bottom);
            sendBroadcast(intent);
            Log.w("H6Viewer", "Bounds request " + str + " -> " + rect);
        } catch (Exception e) {
            Log.w("H6Viewer", "Bounds request failed for " + str + " (" + e.getClass().getSimpleName() + ")");
        }
    }

    private void requestTaskId(String str, boolean z) {
        if (str == null || str.isEmpty()) {
            return;
        }
        long jUptimeMillis = SystemClock.uptimeMillis();
        if (jUptimeMillis - (z ? this.lastRightTaskRequestMs : this.lastLeftTaskRequestMs) < 1500) {
            return;
        }
        if (z) {
            this.lastRightTaskRequestMs = jUptimeMillis;
        } else {
            this.lastLeftTaskRequestMs = jUptimeMillis;
        }
        try {
            Intent intent = new Intent("br.com.opengw.ACTION_RESOLVE_TASK");
            intent.setPackage("br.com.opengw");
            intent.putExtra("caller", apiCallerToken());
            intent.putExtra("package", str);
            intent.putExtra("slot", z ? "right" : "left");
            sendBroadcast(intent);
        } catch (Exception e) {
            Log.w("H6Viewer", "Task id request failed for " + str + " (" + e.getClass().getSimpleName() + ")");
        }
    }

    private void requestTelemetrySnapshot(String str) {
        try {
            Intent intent = new Intent("com.haval.vehicle.REQUEST_SNAPSHOT");
            intent.setPackage("br.com.opengw");
            intent.putExtra("requester", getPackageName());
            sendBroadcast(intent);
            Log.w("H6Viewer", "Telemetry snapshot requested (" + str + ")");
        } catch (Exception e) {
            Log.w("H6Viewer", "Telemetry snapshot request failed", e);
        }
    }

    private boolean resizeTaskToBounds(int taskId, Rect bounds) {
        if (taskId < 0 || bounds == null) {
            return false;
        }
        allowHiddenApis();
        Rect rect = new Rect(bounds);
        try {
            ActivityManager activityManager = (ActivityManager) getSystemService("activity");
            try {
                ActivityManager.class.getMethod("resizeTask", Integer.TYPE, Rect.class).invoke(activityManager, taskId, rect);
                Log.w("H6Viewer", "Pinned task " + taskId + " via AM.resizeTask(2) " + rect);
                return true;
            } catch (NoSuchMethodException e) {
                try {
                    ActivityManager.class.getMethod("resizeTask", Integer.TYPE, Rect.class, Integer.TYPE).invoke(activityManager, taskId, rect, 0);
                    Log.w("H6Viewer", "Pinned task " + taskId + " via AM.resizeTask(3) " + rect);
                    return true;
                } catch (NoSuchMethodException e2) {
                    return resizeTaskViaActivityTaskManager(taskId, rect);
                }
            }
        } catch (Exception e) {
            Log.w("H6Viewer", "AM.resizeTask failed for task " + taskId, e);
        }
        return false;
    }

    /** Fallback para quando o ActivityManager não expõe resizeTask: procura o método no serviço do sistema. */
    private boolean resizeTaskViaActivityTaskManager(int taskId, Rect rect) {
        try {
            Object service = systemActivityService();
            if (service == null) {
                return false;
            }
            Method method = null;
            for (Method candidate : service.getClass().getMethods()) {
                if ("resizeTask".equals(candidate.getName())) {
                    Class<?>[] types = candidate.getParameterTypes();
                    if (types.length >= 2 && types[0] == Integer.TYPE && Rect.class.isAssignableFrom(types[1])) {
                        method = candidate;
                        break;
                    }
                }
            }
            if (method == null) {
                return false;
            }
            Class<?>[] types = method.getParameterTypes();
            if (types.length == 2) {
                method.invoke(service, taskId, rect);
            } else {
                if (types.length < 3 || types[2] != Integer.TYPE) {
                    return false;
                }
                method.invoke(service, taskId, rect, 0);
            }
            Log.w("H6Viewer", "Pinned task " + taskId + " via ATM.resizeTask " + rect);
            return true;
        } catch (Exception e) {
            Log.w("H6Viewer", "ATM.resizeTask failed for task " + taskId, e);
            return false;
        }
    }

    private Drawable resolveDockIcon(String str, Drawable drawable) {
        if (str == null || this.dockAppOverrides == null) {
            return drawable;
        }
        String strIcon = this.dockAppOverrides.icon(str);
        if (strIcon != null) {
            Drawable drawableDrawableFor = DockAppOverrides.drawableFor(this, strIcon, this.dockAppOverrides.color(str));
            return drawableDrawableFor != null ? drawableDrawableFor : drawable;
        }
        if (!"__gwm_hub".equals(str)) {
            return drawable;
        }
        try {
            return getDrawable(R.drawable.ic_gwm_viewer);
        } catch (Exception e) {
            return drawable;
        }
    }

    private String resolveDockLabel(String str, String str2) {
        if (str == null) {
            return str2 != null ? str2 : "";
        }
        String strName = this.dockAppOverrides != null ? this.dockAppOverrides.name(str) : null;
        if (strName != null) {
            return strName;
        }
        if ("__gwm_hub".equals(str)) {
            return "GWM";
        }
        return str2 != null ? str2 : "";
    }

    private String resolveLaunchSide() {
        boolean z = false;
        boolean z2 = (this.activePopupPackage == null || this.activePopupPackage.isEmpty()) && slotAllowsApp("left");
        if ((this.activeMediaPackage == null || this.activeMediaPackage.isEmpty()) && slotAllowsApp("right")) {
            z = true;
        }
        String str = "";
        if (!z2 && !z) {
            return "";
        }
        if ("appCar".equals(this.shellMode)) {
            return z2 ? "left" : "";
        }
        if ("left".equals(this.launchSidePref) || "right".equals(this.launchSidePref)) {
            String str2 = this.launchSidePref;
            if (!"left".equals(str2) || z2) {
                if (!"right".equals(str2) || z) {
                    str = str2;
                } else if (z2) {
                    str = "left";
                }
            } else if (z) {
                str = "right";
            }
        } else {
            str = this.nextLaunchLeft ? "left" : "right";
            if (!slotAllowsApp(str)) {
                str = "left".equals(str) ? "right" : "left";
            }
            if ("appsOnly".equals(this.shellMode)) {
                if (z2 && !z) {
                    str = "left";
                } else if (z && !z2) {
                    str = "right";
                }
            }
            if ("left".equals(str) && !z2 && z) {
                str = "right";
            } else if ("right".equals(str) && !z && z2) {
                str = "left";
            }
            if (!slotAllowsApp(str)) {
                str = "";
            }
        }
        if (!str.isEmpty()) {
            this.nextLaunchLeft = true ^ "left".equals(str);
            if ("left".equals(this.launchSidePref) || "right".equals(this.launchSidePref)) {
                this.launchSidePref = this.nextLaunchLeft ? "left" : "right";
            }
            saveShellPrefs();
            this.mainHandler.post(() -> this.notifyViewerShellLayout());
        }
        this.lastLaunchSide = str;
        return str;
    }

    private Drawable resolveSourceChipIcon(String str, String str2) {
        Drawable drawableIconFor;
        Drawable drawableIconFor2;
        String strIcon;
        Drawable drawableDrawableFor;
        if (str != null && !str.isEmpty() && this.dockAppOverrides != null && (strIcon = this.dockAppOverrides.icon(str)) != null && (drawableDrawableFor = DockAppOverrides.drawableFor(this, strIcon, this.dockAppOverrides.color(str))) != null) {
            return drawableDrawableFor;
        }
        if (isAndroidAutoMediaSource(str, str2)) {
            if (this.projectionPresence != null && (drawableIconFor2 = this.projectionPresence.iconFor(ProjectionPresence.Kind.ANDROID_AUTO)) != null) {
                return drawableIconFor2;
            }
            try {
                return getDrawable(R.drawable.ic_android_auto_default);
            } catch (Exception e) {
            }
        }
        if (!isCarPlayMediaSource(str, str2)) {
            return null;
        }
        if (this.projectionPresence != null && (drawableIconFor = this.projectionPresence.iconFor(ProjectionPresence.Kind.CARPLAY)) != null) {
            return drawableIconFor;
        }
        try {
            return getDrawable(R.drawable.ic_carplay_default);
        } catch (Exception e2) {
            return null;
        }
    }

    private void restartIntoSlot(String str, ComponentName componentName, boolean z) {
        if (componentName == null || str == null || str.isEmpty()) {
            return;
        }
        long jUptimeMillis = SystemClock.uptimeMillis();
        if (jUptimeMillis - this.lastSlotRestartMs < 900) {
            return;
        }
        this.lastSlotRestartMs = jUptimeMillis;
        Rect rectRightFreeformBounds = z ? rightFreeformBounds() : leftFreeformBounds();
        try {
            Intent intent = new Intent("android.intent.action.MAIN");
            intent.addCategory("android.intent.category.LAUNCHER");
            intent.setComponent(componentName);
            intent.setPackage(str);
            intent.addFlags(268632064);
            startActivity(intent, buildWindowOptions(5, rectRightFreeformBounds));
            Log.w("H6Viewer", "Raise: re-started " + str + " into " + rectRightFreeformBounds);
        } catch (Exception e) {
            Log.w("H6Viewer", "Raise: re-start failed for " + str, e);
        }
    }

    private void restoreDockPackage(String str) {
        if (str == null || str.isEmpty() || !this.hiddenPackages.remove(str)) {
            return;
        }
        saveHiddenApps();
        updatePinnedVisibility();
        refreshRecentSlots();
        Log.w("H6Viewer", "Launcher unhidden " + str);
        notifyAppsCatalogChanged();
    }

    private void revealLauncherStrip() {
        endSplashOverlay();
        if (this.launcherRevealed) {
            return;
        }
        this.launcherRevealed = true;
        this.mainHandler.removeCallbacks(this.launcherRevealFallback);
        updateAppsOnlyChrome();
        if ("appsOnly".equals(this.shellMode)) {
            maybeRestoreAppsOnlySlots();
            return;
        }
        updateLauncherStripVisibility();
        if (this.launcherItems.isEmpty()) {
            return;
        }
        float f = getResources().getDisplayMetrics().density;
        float f2 = 120.0f * f;
        float f3 = 90.0f * f;
        ArrayList arrayList = new ArrayList();
        for (MotionTrailLayout motionTrailLayout : this.launcherItems) {
            if (motionTrailLayout != null && motionTrailLayout.getVisibility() == 0) {
                arrayList.add(motionTrailLayout);
            }
        }
        int size = arrayList.size();
        for (int i = 0; i < size; i++) {
            MotionTrailLayout motionTrailLayout2 = (MotionTrailLayout) arrayList.get(i);
            ValueAnimator valueAnimatorOfFloat = ValueAnimator.ofFloat(0.0f, 1.0f);
            valueAnimatorOfFloat.setDuration(520L);
            valueAnimatorOfFloat.setStartDelay(((long) ((size - 1) - i)) * 200);
            valueAnimatorOfFloat.setInterpolator(new DecelerateInterpolator(1.8f));
            valueAnimatorOfFloat.addUpdateListener(valueAnimator -> {
                float fFloatValue = ((Float) valueAnimator.getAnimatedValue()).floatValue();
                motionTrailLayout2.setTranslationX((-f2) * (1.0f - fFloatValue));
                motionTrailLayout2.setAlpha(Math.min(1.0f, 1.6f * fFloatValue));
                motionTrailLayout2.setTrailPx((1.0f - fFloatValue) * f3);
            });
            valueAnimatorOfFloat.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    motionTrailLayout2.setTranslationX(0.0f);
                    motionTrailLayout2.setAlpha(1.0f);
                    motionTrailLayout2.setTrailPx(0.0f);
                }
            });
            valueAnimatorOfFloat.start();
        }
        if (this.bootProgressTrack != null && this.bootProgressTrack.getVisibility() == 0) {
            this.bootProgressTrack.bringToFront();
        }
        if (this.bootCenterLoader == null || this.bootCenterLoader.getVisibility() != 0) {
            return;
        }
        this.bootCenterLoader.bringToFront();
    }

    private Rect rightFreeformBounds() {
        Rect rectUsableDisplayRect = usableDisplayRect();
        int iSlotBandTopPx = slotBandTopPx();
        int iSlotBandBottomPx = slotBandBottomPx();
        if (!"appsOnly".equals(this.shellMode)) {
            return clampSlot(scaleToUsable(RIGHT_APP_BOUNDS, rectUsableDisplayRect), rectUsableDisplayRect);
        }
        return clampSlot(new Rect((Math.max(6, Math.round(rectUsableDisplayRect.width() * 0.004f)) / 2) + splitDividerX(rectUsableDisplayRect), iSlotBandTopPx, rectUsableDisplayRect.right - Math.max(6, Math.round(rectUsableDisplayRect.width() * 0.004f)), iSlotBandBottomPx), rectUsableDisplayRect);
    }

    private static boolean runningTaskIsVisible(ActivityManager.RunningTaskInfo runningTaskInfo) {
        try {
            Object obj = ActivityManager.RunningTaskInfo.class.getField("isVisible").get(runningTaskInfo);
            if (obj instanceof Boolean) {
                return ((Boolean) obj).booleanValue();
            }
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    private boolean sameBottomCardStructure(List<BottomCardDescriptor> list, List<BottomCardDescriptor> list2) {
        if (list.size() != list2.size()) {
            return false;
        }
        for (int i = 0; i < list.size(); i++) {
            BottomCardDescriptor bottomCardDescriptor = list.get(i);
            BottomCardDescriptor bottomCardDescriptor2 = list2.get(i);
            if (!bottomCardDescriptor.id.equals(bottomCardDescriptor2.id) || !bottomCardDescriptor.title.equals(bottomCardDescriptor2.title) || !bottomCardDescriptor.action.equals(bottomCardDescriptor2.action) || ("clock".equals(bottomCardDescriptor.id) && (!bottomCardDescriptor.clockFace.equals(bottomCardDescriptor2.clockFace) || !bottomCardDescriptor.clockHourFormat.equals(bottomCardDescriptor2.clockHourFormat) || !bottomCardDescriptor.dialMarks.equals(bottomCardDescriptor2.dialMarks) || !bottomCardDescriptor.splitPlates.equals(bottomCardDescriptor2.splitPlates) || !bottomCardDescriptor.dateSpineFormat.equals(bottomCardDescriptor2.dateSpineFormat) || !bottomCardDescriptor.dateWording.equals(bottomCardDescriptor2.dateWording)))) {
                return false;
            }
        }
        return true;
    }

    private String sanitizeClockOption(String str, String[] strArr, String str2) {
        String lowerCase = str == null ? "" : str.trim().toLowerCase(Locale.US);
        for (String str3 : strArr) {
            if (str3.equals(lowerCase)) {
                return lowerCase;
            }
        }
        return str2;
    }

    private String sanitizeDrivingState(String str) {
        String lowerCase = str == null ? "" : str.trim().toLowerCase(Locale.US);
        switch (lowerCase) {
            case "eco":
            case "normal":
            case "sport":
            case "snow":
            case "sand":
            case "mud":
            case "awd":
            case "hev":
            case "evp":
            case "ev":
            case "level1":
            case "level2":
            case "level3":
            case "onepedal":
                return lowerCase;
            default:
                return "unknown";
        }
    }

    private String sanitizeGlyphPath(String str) {
        if (str == null) {
            return "";
        }
        String strTrim = str.trim();
        if (strTrim.isEmpty() || strTrim.length() > 4000) {
            return "";
        }
        for (int i = 0; i < strTrim.length(); i++) {
            char cCharAt = strTrim.charAt(i);
            if (!(cCharAt == 'M' || cCharAt == 'L' || cCharAt == 'C' || cCharAt == 'Z' || cCharAt == ' ' || cCharAt == '.' || cCharAt == '-' || (cCharAt >= '0' && cCharAt <= '9'))) {
                return "";
            }
        }
        return strTrim;
    }

    private String sanitizeNavigationState(String str) {
        String strReplace = str == null ? "" : str.trim().toLowerCase(Locale.US).replace('-', '_');
        switch (strReplace) {
            case "idle":
            case "demo":
            case "unavailable":
            case "turn_left":
            case "turn_right":
            case "straight":
            case "uturn":
            case "roundabout":
            case "fork":
            case "merge":
            case "exit":
            case "destination":
                return strReplace;
            default:
                return "unavailable";
        }
    }

    private String[] sanitizeOpeningStates(String str) {
        String[] strArr = {"unknown", "unknown", "unknown", "unknown", "unknown"};
        if (str == null || str.trim().isEmpty()) {
            return strArr;
        }
        String[] strArrSplit = str.split(",", -1);
        for (int i = 0; i < strArr.length && i < strArrSplit.length; i++) {
            String lowerCase = strArrSplit[i].trim().toLowerCase(Locale.US);
            if ("open".equals(lowerCase) || "closed".equals(lowerCase) || "unknown".equals(lowerCase)) {
                strArr[i] = lowerCase;
            }
        }
        return strArr;
    }

    private String sanitizePowerDirections(String str) {
        return (str == null || !str.matches("(?:-1|0|1),(?:-1|0|1),[01]")) ? "" : str;
    }

    private String sanitizePowerState(String str) {
        String lowerCase = str == null ? "" : str.trim().toLowerCase(Locale.US);
        switch (lowerCase) {
            case "live":
            case "partial":
            case "stale":
            case "unavailable":
            case "demo":
            case "ev":
            case "hybrid":
            case "ice":
            case "regen":
            case "charge":
            case "idle":
                return lowerCase;
            default:
                return "unavailable";
        }
    }

    private String sanitizePowerVariant(String str) {
        String lowerCase = str == null ? "" : str.trim().toLowerCase(Locale.US);
        return ("phev34".equals(lowerCase) || "phev19".equals(lowerCase) || "hev2".equals(lowerCase)) ? lowerCase : "phev19";
    }

    private String[] sanitizeSeatBeltStates(String str) {
        String[] strArr = {"unknown", "unknown", "unknown", "unknown", "unknown"};
        if (str == null || str.trim().isEmpty()) {
            return strArr;
        }
        String[] strArrSplit = str.split(",", -1);
        for (int i = 0; i < strArr.length && i < strArrSplit.length; i++) {
            String lowerCase = strArrSplit[i].trim().toLowerCase(Locale.US);
            if ("fastened".equals(lowerCase) || "unfastened".equals(lowerCase) || "unknown".equals(lowerCase)) {
                strArr[i] = lowerCase;
            }
        }
        return strArr;
    }

    private String sanitizeStatusState(String str) {
        String lowerCase = str == null ? "" : str.trim().toLowerCase(Locale.US);
        switch (lowerCase) {
            case "live":
            case "demo":
            case "partial":
            case "stale":
            case "unavailable":
                return lowerCase;
            default:
                return "unavailable";
        }
    }

    private String sanitizeTiresState(String str) {
        String lowerCase = str == null ? "" : str.trim().toLowerCase(Locale.US);
        switch (lowerCase) {
            case "live":
            case "demo":
            case "stale":
            case "warning":
            case "unavailable":
                return lowerCase;
            default:
                return "unavailable";
        }
    }

    private String[] sanitizeWheelStates(String str) {
        String[] strArr = {"unavailable", "unavailable", "unavailable", "unavailable"};
        if (str == null || str.trim().isEmpty()) {
            return strArr;
        }
        String[] strArrSplit = str.split(",", -1);
        for (int i = 0; i < strArr.length && i < strArrSplit.length; i++) {
            String lowerCase = strArrSplit[i].trim().toLowerCase(Locale.US);
            if ("normal".equals(lowerCase) || "warning".equals(lowerCase) || "unavailable".equals(lowerCase)) {
                strArr[i] = lowerCase;
            }
        }
        return strArr;
    }

    private void saveAppsFabPos() {
        getSharedPreferences("opengw_lancador", 0).edit().putInt("fabX", this.appsFabPosX).putInt("fabY", this.appsFabPosY).apply();
    }

    private void saveAppsOnlyDefaultNow() {
        this.shellMode = "appsOnly";
        SharedPreferences.Editor editorEdit = getSharedPreferences("opengw_lancador", 0).edit();
        editorEdit.putString("mode", "appsOnly");
        editorEdit.putString("lastNonAppsMode", this.lastNonAppsShellMode);
        editorEdit.putString("splitRatio", this.splitRatio);
        editorEdit.putString("leftApp", this.activePopupPackage != null ? this.activePopupPackage : "");
        editorEdit.putString("rightApp", this.activeMediaPackage != null ? this.activeMediaPackage : "");
        editorEdit.putString("launchSide", this.launchSidePref);
        editorEdit.putBoolean("nextLeft", this.nextLaunchLeft);
        editorEdit.putString("uiMode", this.uiMode);
        editorEdit.apply();
        try {
            Toast.makeText(this, "Side by Side saved as default", 0).show();
        } catch (Exception e) {
        }
        Log.w("H6Viewer", "Saved APP+APP default ratio=" + this.splitRatio + " left=" + this.activePopupPackage + " right=" + this.activeMediaPackage);
    }

    private void saveHiddenApps() {
        StringBuilder sb = new StringBuilder();
        boolean z = true;
        for (String str : this.hiddenPackages) {
            if (!z) {
                sb.append(',');
            }
            z = false;
            sb.append(str);
        }
        getSharedPreferences("opengw_lancador", 0).edit().putString("hidden_apps", sb.toString()).apply();
    }

    private void saveRecentApps() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < this.recentPackages.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(this.recentPackages.get(i));
        }
        getSharedPreferences("opengw_lancador", 0).edit().putString("recent_apps", sb.toString()).apply();
    }

    private void saveShellPrefs() {
        SharedPreferences.Editor editorEdit = getSharedPreferences("opengw_lancador", 0).edit();
        String str = this.shellMode;
        if ("appsOnly".equals(this.shellMode)) {
            str = this.lastNonAppsShellMode;
            editorEdit.putString("lastNonAppsMode", this.lastNonAppsShellMode);
        } else {
            this.lastNonAppsShellMode = this.shellMode;
            editorEdit.putString("lastNonAppsMode", this.lastNonAppsShellMode);
        }
        editorEdit.putString("mode", str);
        editorEdit.putString("launchSide", this.launchSidePref);
        editorEdit.putBoolean("nextLeft", this.nextLaunchLeft);
        editorEdit.putString("uiMode", this.uiMode);
        editorEdit.putString("splitRatio", this.splitRatio);
        editorEdit.apply();
    }

    private void saveWindowApps() {
        StringBuilder sb = new StringBuilder();
        boolean z = true;
        for (String str : this.windowPackages) {
            if (!z) {
                sb.append(',');
            }
            z = false;
            sb.append(str);
        }
        getSharedPreferences("opengw_lancador", 0).edit().putString("window_apps", sb.toString()).apply();
    }

    private Rect scaleToUsable(Rect rect, Rect rect2) {
        float fWidth = rect2.width() / SLOT_REFERENCE.width();
        return new Rect(rect2.left + Math.round(rect.left * fWidth), rect.top, rect2.left + Math.round(rect.right * fWidth), rect.bottom);
    }

    private void schedulePinPopupBounds(String packageName, ComponentName component, Rect bounds, boolean rightSlot) {
        Runnable previous = rightSlot ? this.pinMediaBoundsRunnable : this.pinBoundsRunnable;
        if (previous != null) {
            this.mainHandler.removeCallbacks(previous);
        }
        Runnable pinTask = new Runnable() {
            private int attempts = 0;

            @Override
            public void run() {
                String activePackage = rightSlot ? activeMediaPackage : activePopupPackage;
                if (packageName == null || !packageName.equals(activePackage)) {
                    return;
                }
                int taskId = findTaskIdForPackage(packageName);
                if (taskId >= 0) {
                    if (rightSlot) {
                        activeMediaTaskId = taskId;
                    } else {
                        activePopupTaskId = taskId;
                    }
                    resizeTaskToBounds(taskId, bounds);
                    setTaskAlwaysOnTop(taskId, true);
                }
                if (attempts == 1 && component != null) {
                    try {
                        Intent intent = new Intent("android.intent.action.MAIN");
                        intent.addCategory("android.intent.category.LAUNCHER");
                        intent.setComponent(component);
                        intent.setPackage(packageName);
                        intent.addFlags(268632064);
                        startActivity(intent, buildWindowOptions(5, bounds));
                    } catch (Exception e) {
                        Log.w("H6Viewer", "Re-apply launch bounds failed", e);
                    }
                }
                attempts++;
                if (attempts < 16) {
                    mainHandler.postDelayed(this, 200L);
                }
            }
        };
        if (rightSlot) {
            this.pinMediaBoundsRunnable = pinTask;
        } else {
            this.pinBoundsRunnable = pinTask;
        }
        this.mainHandler.postDelayed(pinTask, 150L);
    }

    private void scheduleTelemetrySnapshotRequests() {
        requestTelemetrySnapshot("startup");
        for (long j : SNAPSHOT_RETRY_DELAYS_MS) {
            this.mainHandler.postDelayed(() -> {
                requestTelemetrySnapshot("retry");
            }, j);
        }
    }

    private void scrollRailToCard(String str) {
        if (!(this.cardsScrollView instanceof HorizontalScrollView) || this.quickCardsRow == null) {
            return;
        }
        HorizontalScrollView horizontalScrollView = (HorizontalScrollView) this.cardsScrollView;
        horizontalScrollView.post(() -> {
            View viewFindViewWithTag = this.quickCardsRow.findViewWithTag("bottomCard:" + str);
            if (viewFindViewWithTag == null) {
                return;
            }
            horizontalScrollView.smoothScrollTo(Math.max(0, (viewFindViewWithTag.getLeft() + (viewFindViewWithTag.getWidth() / 2)) - (horizontalScrollView.getWidth() / 2)), 0);
            viewFindViewWithTag.animate().scaleX(1.07f).scaleY(1.07f).setDuration(170L).withEndAction(() -> viewFindViewWithTag.animate().scaleX(1.0f).scaleY(1.0f).setDuration(220L).start()).start();
        });
    }

    private void seedRecentAppsFromSystem(List<ResolveInfo> list) {
        List<ActivityManager.RecentTaskInfo> recentTasks;
        if (this.recentPackages.isEmpty()) {
            HashSet hashSet = new HashSet();
            if (list != null) {
                for (ResolveInfo resolveInfo : list) {
                    if (resolveInfo != null && resolveInfo.activityInfo != null) {
                        hashSet.add(resolveInfo.activityInfo.packageName);
                    }
                }
            }
            try {
                ActivityManager activityManager = (ActivityManager) getSystemService("activity");
                if (activityManager == null || (recentTasks = activityManager.getRecentTasks(16, 1)) == null) {
                    return;
                }
                Iterator<ActivityManager.RecentTaskInfo> it = recentTasks.iterator();
                while (it.hasNext()) {
                    ComponentName componentNameRecentTaskRealActivity = recentTaskRealActivity(it.next());
                    String packageName = componentNameRecentTaskRealActivity != null ? componentNameRecentTaskRealActivity.getPackageName() : null;
                    if (packageName != null && hashSet.contains(packageName) && !skipInRecents(packageName) && !this.recentPackages.contains(packageName)) {
                        this.recentPackages.add(packageName);
                        if (this.recentPackages.size() >= 3) {
                            break;
                        }
                    }
                }
                if (this.recentPackages.isEmpty()) {
                    return;
                }
                saveRecentApps();
            } catch (Exception e) {
            }
        }
    }

    private void selectStripMode(String str) {
        if (str == null) {
            str = "apps";
        }
        String str2 = str;
        String str3 = this.stripMode;
        this.stripMode = str2;
        applyDrawerCollapsedUi(true);
        if (str2.equals(str3)) {
            showStripContent(str2, false);
        } else {
            crossfadeStripContent(str3, str2);
        }
        if (!"layout".equals(str3) || "layout".equals(str2)) {
            return;
        }
        callViewerDock("closePanel");
    }

    private void setAppsFabMenuOpen(boolean appsFabMenuOpen) {
        Math.round(10.0f * getResources().getDisplayMetrics().density);
        if (appsFabMenuOpen && !this.appsFabMenuOpen) {
            this.appsFabBtnAnchorY = this.appsFabPosY;
        }
        this.appsFabMenuOpen = appsFabMenuOpen;
        if (this.appsFabMenu != null) {
            this.appsFabMenu.setVisibility(appsFabMenuOpen ? 0 : 8);
        }
        if (this.appsFabButton != null) {
            this.appsFabButton.setTextColor(appsFabMenuOpen ? -11544856 : -1379592);
        }
        if (appsFabMenuOpen) {
            showAppsFabScrim();
            refreshAppsFabMenu();
        } else {
            dismissAppsFabScrim();
            if (this.appsFabBtnAnchorY != Integer.MIN_VALUE) {
                this.appsFabPosY = this.appsFabBtnAnchorY;
                this.appsFabBtnAnchorY = Integer.MIN_VALUE;
            }
        }
        if (!this.appsFabUsesSystemOverlay) {
            applyChromeOnTop(appsFabMenuOpen);
        }
        if (!this.appsFabOverlayAttached || this.appsFabRoot == null) {
            return;
        }
        this.appsFabRoot.post(() -> this.remeasureAppsFabWindow());
    }

    private void setDrawerExpanded(boolean drawerExpanded) {
        this.drawerExpanded = drawerExpanded;
        if (drawerExpanded) {
            applyDrawerExpandedUi();
        } else {
            applyDrawerCollapsedUi(true);
        }
    }

    private void setModeCellIcon(View view, Drawable drawable) {
        ImageView imageView;
        if (view == null || (imageView = (ImageView) view.findViewWithTag("modeIcon")) == null) {
            return;
        }
        imageView.setImageDrawable(drawable);
    }

    private void setModeCellLabelVisible(View view, boolean z) {
        View viewFindViewWithTag;
        if (view == null || (viewFindViewWithTag = view.findViewWithTag("modeLabel")) == null) {
            return;
        }
        CharSequence text = viewFindViewWithTag instanceof TextView ? ((TextView) viewFindViewWithTag).getText() : null;
        viewFindViewWithTag.setVisibility((z && (text != null && text.length() > 0)) ? 0 : 4);
    }

    private void setModeCellSelected(View view, boolean z) {
        if (view == null) {
            return;
        }
        view.setSelected(z);
        View viewFindViewWithTag = view.findViewWithTag("modePlate");
        float f = getResources().getDisplayMetrics().density;
        int iRound = this.dockIconPx > 0 ? this.dockIconPx : Math.round(getResources().getDisplayMetrics().density * 60.0f);
        if (viewFindViewWithTag != null) {
            viewFindViewWithTag.setBackground(view == this.modeCollapsedBtn ? makeCircleFrostDrawable(z, f) : makeDockPlateDrawable(z, f));
        }
        float f2 = 1.0f;
        if (!this.dockUiLight && !z) {
            f2 = 0.78f;
        }
        view.setAlpha(f2);
        ImageView imageView = (ImageView) view.findViewWithTag("modeIcon");
        if (imageView != null) {
            Drawable drawableGlyphForModeCell = glyphForModeCell(view, view == this.modeCollapsedBtn ? cardsDockGlyphPx(iRound, f) : iRound, z);
            if (drawableGlyphForModeCell != null) {
                imageView.setImageDrawable(drawableGlyphForModeCell);
            }
        }
        TextView textViewFindModeCellLabel = findModeCellLabel(view);
        if (textViewFindModeCellLabel != null) {
            textViewFindModeCellLabel.setTextColor(z ? dockLabelColor() : dockLabelColorMuted());
        }
    }

    private void setRailEditMode(boolean railEditMode) {
        if (this.railEditMode == railEditMode) {
            return;
        }
        this.railEditMode = railEditMode;
        refreshDockSurfaceUi(true);
        if (railEditMode) {
            applyRailEdit(true);
        } else {
            applyRailEdit(false);
            rebuildQuickCardsRow();
        }
    }

    private static void setSubtreeClickable(View view, boolean z) {
        view.setClickable(z);
        view.setLongClickable(z);
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                setSubtreeClickable(viewGroup.getChildAt(i), z);
            }
        }
    }

    private boolean setTaskAlwaysOnTop(int i, boolean z) {
        if (i < 0 || !this.taskAlwaysOnTopSupported) {
            return false;
        }
        try {
            Object objSystemActivityService = systemActivityService();
            if (objSystemActivityService == null) {
                this.taskAlwaysOnTopSupported = false;
                return false;
            }
            Method method = null;
            for (Method method2 : objSystemActivityService.getClass().getMethods()) {
                if ("setTaskAlwaysOnTop".equals(method2.getName())) {
                    Class<?>[] parameterTypes = method2.getParameterTypes();
                    if (parameterTypes.length == 2 && parameterTypes[0] == Integer.TYPE && parameterTypes[1] == Boolean.TYPE) {
                        method = method2;
                        break;
                    }
                }
            }
            if (method != null) {
                method.invoke(objSystemActivityService, Integer.valueOf(i), Boolean.valueOf(z));
                Log.w("H6Viewer", "setTaskAlwaysOnTop task=" + i + " " + z);
                return true;
            }
            this.taskAlwaysOnTopSupported = false;
            Log.w("H6Viewer", "setTaskAlwaysOnTop unavailable; using moveTaskToFront instead");
            return false;
        } catch (Exception e) {
            this.taskAlwaysOnTopSupported = false;
            Log.w("H6Viewer", "ATM.setTaskAlwaysOnTop rejected; using moveTaskToFront instead", e);
        }
            return false;
    }

    private void setWorkspaceLayoutMode(boolean workspaceLayoutMode) {
        if (this.workspaceLayoutMode == workspaceLayoutMode) {
            return;
        }
        this.workspaceLayoutMode = workspaceLayoutMode;
        if (this.workspaceHeading != null) {
            this.workspaceHeading.setText(workspaceLayoutMode ? "LAYOUT" : "WORKSPACE");
            this.workspaceHeading.setVisibility(workspaceLayoutMode ? 8 : 0);
        }
        crossfadeFaces(workspaceLayoutMode ? this.workspaceActionsFace : this.workspaceLayoutFace, workspaceLayoutMode ? this.workspaceLayoutFace : this.workspaceActionsFace);
    }

    private void setupBootHud(FrameLayout frameLayout) {
        float f = getResources().getDisplayMetrics().density;
        FrameLayout frameLayout2 = new FrameLayout(this);
        frameLayout2.setClickable(false);
        frameLayout2.setFocusable(false);
        frameLayout2.setBackgroundColor(687865855);
        frameLayout2.setElevation(f * 20.0f);
        View view = new View(this);
        view.setBackgroundColor(-11544856);
        view.setLayoutParams(new FrameLayout.LayoutParams(0, -1));
        frameLayout2.addView(view);
        this.bootProgressFill = view;
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(-1, 2);
        layoutParams.gravity = 8388659;
        layoutParams.topMargin = bootProgressLineTopMarginPx();
        frameLayout.addView(frameLayout2, layoutParams);
        this.bootProgressTrack = frameLayout2;
        frameLayout2.setVisibility(8);
        ProgressBar progressBar = new ProgressBar(this);
        int iRound = Math.round(36.0f * f);
        FrameLayout.LayoutParams layoutParams2 = new FrameLayout.LayoutParams(iRound, iRound);
        layoutParams2.gravity = 17;
        progressBar.setLayoutParams(layoutParams2);
        progressBar.setIndeterminate(true);
        try {
            progressBar.getIndeterminateDrawable().setColorFilter(-11544856, PorterDuff.Mode.SRC_IN);
        } catch (Exception e) {
        }
        progressBar.setElevation(20.0f * f);
        progressBar.setVisibility(8);
        frameLayout.addView(progressBar, layoutParams2);
        this.bootCenterLoader = progressBar;
    }

    private void setupNativeLauncherUI(FrameLayout frameLayout) {
        boolean z;
        int i;
        loadRecentApps();
        loadHiddenApps();
        loadWindowApps();
        this.dockAppOverrides = DockAppOverrides.load(getSharedPreferences("opengw_lancador", 0));
        String str = "launcher";
        this.dockSurfaceMode = normalizeDockSurfaceMode(getSharedPreferences("opengw_lancador", 0).getString("dockSurfaceMode", "launcher"));
        float f = getResources().getDisplayMetrics().density;
        int iRound = Math.round(60.0f * f);
        int iRound2 = Math.round(84.0f * f);
        this.dockCellPx = iRound2;
        this.dockIconPx = iRound;
        int iCompensatedBottomMarginPx = compensatedBottomMarginPx(launcherBottomGapPx());
        int iRound3 = Math.round(72.0f * f);
        int iLauncherStripHeightPx = launcherStripHeightPx();
        FrameLayout frameLayout2 = new FrameLayout(this);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(-1, iLauncherStripHeightPx);
        layoutParams.gravity = 80;
        layoutParams.bottomMargin = iCompensatedBottomMarginPx;
        frameLayout2.setLayoutParams(layoutParams);
        frameLayout2.setBackgroundColor(0);
        frameLayout2.setClipChildren(false);
        frameLayout2.setVisibility(4);
        this.stripContainer = frameLayout2;
        View view = new View(this);
        view.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{1375731712, -1509949440}));
        view.setClickable(false);
        view.setFocusable(false);
        view.setVisibility(4);
        FrameLayout.LayoutParams layoutParams2 = new FrameLayout.LayoutParams(-1, launcherBottomGapPx());
        layoutParams2.gravity = 80;
        view.setLayoutParams(layoutParams2);
        this.reserveBand = view;
        LinearLayout linearLayout = new LinearLayout(this);
        this.stripRow = linearLayout;
        linearLayout.setOrientation(0);
        linearLayout.setGravity(8388659);
        linearLayout.setClipChildren(false);
        linearLayout.setBackgroundColor(0);
        frameLayout2.addView(linearLayout, new FrameLayout.LayoutParams(-1, -1));
        int iMax = Math.max(Math.round(8.0f * f), (iLauncherStripHeightPx - dockPlatePx(iRound, f)) / 2);
        this.dockIconRowTopPadPx = iMax;
        this.modeDrawer = buildModeDrawer(f, iRound2, iRound, iMax);
        this.modeDrawer.setElevation(f * 10.0f);
        linearLayout.addView(this.modeDrawer);
        FrameLayout frameLayout3 = new FrameLayout(this);
        frameLayout3.setClipChildren(true);
        frameLayout3.setClipToPadding(true);
        frameLayout3.setBackgroundColor(0);
        this.contentHost = frameLayout3;
        View view2 = view;
        LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(0, -1, 1.0f);
        layoutParams3.leftMargin = Math.round(f * 12.0f);
        linearLayout.addView(frameLayout3, layoutParams3);
        BounceHorizontalScrollView bounceHorizontalScrollView = new BounceHorizontalScrollView(this);
        bounceHorizontalScrollView.setHorizontalScrollBarEnabled(false);
        bounceHorizontalScrollView.setOverScrollMode(0);
        bounceHorizontalScrollView.setHorizontalFadingEdgeEnabled(false);
        bounceHorizontalScrollView.setFadingEdgeLength(iRound3);
        bounceHorizontalScrollView.setBackgroundColor(0);
        bounceHorizontalScrollView.setClipChildren(true);
        bounceHorizontalScrollView.setClipToPadding(true);
        this.appsScrollView = bounceHorizontalScrollView;
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(0);
        linearLayout2.setGravity(8388659);
        linearLayout2.setPadding(Math.round(f * 12.0f), iMax, Math.round(24.0f * f), 0);
        linearLayout2.setClipChildren(false);
        PackageManager packageManager = getPackageManager();
        Intent intent = new Intent("android.intent.action.MAIN", (Uri) null);
        intent.addCategory("android.intent.category.LAUNCHER");
        List<ResolveInfo> listQueryIntentActivities = packageManager.queryIntentActivities(intent, 0);
        HashMap map = new HashMap();
        if (listQueryIntentActivities != null) {
            for (ResolveInfo resolveInfo : listQueryIntentActivities) {
                if (resolveInfo != null) {
                    LinearLayout.LayoutParams layoutParams4 = layoutParams3;
                    if (resolveInfo.activityInfo == null) {
                        layoutParams3 = layoutParams4;
                    } else {
                        map.put(resolveInfo.activityInfo.packageName, resolveInfo);
                        layoutParams3 = layoutParams4;
                    }
                }
            }
        }
        this.projectionItem = makeDockItem(iRound2, iRound, f);
        this.projectionItem.setVisibility(8);
        linearLayout2.addView(this.projectionItem);
        this.launcherItems.add(this.projectionItem);
        int i2 = 0;
        while (true) {
            int i3 = iRound3;
            int i4 = iLauncherStripHeightPx;
            if (i2 >= PINNED_PACKAGES.length) {
                break;
            }
            String str2 = PINNED_PACKAGES[i2];
            int i5 = i2;
            this.pinnedBound[i5] = false;
            ResolveInfo resolveInfo2 = (ResolveInfo) map.get(str2);
            if (resolveInfo2 != null) {
                this.pinnedBound[i5] = true;
                Log.w("H6Viewer", "Launcher pinned " + str2 + " | " + pinnedLabel(str2, packageManager, resolveInfo2));
            } else if (isEmulatorDevice()) {
                this.emulatorStubPackages.add(str2);
                this.pinnedBound[i5] = true;
                Log.w("H6Viewer", "Launcher pinned stub " + str2 + " | " + pinnedLabel(str2, packageManager, null));
            }
            i2 = i5 + 1;
            iRound3 = i3;
            iLauncherStripHeightPx = i4;
        }
        HashMap map2 = map;
        BounceHorizontalScrollView bounceHorizontalScrollView2 = bounceHorizontalScrollView;
        this.gwmHubItem = makeDockItem(iRound2, iRound, f);
        this.gwmHubItem.setVisibility(8);
        linearLayout2.addView(this.gwmHubItem);
        this.launcherItems.add(this.gwmHubItem);
        bindGwmHub();
        updatePinnedVisibility();
        this.sideBySideItem = makeDockItem(iRound2, iRound, f);
        linearLayout2.addView(this.sideBySideItem);
        this.launcherItems.add(this.sideBySideItem);
        bindSideBySideItem();
        for (int i6 = 0; i6 < this.recentItems.length; i6++) {
            this.recentItems[i6] = makeDockItem(iRound2, iRound, f);
            this.recentItems[i6].setVisibility(8);
            linearLayout2.addView(this.recentItems[i6]);
            this.launcherItems.add(this.recentItems[i6]);
        }
        seedRecentAppsFromSystem(listQueryIntentActivities);
        this.dockItemsByPackage.clear();
        for (ResolveInfo resolveInfo3 : listQueryIntentActivities) {
            String str3 = resolveInfo3.activityInfo.packageName;
            if (!excludedFromAppRow(str3)) {
                String strLauncherLabel = launcherLabel(packageManager, resolveInfo3);
                Drawable drawableIconForLauncherApp = iconForLauncherApp(packageManager, resolveInfo3);
                MotionTrailLayout motionTrailLayoutMakeDockItem = makeDockItem(iRound2, iRound, f);
                bindDockItem(motionTrailLayoutMakeDockItem, drawableIconForLauncherApp, strLauncherLabel, view7 -> {
                    launchAppForPackage(str3, strLauncherLabel);
                }, str3);
                linearLayout2.addView(motionTrailLayoutMakeDockItem);
                this.launcherItems.add(motionTrailLayoutMakeDockItem);
                this.dockItemsByPackage.put(str3, motionTrailLayoutMakeDockItem);
                if (isUserHidden(str3)) {
                    motionTrailLayoutMakeDockItem.setVisibility(8);
                }
                Log.w("H6Viewer", "Launcher " + (isUserHidden(str3) ? "hidden " : "shown ") + str3 + " | " + strLauncherLabel + (isGwmApp(str3) ? " | gwm" : ""));
            }
        }
        if (isEmulatorDevice()) {
            String[][] strArr = EMULATOR_EXTRA_GWM_STUBS;
            int length = strArr.length;
            int i7 = 0;
            while (i7 < length) {
                String[] strArr2 = strArr[i7];
                String str6 = strArr2[0];
                String str7 = strArr2[1];
                if (map2.containsKey(str6)) {
                    i = length;
                } else if (excludedFromAppRow(str6)) {
                    i = length;
                } else {
                    this.emulatorStubPackages.add(str6);
                    int i8 = length;
                    MotionTrailLayout motionTrailLayoutMakeDockItem2 = makeDockItem(iRound2, iRound, f);
                    i = i8;
                    bindDockItem(motionTrailLayoutMakeDockItem2, launcherIconForPackage(str6), str7, view8 -> {
                        launchAppForPackage(str6, str7);
                    }, str6);
                    linearLayout2.addView(motionTrailLayoutMakeDockItem2);
                    this.launcherItems.add(motionTrailLayoutMakeDockItem2);
                    this.dockItemsByPackage.put(str6, motionTrailLayoutMakeDockItem2);
                    Log.w("H6Viewer", "Launcher emulator stub " + str6 + " | " + str7);
                }
                i7++;
                length = i;
            }
            z = true;
        } else {
            z = true;
        }
        bindRecentSlots(packageManager, listQueryIntentActivities);
        this.projectionPresence = new ProjectionPresence(this, this.mainHandler, this.mediaNowPlaying);
        this.projectionPresence.start(this::bindProjectionSlot);
        bindProjectionSlot(this.projectionPresence.current());
        bounceHorizontalScrollView2.addView(linearLayout2, new FrameLayout.LayoutParams(-2, -1));
        bounceHorizontalScrollView2.addOnLayoutChangeListener((view9, i9, i22, i32, i42, i52, i62, i72, i82) -> {
            int width = bounceHorizontalScrollView2.getWidth();
            if (width <= 0) {
                return;
            }
            linearLayout2.setMinimumWidth(width);
            linearLayout2.setGravity(8388659);
        });
        frameLayout3.addView(bounceHorizontalScrollView2, new FrameLayout.LayoutParams(-1, -1));
        this.cardsScrollView = buildQuickCardsRow(f, iRound);
        this.cardsScrollView.setVisibility("cards".equals(this.dockSurfaceMode) ? 0 : 8);
        this.cardsScrollView.setAlpha("cards".equals(this.dockSurfaceMode) ? 1.0f : 0.0f);
        frameLayout3.addView(this.cardsScrollView, new FrameLayout.LayoutParams(-1, -1));
        bounceHorizontalScrollView2.setVisibility(str.equals(this.dockSurfaceMode) ? 0 : 8);
        bounceHorizontalScrollView2.setAlpha(str.equals(this.dockSurfaceMode) ? 1.0f : 0.0f);
        this.layoutContentRow = buildLayoutContentRow(f, iRound2, iRound);
        this.layoutContentRow.setVisibility(8);
        this.layoutContentRow.setAlpha(0.0f);
        frameLayout3.addView(this.layoutContentRow, new FrameLayout.LayoutParams(-1, -1));
        this.configContentScroll = buildConfigContentRow(f, iRound2, iRound);
        this.configContentScroll.setVisibility(8);
        this.configContentScroll.setAlpha(0.0f);
        frameLayout3.addView(this.configContentScroll, new FrameLayout.LayoutParams(-1, -1));
        frameLayout2.setPadding(pageSideInsetPx(false), frameLayout2.getPaddingTop(), pageSideInsetPx(z), frameLayout2.getPaddingBottom());
        frameLayout.addView(view2);
        frameLayout.addView(frameLayout2);
        applyReserveBandGeometry();
        frameLayout2.addOnLayoutChangeListener((view10, i10, i23, i33, i43, i53, i63, i73, i83) -> {
            if (i10 == i53 && i23 == i63 && i33 == i73 && i43 == i83) {
                return;
            }
            this.mainHandler.post(() -> this.notifyViewerShellLayout());
        });
        bounceHorizontalScrollView2.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> bounceHorizontalScrollView2.setHorizontalFadingEdgeEnabled(scrollX > 0));
        this.mainHandler.postDelayed(this.launcherRevealFallback, 60000L);
        View view5 = new View(this);
        view5.setBackgroundColor(0);
        view5.setClickable(false);
        view5.setFocusable(false);
        view5.setVisibility(4);
        FrameLayout.LayoutParams layoutParams5 = new FrameLayout.LayoutParams(LEFT_POPUP_BOUNDS.width(), LEFT_POPUP_BOUNDS.height());
        layoutParams5.leftMargin = LEFT_POPUP_BOUNDS.left;
        layoutParams5.topMargin = LEFT_POPUP_BOUNDS.top;
        frameLayout.addView(view5, 0, layoutParams5);
        this.launchAnchor = view5;
        View view6 = new View(this);
        view6.setBackgroundColor(0);
        view6.setClickable(false);
        view6.setFocusable(false);
        view6.setVisibility(4);
        FrameLayout.LayoutParams layoutParams6 = new FrameLayout.LayoutParams(RIGHT_APP_BOUNDS.width(), RIGHT_APP_BOUNDS.height());
        layoutParams6.leftMargin = RIGHT_APP_BOUNDS.left;
        layoutParams6.topMargin = RIGHT_APP_BOUNDS.top;
        frameLayout.addView(view6, 0, layoutParams6);
        this.mediaLaunchAnchor = view6;
        applyDrawerCollapsedUi(false);
        refreshLayoutChipSelection();
        refreshDockSurfaceUi(false);
        updateSlotAnchors();
        updateAppsOnlyChrome();
    }

    private void setupSplashFade(FrameLayout frameLayout) {
        ImageView imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setBackgroundColor(-16777216);
        imageView.setVisibility(8);
        imageView.setClickable(false);
        imageView.setFocusable(false);
        frameLayout.addView(imageView, new FrameLayout.LayoutParams(-1, -1));
        this.splashFadeView = imageView;
        new Thread(() -> this.preloadSplashHoldFrame(), "splash-frame").start();
    }

    private void setupSplashSkip(FrameLayout frameLayout) {
        float f = getResources().getDisplayMetrics().density;
        TextView textView = new TextView(this);
        textView.setText("SKIP");
        textView.setTextColor(-385875969);
        textView.setTextSize(2, 12.0f);
        textView.setLetterSpacing(0.16f);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        int iRound = Math.round(16.0f * f);
        int iRound2 = Math.round(8.0f * f);
        textView.setPadding(iRound, iRound2, iRound, iRound2);
        textView.setBackgroundColor(1795162112);
        textView.setElevation(24.0f * f);
        textView.setClickable(true);
        textView.setFocusable(true);
        textView.setOnClickListener(view -> {
            hideSplashSkip();
            if (this.webView == null) {
                return;
            }
            this.webView.evaluateJavascript("(function(){try{if(window.HavalSplash&&window.HavalSplash.skip)window.HavalSplash.skip();}catch(e){}})()", null);
        });
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(-2, -2);
        layoutParams.gravity = 8388691;
        layoutParams.leftMargin = Math.round(14.0f * f);
        layoutParams.bottomMargin = compensatedBottomMarginPx(chromeBottomGapPx());
        frameLayout.addView(textView, layoutParams);
        this.splashSkipBtn = textView;
    }

    private boolean shouldLaunchAsWindow(String str) {
        return "appsOnly".equals(this.shellMode) || prefersWindow(str);
    }

    private void showAppsFabOverlay() {
        if (this.appsFabOverlayAttached) {
            refreshAppsFabMenu();
            return;
        }
        ensureAppsFabBuilt();
        if (this.appsFabRoot == null) {
            return;
        }
        if (tryAttachAppsFabSystemOverlay()) {
            this.appsFabUsesSystemOverlay = true;
            this.appsFabOverlayAttached = true;
            onAppsFabAttached("system-overlay");
        } else {
            if (!tryAttachAppsFabInWindow()) {
                Log.w("H6Viewer", "APP+APP FAB could not attach (overlay denied and no rootLayout)");
                return;
            }
            this.appsFabUsesSystemOverlay = false;
            this.appsFabOverlayAttached = true;
            onAppsFabAttached("in-window");
        }
    }

    private void showAppsFabScrim() {
        if (this.appsFabScrimAttached) {
            return;
        }
        ensureAppsFabScrimBuilt();
        try {
            if (this.appsFabUsesSystemOverlay) {
                getWindowManager().addView(this.appsFabScrim, this.appsFabScrimLp);
                if (this.appsFabOverlayAttached && this.appsFabRoot != null && this.appsFabLp != null) {
                    getWindowManager().removeView(this.appsFabRoot);
                    getWindowManager().addView(this.appsFabRoot, this.appsFabLp);
                }
            } else if (this.rootLayout != null) {
                this.rootLayout.addView(this.appsFabScrim, 0, new FrameLayout.LayoutParams(-1, -1));
                if (this.appsFabRoot != null) {
                    this.appsFabRoot.bringToFront();
                }
            }
            this.appsFabScrimAttached = true;
        } catch (Exception e) {
            Log.w("H6Viewer", "APP+APP FAB scrim attach failed", e);
        }
    }

    private void showBootHud(int i) {
        if (this.bootHudCenterMode) {
            if (this.bootProgressTrack != null) {
                this.bootProgressTrack.setVisibility(8);
            }
            if (this.bootCenterLoader != null) {
                this.bootCenterLoader.setVisibility(0);
                this.bootCenterLoader.bringToFront();
                return;
            }
            return;
        }
        if (this.bootCenterLoader != null) {
            this.bootCenterLoader.setVisibility(8);
        }
        if (this.bootProgressTrack == null || this.bootProgressFill == null) {
            return;
        }
        layoutBootProgressLine();
        this.bootProgressTrack.setVisibility(0);
        this.bootProgressTrack.bringToFront();
        int width = this.bootProgressTrack.getWidth();
        if (width <= 0) {
            width = getResources().getDisplayMetrics().widthPixels;
        }
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) this.bootProgressFill.getLayoutParams();
        layoutParams.width = Math.round(width * (Math.max(0, Math.min(100, i)) / 100.0f));
        this.bootProgressFill.setLayoutParams(layoutParams);
    }

    private void showDockCustomizeSheet(View view, String str) {
        String strPinnedLabel;
        TextView textView;
        Drawable drawable;
        if (view == null || str == null) {
            return;
        }
        if (str.isEmpty()) {
            return;
        }
        dismissDockCustomizeSheet();
        float f = getResources().getDisplayMetrics().density;
        if ("__gwm_hub".equals(str)) {
            strPinnedLabel = "GWM";
        } else if (isPinnedPackage(str)) {
            strPinnedLabel = pinnedLabel(str, getPackageManager(), null);
        } else {
            MotionTrailLayout motionTrailLayout = this.dockItemsByPackage.get(str);
            String strTrim = (motionTrailLayout == null || (textView = (TextView) motionTrailLayout.findViewWithTag("label")) == null || textView.getText() == null) ? null : textView.getText().toString().trim();
            String string = strTrim != null ? strTrim : str;
            try {
                PackageManager packageManager = getPackageManager();
                CharSequence applicationLabel = packageManager.getApplicationLabel(packageManager.getApplicationInfo(str, 0));
                if (applicationLabel != null) {
                    string = applicationLabel.toString();
                }
            } catch (Exception e) {
            }
            strPinnedLabel = string;
        }
        String strName = this.dockAppOverrides != null ? this.dockAppOverrides.name(str) : null;
        String[] strArr = {this.dockAppOverrides != null ? this.dockAppOverrides.icon(str) : null};
        String[] strArr2 = {(this.dockAppOverrides == null || this.dockAppOverrides.color(str) == null) ? DockAppOverrides.COLOR_DEFAULT : this.dockAppOverrides.color(str)};
        String[] strArr3 = {(this.dockAppOverrides == null || this.dockAppOverrides.bg(str) == null) ? DockAppOverrides.BG_DEFAULT : this.dockAppOverrides.bg(str)};
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackground(makeDockPopupPanel(f));
        scrollView.setClipToOutline(true);
        scrollView.setElevation(f * 12.0f);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        int iRound = Math.round(f * 16.0f);
        linearLayout.setPadding(iRound, iRound, iRound, iRound);
        scrollView.addView(linearLayout, new FrameLayout.LayoutParams(-1, -2));
        linearLayout.addView(makeDockPopupKicker("PERSONALIZAR", f));
        EditText editText = new EditText(this);
        editText.setHint(strPinnedLabel);
        editText.setText(strName != null ? strName : "");
        editText.setHintTextColor(dockLabelColorMuted());
        editText.setTextColor(dockLabelColor());
        editText.setTextSize(16.0f);
        editText.setSingleLine(true);
        editText.setInputType(8193);
        editText.setImeOptions(6);
        editText.setBackground(makeDockPopupFieldBackground(f));
        editText.setPadding(Math.round(f * 12.0f), Math.round(f * 10.0f), Math.round(f * 12.0f), Math.round(f * 10.0f));
        linearLayout.addView(editText);
        TextView textViewMakeDockPopupKicker = makeDockPopupKicker("ÍCONE", f);
        textViewMakeDockPopupKicker.setPadding(0, Math.round(f * 12.0f), 0, Math.round(f * 6.0f));
        linearLayout.addView(textViewMakeDockPopupKicker);
        int iRound2 = Math.round(48.0f * f);
        int iRound3 = Math.round(7.0f * f);
        int iRound4 = Math.round(f * 6.0f);
        int i = getResources().getDisplayMetrics().widthPixels;
        int iMax = Math.max(7, Math.min(14, (((i - Math.round(24.0f * f)) - (iRound * 2)) + iRound4) / (iRound2 + iRound4)));
        int i2 = ((iRound2 + iRound4) * iMax) + (iRound * 2);
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(1);
        linearLayout.addView(linearLayout2);
        List<View> arrayList = new ArrayList<>();
        LinearLayout[] linearLayoutArr = {null};
        int[] iArr = {0};
        int i3 = iRound3;
        Runnable refreshColorSwatches = () -> {
            int i12;
            int i22;
            int color = DockAppOverrides.parseColor(strArr3[0], -12761520);
            Iterator it = arrayList.iterator();
            while (it.hasNext()) {
                View view2 = (View) it.next();
                Object tag = view2.getTag();
                boolean z = (tag == null && strArr[0] == null) || (tag != null && tag.equals(strArr[0]));
                GradientDrawable gradientDrawable = new GradientDrawable();
                gradientDrawable.setCornerRadius(10.0f * f);
                if (tag == null) {
                    i12 = this.dockUiLight ? 254095419 : 352321535;
                } else {
                    i12 = color;
                }
                gradientDrawable.setColor(i12);
                int iRound7 = Math.round((z ? 2.5f : 1.0f) * f);
                if (z) {
                    i22 = this.dockAccentColor;
                } else {
                    i22 = this.dockUiLight ? 572862523 : 872415231;
                }
                gradientDrawable.setStroke(iRound7, i22);
                view2.setBackground(gradientDrawable);
                if ((tag instanceof String) && (view2 instanceof FrameLayout)) {
                    FrameLayout frameLayout3 = (FrameLayout) view2;
                    if (frameLayout3.getChildCount() > 0) {
                        View childAt = frameLayout3.getChildAt(0);
                        if (childAt instanceof ImageView) {
                            ((ImageView) childAt).setImageDrawable(DockAppOverrides.drawableFor(this, (String) tag, strArr2[0]));
                        }
                    }
                }
            }
        };
        FrameLayout frameLayout = new FrameLayout(this);
        frameLayout.setPadding(i3, i3, i3, i3);
        frameLayout.setTag(null);
        TextView textView2 = new TextView(this);
        textView2.setText("Padrão");
        textView2.setTextColor(dockLabelColor());
        textView2.setTextSize(8.0f);
        textView2.setGravity(17);
        frameLayout.addView(textView2, new FrameLayout.LayoutParams(-1, -1));
        frameLayout.setOnClickListener(view3 -> {
            strArr[0] = null;
            refreshColorSwatches.run();
            View viewFindViewWithTag = linearLayout.findViewWithTag("dockColorHost");
            View viewFindViewWithTag2 = linearLayout.findViewWithTag("dockTintBlock");
            View viewFindViewWithTag3 = linearLayout.findViewWithTag("dockBgBlock");
            if (viewFindViewWithTag != null) {
                viewFindViewWithTag.setVisibility(8);
            }
            if (viewFindViewWithTag2 != null) {
                viewFindViewWithTag2.setVisibility(8);
            }
            if (viewFindViewWithTag3 != null) {
                viewFindViewWithTag3.setVisibility(8);
            }
        });
        LinearLayout linearLayout3 = linearLayout2;
        addDockIconGridCell(linearLayout3, linearLayoutArr, iArr, arrayList, iMax, iRound2, iRound4, frameLayout);
        String[][] strArr4 = DockAppOverrides.SUBSTITUTE_ICONS;
        int length = strArr4.length;
        int i4 = 0;
        while (i4 < length) {
            int i5 = i4;
            String str2 = strArr4[i4][0];
            FrameLayout frameLayout2 = new FrameLayout(this);
            LinearLayout linearLayout4 = linearLayout3;
            frameLayout2.setPadding(i3, i3, i3, i3);
            frameLayout2.setTag(str2);
            ImageView imageView = new ImageView(this);
            Drawable drawableDrawableFor = DockAppOverrides.drawableFor(this, str2, strArr2[0]);
            if (drawableDrawableFor == null && "gwm".equals(str2)) {
                try {
                    drawable = getDrawable(R.drawable.ic_gwm_viewer);
                } catch (Exception e2) {
                    drawable = drawableDrawableFor;
                }
            } else {
                drawable = drawableDrawableFor;
            }
            imageView.setImageDrawable(drawable);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            frameLayout2.addView(imageView, new FrameLayout.LayoutParams(-1, -1));
            frameLayout2.setOnClickListener(view4 -> {
                strArr[0] = str2;
                refreshColorSwatches.run();
                View viewFindViewWithTag4 = linearLayout.findViewWithTag("dockColorHost");
                View viewFindViewWithTag22 = linearLayout.findViewWithTag("dockTintBlock");
                View viewFindViewWithTag32 = linearLayout.findViewWithTag("dockBgBlock");
                if (viewFindViewWithTag4 != null) {
                    viewFindViewWithTag4.setVisibility(0);
                }
                if (viewFindViewWithTag22 != null) {
                    viewFindViewWithTag22.setVisibility(0);
                }
                if (viewFindViewWithTag32 != null) {
                    viewFindViewWithTag32.setVisibility(0);
                }
            });
            linearLayout3 = linearLayout4;
            addDockIconGridCell(linearLayout3, linearLayoutArr, iArr, arrayList, iMax, iRound2, iRound4, frameLayout2);
            i4 = i5 + 1;
        }
        refreshColorSwatches.run();
        LinearLayout linearLayout5 = new LinearLayout(this);
        linearLayout5.setOrientation(0);
        linearLayout5.setTag("dockColorHost");
        linearLayout5.setPadding(0, Math.round(f * 8.0f), 0, 0);
        linearLayout.addView(linearLayout5);
        LinearLayout linearLayout6 = new LinearLayout(this);
        linearLayout6.setOrientation(1);
        linearLayout6.setTag("dockTintBlock");
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams.rightMargin = Math.round(f * 12.0f);
        linearLayout5.addView(linearLayout6, layoutParams);
        linearLayout6.addView(makeDockPopupKicker("COR", f));
        LinearLayout linearLayout7 = new LinearLayout(this);
        linearLayout7.setOrientation(0);
        linearLayout6.addView(linearLayout7);
        LinearLayout linearLayout8 = new LinearLayout(this);
        linearLayout8.setOrientation(1);
        linearLayout8.setTag("dockBgBlock");
        linearLayout5.addView(linearLayout8, new LinearLayout.LayoutParams(0, -2, 1.0f));
        linearLayout8.addView(makeDockPopupKicker("FUNDO", f));
        LinearLayout linearLayout9 = new LinearLayout(this);
        linearLayout9.setOrientation(0);
        linearLayout8.addView(linearLayout9);
        int i6 = strArr[0] == null ? 8 : 0;
        linearLayout5.setVisibility(i6);
        linearLayout6.setVisibility(i6);
        linearLayout8.setVisibility(i6);
        int length2 = DockAppOverrides.COLORS.length;
        int iRound5 = Math.round(5.0f * f);
        int iMax2 = Math.max(1, ((i2 - (iRound * 2)) - Math.round(f * 12.0f)) / 2);
        int iMax3 = Math.max(Math.round(20.0f * f), Math.min(Math.round(28.0f * f), (iMax2 - ((length2 - 1) * iRound5)) / length2));
        addDockColorSwatches(linearLayout7, iMax3, iRound5, f);
        addDockColorSwatches(linearLayout9, iMax3, iRound5, f);
        Runnable refreshStyleSelection = () -> {
            int i13 = 0;
            while (true) {
                if (i13 >= linearLayout7.getChildCount()) {
                    break;
                }
                View childAt3 = linearLayout7.getChildAt(i13);
                boolean z3 = strArr2[0] != null && strArr2[0].equalsIgnoreCase(String.valueOf(childAt3.getTag()));
                childAt3.setAlpha(z3 ? 1.0f : 0.5f);
                if (childAt3 instanceof FrameLayout) {
                    ((FrameLayout) childAt3).setForeground(z3 ? new ColorDrawable(1728053247) : null);
                }
                i13++;
            }
            for (int i23 = 0; i23 < linearLayout9.getChildCount(); i23++) {
                View childAt2 = linearLayout9.getChildAt(i23);
                boolean z2 = strArr3[0] != null && strArr3[0].equalsIgnoreCase(String.valueOf(childAt2.getTag()));
                childAt2.setAlpha(z2 ? 1.0f : 0.5f);
                if (childAt2 instanceof FrameLayout) {
                    ((FrameLayout) childAt2).setForeground(z2 ? new ColorDrawable(1728053247) : null);
                }
            }
        };
        int i7 = 0;
        while (true) {
            int i8 = iMax2;
            if (i7 >= linearLayout7.getChildCount()) {
                break;
            }
            linearLayout7.getChildAt(i7).setOnClickListener(view5 -> {
                strArr2[0] = String.valueOf(view5.getTag());
                refreshStyleSelection.run();
                refreshColorSwatches.run();
            });
            i7++;
            iMax2 = i8;
        }
        for (int i9 = 0; i9 < linearLayout9.getChildCount(); i9++) {
            linearLayout9.getChildAt(i9).setOnClickListener(view6 -> {
                strArr3[0] = String.valueOf(view6.getTag());
                refreshStyleSelection.run();
                refreshColorSwatches.run();
            });
        }
        refreshStyleSelection.run();
        LinearLayout linearLayout10 = new LinearLayout(this);
        linearLayout10.setOrientation(0);
        linearLayout10.setPadding(0, Math.round(14.0f * f), 0, 0);
        linearLayout.addView(linearLayout10);
        linearLayout10.addView(makeDockPopupAction("RESET", false, f, () -> {
            if (this.dockAppOverrides != null) {
                this.dockAppOverrides.clear(str);
                this.dockAppOverrides.save(getSharedPreferences("opengw_lancador", 0));
            }
            dismissDockCustomizeSheet();
            rebindDockPackage(str);
        }));
        linearLayout10.addView(makeDockPopupAction("SAVE", true, f, () -> {
            if (this.dockAppOverrides == null) {
                this.dockAppOverrides = new DockAppOverrides(null);
            }
            this.dockAppOverrides.put(str, editText.getText() != null ? editText.getText().toString() : "", strArr[0], strArr2[0], strArr3[0]);
            this.dockAppOverrides.save(getSharedPreferences("opengw_lancador", 0));
            dismissDockCustomizeSheet();
            rebindDockPackage(str);
        }));
        PopupWindow popupWindow = new PopupWindow((View) scrollView, i2, -2, true);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(0));
        popupWindow.setFocusable(true);
        popupWindow.setOnDismissListener(() -> {
            if (this.dockCustomizeSheet == popupWindow) {
                this.dockCustomizeSheet = null;
            }
        });
        this.dockCustomizeSheet = popupWindow;
        scrollView.measure(View.MeasureSpec.makeMeasureSpec(i2, Integer.MIN_VALUE), 0);
        int i10 = -(Math.min(scrollView.getMeasuredHeight(), Math.round(520.0f * f)) + view.getHeight() + Math.round(f * 8.0f));
        int[] iArr2 = new int[2];
        view.getLocationOnScreen(iArr2);
        int iRound6 = Math.round(12.0f * f);
        int i11 = iArr2[0] + i2 > i - iRound6 ? ((i - iRound6) - i2) - iArr2[0] : 0;
        popupWindow.showAsDropDown(view, iArr2[0] + i11 < iRound6 ? iRound6 - iArr2[0] : i11, i10);
    }

    private void showDockEditMenu(View view, String str) {
        if (view == null || str == null || str.isEmpty()) {
            return;
        }
        dismissDockEditMenu();
        dismissGwmHubMenu();
        float f = getResources().getDisplayMetrics().density;
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setBackgroundColor(-233564128);
        linearLayout.setElevation(f * 12.0f);
        int iRound = Math.round(18.0f * f);
        int iRound2 = Math.round(12.0f * f);
        linearLayout.setPadding(iRound, iRound2, iRound, iRound2);
        linearLayout.setMinimumWidth(Math.round(220.0f * f));
        boolean zEquals = "__gwm_hub".equals(str);
        linearLayout.addView(makeDockMenuRow("Customize…", () -> {
            dismissDockEditMenu();
            showDockCustomizeSheet(view, str);
        }));
        linearLayout.addView(makeDockMenuRow("Hide", () -> {
            dismissDockEditMenu();
            if (zEquals) {
                hideGwmHub();
            } else {
                removeDockPackage(str, true);
            }
        }));
        if (!zEquals && canToggleOpenAsWindow(str)) {
            linearLayout.addView(makeDockMenuRow(prefersWindow(str) ? "✓ Open as window" : "Open as window", () -> {
                dismissDockEditMenu();
                toggleOpenAsWindow(str);
            }));
        }
        if (!this.hiddenPackages.isEmpty()) {
            linearLayout.addView(makeDockMenuRow("Unhide selected…", () -> {
                dismissDockEditMenu();
                showUnhidePicker();
            }));
        }
        if (!zEquals && canUninstallPackage(str)) {
            linearLayout.addView(makeDockMenuRow("Uninstall", () -> {
                dismissDockEditMenu();
                try {
                    Intent intent = new Intent("android.intent.action.DELETE", Uri.parse("package:" + str));
                    intent.addFlags(268435456);
                    startActivity(intent);
                } catch (Exception e) {
                    Log.w("H6Viewer", "Uninstall failed for " + str, e);
                }
            }));
        }
        PopupWindow popupWindow = new PopupWindow((View) linearLayout, -2, -2, true);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(0));
        popupWindow.setOnDismissListener(() -> {
            if (this.dockEditMenu == popupWindow) {
                this.dockEditMenu = null;
            }
        });
        this.dockEditMenu = popupWindow;
        linearLayout.measure(0, 0);
        popupWindow.showAsDropDown(view, (view.getWidth() - linearLayout.getMeasuredWidth()) / 2, -(linearLayout.getMeasuredHeight() + view.getHeight() + Math.round(8.0f * f)));
    }

    private void showGwmHubMenu(View view) {
        float f;
        float f2;
        if (view == null) {
            return;
        }
        dismissGwmHubMenu();
        dismissDockEditMenu();
        float f3 = getResources().getDisplayMetrics().density;
        PackageManager packageManager = getPackageManager();
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setBackgroundColor(-233564128);
        float f4 = 14.0f;
        linearLayout.setElevation(f3 * 14.0f);
        int iRound = Math.round(18.0f * f3);
        linearLayout.setPadding(iRound, Math.round(f3 * 12.0f), iRound, Math.round(12.0f * f3));
        linearLayout.setMinimumWidth(Math.round(280.0f * f3));
        int i = 0;
        int iRound2 = Math.round(40.0f * f3);
        int i2 = 0;
        while (i2 < PINNED_PACKAGES.length) {
            if (this.pinnedBound[i2]) {
                String str = PINNED_PACKAGES[i2];
                if (isUserHidden(str)) {
                    f = f3;
                    f2 = f4;
                } else {
                    String strResolveDockLabel = resolveDockLabel(str, pinnedLabel(str, packageManager, null));
                    Drawable drawableFlyoutIconForPinned = flyoutIconForPinned(str);
                    f2 = f4;
                    LinearLayout linearLayout2 = new LinearLayout(this);
                    linearLayout2.setOrientation(0);
                    linearLayout2.setGravity(16);
                    f = f3;
                    linearLayout2.setPadding(Math.round(f3 * 8.0f), Math.round(f3 * f2), Math.round(f3 * 8.0f), Math.round(f3 * f2));
                    linearLayout2.setMinimumHeight(Math.round(56.0f * f));
                    linearLayout2.setClickable(true);
                    linearLayout2.setFocusable(true);
                    ImageView imageView = new ImageView(this);
                    imageView.setImageDrawable(drawableFlyoutIconForPinned);
                    LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(iRound2, iRound2);
                    layoutParams.rightMargin = Math.round(f * f2);
                    linearLayout2.addView(imageView, layoutParams);
                    TextView textView = new TextView(this);
                    textView.setText(strResolveDockLabel);
                    textView.setTextColor(-1);
                    textView.setTextSize(17.0f);
                    linearLayout2.addView(textView);
                    linearLayout2.setOnClickListener(view2 -> {
                        dismissGwmHubMenu();
                        launchAppForPackage(str, strResolveDockLabel);
                    });
                    linearLayout.addView(linearLayout2);
                    i++;
                }
            } else {
                f = f3;
                f2 = f4;
            }
            i2++;
            f4 = f2;
            f3 = f;
        }
        float f5 = f3;
        if (i == 0) {
            TextView textView2 = new TextView(this);
            textView2.setText("Nenhum app GWM");
            textView2.setTextColor(-1711276033);
            textView2.setTextSize(15.0f);
            textView2.setPadding(Math.round(f5 * 8.0f), Math.round(f5 * 10.0f), Math.round(f5 * 8.0f), Math.round(f5 * 10.0f));
            linearLayout.addView(textView2);
        }
        PopupWindow popupWindow = new PopupWindow((View) linearLayout, -2, -2, true);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(0));
        popupWindow.setOnDismissListener(() -> {
            if (this.gwmHubMenu == popupWindow) {
                this.gwmHubMenu = null;
            }
        });
        this.gwmHubMenu = popupWindow;
        linearLayout.measure(0, 0);
        popupWindow.showAsDropDown(view, (view.getWidth() - linearLayout.getMeasuredWidth()) / 2, -(linearLayout.getMeasuredHeight() + view.getHeight() + Math.round(10.0f * f5)));
    }

    private void showQuickMenu(View view, BottomCardDescriptor bottomCardDescriptor) {
        showQuickMenuRows(view, bottomCardDescriptor.menu, true);
    }

    private void showQuickMenuRows(View view, List<QuickMenuRow> list, boolean separateLastRow) {
        if (list == null || list.isEmpty()) {
            return;
        }
        dismissQuickMenu();
        float f = getResources().getDisplayMetrics().density;
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        int iRound = Math.round(f * 6.0f);
        linearLayout.setPadding(iRound, iRound, iRound, iRound);
        linearLayout.setBackground(makeDockPopupPanel(f));
        linearLayout.setElevation(f * 12.0f);
        for (QuickMenuRow next : list) {
            String command = next.command;
            boolean isSeparatedRow = separateLastRow && next == list.get(list.size() - 1);
            TextView textView = new TextView(this);
            textView.setText(next.label);
            textView.setTextSize(isSeparatedRow ? 13.0f : 15.0f);
            textView.setTypeface(Typeface.create(next.selected ? "sans-serif-medium" : "sans-serif", 0));
            int textColor;
            if (next.selected) {
                textColor = this.dockAccentColor;
            } else {
                textColor = isSeparatedRow ? dockLabelColorMuted() : dockLabelColor();
            }
            textView.setTextColor(textColor);
            textView.setGravity(17);
            textView.setBackground(makeQuickMenuItemBackground(next.selected, isSeparatedRow, f));
            int iRound2 = Math.round(f * 12.0f);
            textView.setPadding(iRound2, Math.round(f * 12.0f), iRound2, Math.round(f * 12.0f));
            textView.setClickable(true);
            textView.setOnClickListener(v -> {
                dismissQuickMenu();
                callViewerDock(command);
            });
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(Math.round(200.0f * f), -2);
            layoutParams.bottomMargin = Math.round(f * 6.0f);
            textView.setLayoutParams(layoutParams);
            if (isSeparatedRow) {
                View divider = new View(this);
                LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, Math.max(1, Math.round(f)));
                layoutParams2.topMargin = Math.round(2.0f * f);
                layoutParams2.bottomMargin = Math.round(f * 8.0f);
                divider.setLayoutParams(layoutParams2);
                divider.setBackgroundColor(this.dockUiLight ? 438644795 : 536870911);
                linearLayout.addView(divider);
            }
            linearLayout.addView(textView);
        }
        this.quickMenuWindow = new PopupWindow((View) linearLayout, -2, -2, true);
        this.quickMenuWindow.setBackgroundDrawable(new ColorDrawable(0));
        this.quickMenuWindow.setOutsideTouchable(true);
        linearLayout.measure(0, 0);
        this.quickMenuWindow.showAsDropDown(view, 0, -(linearLayout.getMeasuredHeight() + view.getHeight() + Math.round(f * 8.0f)));
    }

    private void showStripContent(String str, boolean z) {
        View view = this.appsScrollView;
        View view2 = this.cardsScrollView;
        View view3 = this.layoutContentRow;
        View view4 = this.configContentScroll;
        View view5 = (this.railEditMode || "cards".equals(this.dockSurfaceMode)) ? view2 : view;
        if ("layout".equals(str)) {
            view5 = view3;
        } else if ("config".equals(str)) {
            view5 = view4;
        }
        int i = 0;
        View[] viewArr = {view, view2, view3, view4};
        int length = viewArr.length;
        int i2 = 0;
        while (i2 < length) {
            View view6 = viewArr[i2];
            if (view6 != null) {
                if (view6 == view5) {
                    view6.setVisibility(i);
                    if (z) {
                        view6.animate().alpha(1.0f).setDuration(180L).start();
                    } else {
                        view6.setAlpha(1.0f);
                    }
                } else if (view6.getVisibility() == 0) {
                    if (z) {
                        view6.animate().alpha(0.0f).setDuration(160L).withEndAction(() -> view6.setVisibility(8)).start();
                    } else {
                        view6.setAlpha(0.0f);
                        view6.setVisibility(8);
                    }
                }
            }
            i2++;
            i = 0;
        }
        if ("layout".equals(str)) {
            refreshLayoutChipSelection();
        }
        applyDockPanelBackground();
    }

    private void showUnhidePicker() {
        if (this.hiddenPackages.isEmpty()) {
            return;
        }
        dismissUnhidePicker();
        float f = getResources().getDisplayMetrics().density;
        PackageManager packageManager = getPackageManager();
        Intent intent = new Intent("android.intent.action.MAIN", (Uri) null);
        intent.addCategory("android.intent.category.LAUNCHER");
        HashMap map = new HashMap();
        int i = 0;
        List<ResolveInfo> listQueryIntentActivities = packageManager.queryIntentActivities(intent, 0);
        if (listQueryIntentActivities != null) {
            for (ResolveInfo resolveInfo : listQueryIntentActivities) {
                if (resolveInfo != null && resolveInfo.activityInfo != null) {
                    map.put(resolveInfo.activityInfo.packageName, resolveInfo);
                }
            }
        }
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setBackgroundColor(-233564128);
        linearLayout.setElevation(f * 12.0f);
        int iRound = Math.round(f * 14.0f);
        float f2 = 10.0f;
        linearLayout.setPadding(iRound, Math.round(f * 10.0f), iRound, Math.round(f * 10.0f));
        TextView textView = new TextView(this);
        textView.setText("Hidden apps");
        textView.setTextColor(-1711276033);
        textView.setTextSize(14.0f);
        textView.setPadding(Math.round(f * 6.0f), 0, Math.round(f * 6.0f), Math.round(6.0f * f));
        linearLayout.addView(textView);
        ArrayList arrayList = new ArrayList(this.hiddenPackages);
        Collections.sort(arrayList);
        int iRound2 = Math.round(36.0f * f);
        Iterator it = arrayList.iterator();
        while (true) {
            float f3 = f2;
            if (!it.hasNext()) {
                break;
            }
            String str = (String) it.next();
            ResolveInfo resolveInfo2 = (ResolveInfo) map.get(str);
            String strPinnedLabel = resolveInfo2 != null ? pinnedLabel(str, packageManager, resolveInfo2) : str;
            Drawable drawableIconForLauncherApp = resolveInfo2 != null ? iconForLauncherApp(packageManager, resolveInfo2) : null;
            LinearLayout linearLayout2 = new LinearLayout(this);
            linearLayout2.setOrientation(i);
            linearLayout2.setGravity(16);
            PackageManager packageManager2 = packageManager;
            Intent intent2 = intent;
            HashMap map2 = map;
            linearLayout2.setPadding(Math.round(f * 8.0f), Math.round(f * 12.0f), Math.round(f * 8.0f), Math.round(f * 12.0f));
            linearLayout2.setMinimumHeight(Math.round(52.0f * f));
            linearLayout2.setClickable(true);
            linearLayout2.setFocusable(true);
            ImageView imageView = new ImageView(this);
            FrameLayout frameLayoutWrapLauncherIconPlate = wrapLauncherIconPlate(imageView, iRound2, f, usesDarkIconPlate(str));
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, -2);
            layoutParams.rightMargin = Math.round(f * f3);
            frameLayoutWrapLauncherIconPlate.setLayoutParams(layoutParams);
            float f4 = f;
            imageView.setImageDrawable(normalizeAdaptiveIcon(drawableIconForLauncherApp, iRound2));
            linearLayout2.addView(frameLayoutWrapLauncherIconPlate);
            TextView textView2 = new TextView(this);
            textView2.setText(strPinnedLabel);
            textView2.setTextColor(-1);
            textView2.setTextSize(16.0f);
            textView2.setSingleLine(true);
            textView2.setEllipsize(TextUtils.TruncateAt.END);
            textView2.setMaxWidth(Math.round(240.0f * f4));
            linearLayout2.addView(textView2);
            linearLayout2.setOnClickListener(view2 -> {
                dismissUnhidePicker();
                restoreDockPackage(str);
            });
            linearLayout.addView(linearLayout2);
            f2 = f3;
            f = f4;
            intent = intent2;
            packageManager = packageManager2;
            map = map2;
            i = 0;
        }
        ScrollView scrollView = new ScrollView(this);
        scrollView.setOverScrollMode(2);
        scrollView.addView(linearLayout);
        PopupWindow popupWindow = new PopupWindow((View) scrollView, -2, -2, true);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(0));
        popupWindow.setOnDismissListener(() -> {
            if (this.unhidePicker == popupWindow) {
                this.unhidePicker = null;
            }
        });
        this.unhidePicker = popupWindow;
        View view = this.stripContainer != null ? this.stripContainer : this.webView;
        if (view == null) {
            this.unhidePicker = null;
        } else {
            popupWindow.showAtLocation(view, 17, 0, 0);
        }
    }

    private boolean skipInAppRow(String str) {
        return excludedFromAppRow(str) || isUserHidden(str);
    }

    private boolean skipInAppsCatalog(String str) {
        if (str == null || str.isEmpty() || str.equals(getPackageName()) || IGNORED_PACKAGES.contains(str) || ProjectionPresence.isProjectionPackage(str)) {
            return true;
        }
        return isUserHidden(str);
    }

    private boolean skipInRecents(String str) {
        return skipInAppRow(str);
    }

    private boolean slotAllowsApp(String str) {
        if ("right".equals(str)) {
            return !"appCar".equals(this.shellMode);
        }
        return true;
    }

    private int slotBandBottomPx() {
        return Math.max(slotBandTopPx() + 200, panelHeightPx() - dockReservePx());
    }

    private int slotBandTopPx() {
        return Math.max(statusBarHeightPx(), this.widestTopInsetPx) + chromeReservePx();
    }

    private int splitDividerX(Rect rect) {
        float f = 0.5f;
        if ("2:1".equals(this.splitRatio)) {
            f = 0.6666667f;
        } else if ("1:2".equals(this.splitRatio)) {
            f = 0.33333334f;
        }
        return rect.left + Math.round(rect.width() * f);
    }

    private void startMediaVisualizerIfNeeded() {
        if (this.mediaAudioViz == null) {
            this.mediaAudioViz = new MediaAudioVisualizer();
            this.mediaAudioViz.setOnUpdate(() -> {
                if (this.quickMediaBars != null) {
                    this.quickMediaBars.ensureTicking();
                }
            });
        }
        this.mediaAudioViz.setWanted(true);
    }

    private void startOverlayWatchdog() {
        this.mainHandler.removeCallbacks(this.overlayWatchdog);
        this.mainHandler.postDelayed(this.overlayWatchdog, 700L);
    }

    private int statusBarHeightPx() {
        int identifier = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (identifier > 0) {
            return getResources().getDimensionPixelSize(identifier);
        }
        return 0;
    }

    private boolean statusBarShown() {
        return !"full".equals(this.uiMode);
    }

    private void stopOverlayWatchdog() {
        this.mainHandler.removeCallbacks(this.overlayWatchdog);
    }

    private void styleAppsFabIconCell(View view, boolean z, float f) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(z ? 776984296 : 285212671);
        gradientDrawable.setCornerRadius(10.0f * f);
        gradientDrawable.setStroke(Math.max(1, Math.round(1.0f * f)), z ? -1504717080 : 704643071);
        view.setBackground(gradientDrawable);
    }

    private void styleCardsDockCell(View view, float f, int i) {
        if (view == null) {
            return;
        }
        View viewFindViewWithTag = view.findViewWithTag("modePlate");
        if (viewFindViewWithTag != null) {
            viewFindViewWithTag.setBackground(makeCircleFrostDrawable(false, f));
        }
        ImageView imageView = (ImageView) view.findViewWithTag("modeIcon");
        if (imageView == null) {
            return;
        }
        int iCardsDockGlyphPx = cardsDockGlyphPx(i, f);
        ViewGroup.LayoutParams layoutParams = imageView.getLayoutParams();
        layoutParams.width = iCardsDockGlyphPx;
        layoutParams.height = iCardsDockGlyphPx;
        imageView.setLayoutParams(layoutParams);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setImageDrawable(dockGlyphCards(iCardsDockGlyphPx, dockGlyphColor(false)));
    }

    private void styleDockCaption(TextView textView, float f) {
        textView.setTextSize(11.0f);
        textView.setTextColor(-419430401);
        textView.setGravity(49);
        textView.setMaxLines(2);
        textView.setMinLines(2);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setLineSpacing(0.0f, 0.92f);
        textView.setIncludeFontPadding(false);
        textView.setShadowLayer(3.0f, 0.0f, 1.0f, -872415232);
        textView.setPadding(Math.round(f * 2.0f), Math.round(4.0f * f), Math.round(2.0f * f), 0);
    }

    private void styleDockLabel(TextView textView, boolean z) {
        if (textView == null) {
            return;
        }
        textView.setTextColor(z ? dockLabelColorMuted() : dockLabelColor());
    }

    private void styleLayoutChipLabel(View view) {
        if (view == null) {
            return;
        }
        for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View childAt = ((ViewGroup) view).getChildAt(i);
            if (childAt instanceof LinearLayout) {
                LinearLayout linearLayout = (LinearLayout) childAt;
                for (int i2 = 0; i2 < linearLayout.getChildCount(); i2++) {
                    View childAt2 = linearLayout.getChildAt(i2);
                    if (childAt2 instanceof TextView) {
                        styleDockLabel((TextView) childAt2, false);
                    }
                }
            }
        }
    }

    private void styleWorkspaceLayoutCells() {
        int iWithAlpha;
        float f = getResources().getDisplayMetrics().density;
        for (TextView textView : this.workspaceLayoutCells) {
            String strSubstring = String.valueOf(textView.getTag()).substring("workspaceLayoutCell:".length());
            boolean zEquals = "return".equals(strSubstring);
            boolean z = !zEquals && strSubstring.equals(this.studioScreen);
            textView.setTextColor(z ? this.dockAccentColor : dockLabelColor());
            GradientDrawable gradientDrawable = new GradientDrawable();
            gradientDrawable.setCornerRadius(9.0f * f);
            if (zEquals) {
                iWithAlpha = withAlpha(this.dockAccentColor, 51);
            } else if (z) {
                iWithAlpha = withAlpha(this.dockAccentColor, 46);
            } else {
                iWithAlpha = this.dockUiLight ? 251658240 : 318767103;
            }
            gradientDrawable.setColor(iWithAlpha);
            if (z) {
                gradientDrawable.setStroke(Math.max(1, Math.round(f)), withAlpha(this.dockAccentColor, 160));
            }
            Drawable drawable = gradientDrawable;
            if (zEquals) {
                textView.setText("");
                LayerDrawable layerDrawable = new LayerDrawable(new Drawable[]{gradientDrawable, workspaceBackGlyph(f, this.dockAccentColor)});
                layerDrawable.setLayerGravity(1, 17);
                drawable = layerDrawable;
            }
            textView.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(dockLabelColor(), 51)), drawable, null));
        }
    }

    private void swapSlotApps() {
        String str = this.activePopupPackage;
        String str2 = this.activeMediaPackage;
        ComponentName componentName = this.activePopupComponent;
        ComponentName componentName2 = this.activeMediaComponent;
        int i = this.activePopupTaskId;
        int i2 = this.activeMediaTaskId;
        boolean z = (str == null || str.isEmpty()) ? false : true;
        boolean z2 = (str2 == null || str2.isEmpty()) ? false : true;
        if (z || z2) {
            this.activePopupPackage = str2 != null ? str2 : "";
            this.activePopupComponent = componentName2;
            this.activePopupTaskId = i2;
            this.activeMediaPackage = str != null ? str : "";
            this.activeMediaComponent = componentName;
            this.activeMediaTaskId = i;
            updateSlotAnchors();
            if (this.activePopupPackage != null && !this.activePopupPackage.isEmpty()) {
                pinSlotToBounds(this.activePopupPackage, this.activePopupComponent, this.activePopupTaskId, leftFreeformBounds(), false);
            }
            if (this.activeMediaPackage != null && !this.activeMediaPackage.isEmpty()) {
                pinSlotToBounds(this.activeMediaPackage, this.activeMediaComponent, this.activeMediaTaskId, rightFreeformBounds(), true);
            }
            notifyViewerShellLayout();
            Log.w("H6Viewer", "Swapped slots: left=" + this.activePopupPackage + " right=" + this.activeMediaPackage);
        }
    }

    private void syncMediaVisualizerWanted(boolean z) {
        if (z) {
            ensureMediaVisualizer();
        } else if (this.mediaAudioViz != null) {
            this.mediaAudioViz.setWanted(false);
        }
    }

    private void syncOverlaySlots() {
        syncOverlaySlots(false);
    }

    private void syncOverlaySlots(boolean z) {
        int iOverlayTaskState;
        if (!isOverlayLaunching() && SystemClock.uptimeMillis() >= this.overlayRaiseGraceUntilMs) {
            if (this.activePopupPackage != null && !this.activePopupPackage.isEmpty() && ((iOverlayTaskState = overlayTaskState(this.activePopupPackage, this.activePopupTaskId)) == 0 || (z && iOverlayTaskState < 0))) {
                Log.w("H6Viewer", "Left freeform gone/minimized: " + this.activePopupPackage + " state=" + iOverlayTaskState);
                clearLeftSlot();
            }
            if (this.activeMediaPackage == null || this.activeMediaPackage.isEmpty()) {
                return;
            }
            int iOverlayTaskState2 = overlayTaskState(this.activeMediaPackage, this.activeMediaTaskId);
            if (iOverlayTaskState2 == 0 || (z && iOverlayTaskState2 < 0)) {
                Log.w("H6Viewer", "Right freeform gone/minimized: " + this.activeMediaPackage + " state=" + iOverlayTaskState2);
                clearRightSlot();
            }
        }
    }

    private Object systemActivityService() {
        if (Boolean.FALSE.equals(this.systemActivityServiceAvailable)) {
            return null;
        }
        allowHiddenApis();
        try {
            Object objInvoke = Build.VERSION.SDK_INT >= 29 ? Class.forName("android.app.ActivityTaskManager").getMethod("getService", new Class[0]).invoke(null, new Object[0]) : ActivityManager.class.getMethod("getService", new Class[0]).invoke(null, new Object[0]);
            if (objInvoke == null) {
                this.systemActivityServiceAvailable = false;
                return null;
            }
            this.systemActivityServiceAvailable = true;
            return objInvoke;
        } catch (Throwable th) {
            if (this.systemActivityServiceAvailable == null) {
                Log.w("H6Viewer", "System activity service unavailable (" + th.getClass().getSimpleName() + ")");
            }
            this.systemActivityServiceAvailable = false;
            return null;
        }
    }

    private Drawable systemDockIcon(int i) {
        return systemDockIcon(i, dockGlyphColor(true));
    }

    private Drawable systemDockIcon(int i, int i2) {
        Drawable drawable = getResources().getDrawable(i, getTheme());
        if (drawable == null) {
            return null;
        }
        Drawable drawableMutate = drawable.mutate();
        drawableMutate.setTint(i2);
        return drawableMutate;
    }

    private List<?> systemTaskList() {
        Object objSystemActivityService = systemActivityService();
        if (objSystemActivityService == null) {
            return null;
        }
        Method method = null;
        try {
            for (Method method2 : objSystemActivityService.getClass().getMethods()) {
                if ("getTasks".equals(method2.getName()) && method2.getParameterTypes().length >= 1) {
                    method = method2;
                    break;
                }
            }
            if (method == null) {
                return null;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            Object objInvoke = parameterTypes.length == 1 ? method.invoke(objSystemActivityService, 32) : (parameterTypes.length == 2 && parameterTypes[1] == Boolean.TYPE) ? method.invoke(objSystemActivityService, 32, false) : method.invoke(objSystemActivityService, 32, false, false);
            if (objInvoke instanceof List) {
                return (List) objInvoke;
            }
            return null;
        } catch (Throwable th) {
            if (!Boolean.FALSE.equals(this.systemTaskListAvailable)) {
                Log.w("H6Viewer", "System getTasks failed (" + th.getClass().getSimpleName() + ")");
            }
            this.systemTaskListAvailable = false;
            return null;
        }
    }

    private static int taskIdIfPackage(ActivityManager.RunningTaskInfo runningTaskInfo, String str) {
        if (runningTaskInfo == null) {
            return -1;
        }
        ComponentName componentName = runningTaskInfo.topActivity;
        ComponentName componentName2 = runningTaskInfo.baseActivity;
        if ((componentName == null || !str.equals(componentName.getPackageName())) && (componentName2 == null || !str.equals(componentName2.getPackageName()))) {
            return -1;
        }
        return runningTaskInfo.id;
    }

    private void tintFrostText(View view) {
        Object tag = view.getTag();
        if ("workspaceApps".equals(tag) || "workspaceLayout".equals(tag)) {
            applyWorkspaceActionGlyph(view);
        } else if ((tag instanceof String) && ((String) tag).startsWith("workspaceLayoutCell:")) {
            styleWorkspaceLayoutCells();
        } else if (view instanceof TextView) {
            ((TextView) view).setTextColor("frostAccent".equals(tag) || "frostActionAccent".equals(tag) ? this.dockAccentColor : "frostSecondary".equals(tag) ? dockLabelColorMuted() : dockLabelColor());
            if ("frostAction".equals(tag) || "frostActionAccent".equals(tag)) {
                view.setBackground(makeDockPlateDrawable(false, getResources().getDisplayMetrics().density));
            }
        } else if ("quickAccentRail".equals(tag)) {
            view.setBackgroundColor(withAlpha(this.dockAccentColor, 216));
        } else if ("workspaceSplit".equals(tag)) {
            view.setBackgroundColor(this.dockUiLight ? 813727872 : 855638015);
        }
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                tintFrostText(viewGroup.getChildAt(i));
            }
        }
    }

    private String[] tireReadings(BottomCardDescriptor bottomCardDescriptor) {
        String[] strArr = {"unavailable", "unavailable", "unavailable", "unavailable"};
        if (!bottomCardDescriptor.tirePressures.isEmpty()) {
            String[] strArrSplit = bottomCardDescriptor.tirePressures.split("\\s*/\\s*", -1);
            if (strArrSplit.length >= 4) {
                for (int i = 0; i < strArr.length; i++) {
                    String strTrim = strArrSplit[i].trim();
                    strArr[i] = strTrim.isEmpty() ? "unavailable" : strTrim;
                }
                return strArr;
            }
        }
        String[] strArrSplit2 = bottomCardDescriptor.primary.split("\\s*[·/]\\s*", -1);
        String[] strArrSplit3 = bottomCardDescriptor.metricA.split("\\s*[·/]\\s*", -1);
        if (strArrSplit2.length >= 2) {
            strArr[0] = strArrSplit2[0].isEmpty() ? "unavailable" : strArrSplit2[0];
            strArr[1] = strArrSplit2[1].isEmpty() ? "unavailable" : strArrSplit2[1];
        }
        if (strArrSplit3.length >= 2) {
            strArr[2] = strArrSplit3[0].isEmpty() ? "unavailable" : strArrSplit3[0];
            strArr[3] = strArrSplit3[1].isEmpty() ? "unavailable" : strArrSplit3[1];
        }
        if (strArrSplit2.length < 2 || strArrSplit3.length < 2) {
            String[] strArrSplit4 = bottomCardDescriptor.value.split("\\s*/\\s*", -1);
            if (strArrSplit4.length >= 4) {
                for (int i2 = 0; i2 < strArr.length; i2++) {
                    strArr[i2] = strArrSplit4[i2].isEmpty() ? "unavailable" : strArrSplit4[i2];
                }
            }
        }
        return strArr;
    }

    private static Rect toWindowRect(Rect rect, Rect rect2) {
        Rect rect3 = new Rect(rect);
        rect3.offset(-rect2.left, -rect2.top);
        return rect3;
    }

    private void toggleDockSurface() {
        this.dockSurfaceMode = "cards".equals(this.dockSurfaceMode) ? "launcher" : "cards";
        persistDockSurfaceMode();
        refreshDockSurfaceUi(true);
        callViewerDock("toggleDockMode");
    }

    private void toggleOpenAsWindow(String str) {
        if (canToggleOpenAsWindow(str)) {
            boolean zContains = this.windowPackages.contains(str);
            Set<String> set = this.windowPackages;
            if (zContains) {
                set.remove(str);
            } else {
                set.add(str);
            }
            saveWindowApps();
        }
    }

    private boolean tryAttachAppsFabInWindow() {
        if (this.rootLayout == null || this.appsFabRoot == null) {
            return false;
        }
        try {
            if (this.appsFabRoot.getParent() instanceof ViewGroup) {
                ((ViewGroup) this.appsFabRoot.getParent()).removeView(this.appsFabRoot);
            }
            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(-2, -2);
            layoutParams.gravity = 8388659;
            clampAppsFabPosition();
            layoutParams.leftMargin = this.appsFabPosX;
            layoutParams.topMargin = this.appsFabPosY;
            this.appsFabRoot.setElevation(getResources().getDisplayMetrics().density * 48.0f);
            this.rootLayout.addView(this.appsFabRoot, layoutParams);
            this.appsFabRoot.bringToFront();
            return true;
        } catch (Exception e) {
            Log.w("H6Viewer", "In-window FAB attach failed", e);
            return false;
        }
    }

    private boolean tryAttachAppsFabSystemOverlay() {
        try {
            if (!Settings.canDrawOverlays(this)) {
                Log.w("H6Viewer", "SYSTEM_ALERT_WINDOW not granted — will try in-window FAB");
                return false;
            }
            try {
                if (this.appsFabLp == null) {
                    this.appsFabLp = buildAppsFabWindowLp();
                }
                this.appsFabLp.x = this.appsFabPosX;
                this.appsFabLp.y = this.appsFabPosY;
                clampAppsFabPosition();
                this.appsFabLp.x = this.appsFabPosX;
                this.appsFabLp.y = this.appsFabPosY;
                getWindowManager().addView(this.appsFabRoot, this.appsFabLp);
                return true;
            } catch (Exception e) {
                Log.w("H6Viewer", "System overlay FAB attach failed", e);
                return false;
            }
        } catch (Exception e2) {
            return false;
        }
    }

    private void unregisterOverlayTaskListener() {
        if (this.taskStackListener == null) {
            return;
        }
        try {
            Class<?> cls = Class.forName("android.app.ITaskStackListener");
            try {
                ActivityManager activityManager = (ActivityManager) getSystemService("activity");
                activityManager.getClass().getMethod("unregisterTaskStackListener", cls).invoke(activityManager, this.taskStackListener);
            } catch (Exception e) {
                Object objSystemActivityService = systemActivityService();
                if (objSystemActivityService != null) {
                    objSystemActivityService.getClass().getMethod("unregisterTaskStackListener", cls).invoke(objSystemActivityService, this.taskStackListener);
                }
            }
        } catch (Throwable th) {
        }
        this.taskStackListener = null;
    }

    private void updateAppsOnlyChrome() {
        applyReserveBandGeometry();
        if (!"appsOnly".equals(this.shellMode)) {
            dismissAppsFabOverlay();
            updateLauncherStripVisibility();
        } else {
            if (this.stripContainer != null) {
                this.stripContainer.setVisibility(8);
            }
            showAppsFabOverlay();
        }
    }

    private void updateBottomCardAccessibility(View view, BottomCardDescriptor bottomCardDescriptor) {
        if (view == null) {
            return;
        }
        View view2 = view;
        while (view2 != null) {
            Object tag = view2.getTag();
            if ((tag instanceof String) && ((String) tag).startsWith("bottomCard:")) {
                view2.setContentDescription(bottomCardAccessibilityDescription(bottomCardDescriptor));
                return;
            } else {
                Object parent = view2.getParent();
                view2 = parent instanceof View ? (View) parent : null;
            }
        }
    }

    private void updateBottomCardValues(List<BottomCardDescriptor> list) {
        for (BottomCardDescriptor bottomCardDescriptor : list) {
            TextView textView = this.quickCardValues.get(bottomCardDescriptor.id);
            String strQuickVisualPrimary = quickVisualPrimary(bottomCardDescriptor);
            if (textView != null && !strQuickVisualPrimary.equals(textView.getText().toString())) {
                textView.setText(strQuickVisualPrimary);
            }
            TextView textView2 = this.quickCardDetails.get(bottomCardDescriptor.id);
            String strQuickVisualDetail = quickVisualDetail(bottomCardDescriptor);
            if (textView2 != null && !strQuickVisualDetail.equals(textView2.getText().toString())) {
                textView2.setText(strQuickVisualDetail);
            }
            TextView textView3 = this.quickCardDemoBadges.get(bottomCardDescriptor.id);
            if (textView3 != null) {
                textView3.setVisibility(bottomCardDescriptor.demo ? 0 : 8);
            }
            QuickCardGraphicView quickCardGraphicView = this.quickCardGraphics.get(bottomCardDescriptor.id);
            if (quickCardGraphicView != null) {
                quickCardGraphicView.setDescriptor(bottomCardDescriptor);
            }
            applyNavigationSourceChip(bottomCardDescriptor);
            QuickClockCardView quickClockCardView = this.quickClockCards.get(bottomCardDescriptor.id);
            if (quickClockCardView != null) {
                quickClockCardView.setDescriptor(bottomCardDescriptor);
            }
            View view = this.quickCardHosts.get(bottomCardDescriptor.id);
            int iDrivingWashColor = drivingWashColor(bottomCardDescriptor.state);
            if (view != null && iDrivingWashColor != this.lastDrivingWash.getOrDefault(bottomCardDescriptor.id, -1).intValue()) {
                this.lastDrivingWash.put(bottomCardDescriptor.id, Integer.valueOf(iDrivingWashColor));
                view.setBackground(makeFrostStateDrawable(false, getResources().getDisplayMetrics().density, iDrivingWashColor));
            }
            updateBottomCardAccessibility(textView != null ? textView : quickClockCardView != null ? quickClockCardView : quickCardGraphicView, bottomCardDescriptor);
        }
    }

    private void updateDesktopSwitcherHitProxy(float f, float f2, float f3, float f4, boolean z) {
        if (this.webView == null) {
            return;
        }
        int[] iArr = new int[2];
        this.webView.getLocationOnScreen(iArr);
        float scale = this.webView.getScale();
        if (scale <= 0.0f) {
            scale = 1.0f;
        }
        boolean z2 = false;
        this.desktopHitX = iArr[0] + Math.round(f * scale);
        this.desktopHitY = iArr[1] + Math.round(f2 * scale);
        this.desktopHitW = Math.max(0, Math.round(f3 * scale));
        this.desktopHitH = Math.max(0, Math.round(f4 * scale));
        if (z && this.desktopHitW > 1 && this.desktopHitH > 1) {
            z2 = true;
        }
        this.desktopHitWanted = z2;
        DesktopSwitcherHitService.applyFromViewer(this, this.desktopHitX, this.desktopHitY, this.desktopHitW, this.desktopHitH, this.desktopHitWanted);
    }

    private void updateLauncherStripVisibility() {
        if (this.stripContainer == null) {
            return;
        }
        this.stripContainer.setVisibility((!this.launcherRevealed || this.launcherHiddenByPage || "appsOnly".equals(this.shellMode)) ? 8 : 0);
        applyReserveBandGeometry();
    }

    private void updatePinnedVisibility() {
        boolean z = false;
        for (int i = 0; i < PINNED_PACKAGES.length; i++) {
            if (this.pinnedBound[i] && !isUserHidden(PINNED_PACKAGES[i])) {
                z = true;
                break;
            }
        }
        if (this.gwmHubItem != null) {
            MotionTrailLayout motionTrailLayout = this.gwmHubItem;
            if (!z) {
                motionTrailLayout.setVisibility(8);
                return;
            }
            motionTrailLayout.setVisibility(0);
            if (this.launcherRevealed) {
                this.gwmHubItem.setAlpha(1.0f);
                this.gwmHubItem.setTranslationX(0.0f);
                this.gwmHubItem.setTrailPx(0.0f);
            }
        }
    }

    private void updateQuickMediaCard(JSONObject lastMediaPayload) {
        if (lastMediaPayload == null) {
            return;
        }
        this.lastMediaPayload = lastMediaPayload;
        this.quickMediaPackage = lastMediaPayload.optString("packageName", "");
        this.quickMediaPlaying = lastMediaPayload.optBoolean("playing", false);
        this.quickMediaCanLaunch = lastMediaPayload.optBoolean("canLaunch", false);
        if (this.quickMediaBars != null) {
            this.quickMediaBars.setPlaying(this.quickMediaPlaying);
        }
        syncMediaVisualizerWanted(mediaAudioWanted());
        boolean zOptBoolean = lastMediaPayload.optBoolean("hasTrack", false);
        this.quickMediaAvailable = zOptBoolean && this.mediaNowPlaying != null;
        for (ImageView imageView : this.quickMediaButtons) {
            imageView.setEnabled(this.quickMediaAvailable);
            imageView.setAlpha(this.quickMediaAvailable ? 1.0f : 0.35f);
        }
        String strOptString = lastMediaPayload.optString("title", "");
        String strOptString2 = lastMediaPayload.optString("artist", "");
        String strOptString3 = lastMediaPayload.optString("appLabel", "");
        String str = (!zOptBoolean || strOptString.isEmpty()) ? !strOptString3.isEmpty() ? strOptString3 : "Nothing playing" : strOptString;
        if (this.quickMediaTitle != null) {
            this.quickMediaTitle.setText(str);
        }
        if (this.quickMediaArtist != null) {
            String strOptString4 = lastMediaPayload.optString("album", "");
            this.quickMediaArtist.setText(strOptString2.isEmpty() ? zOptBoolean ? strOptString4 : "Choose a media app" : strOptString2);
            this.quickMediaArtist.setVisibility(this.quickMediaArtist.getText().length() == 0 ? 4 : 0);
        }
        if (this.quickMediaArt != null) {
            Bitmap bitmapDecodeDataUrlBitmap = decodeDataUrlBitmap(lastMediaPayload.optString("artDataUrl", ""));
            this.quickMediaHasArt = bitmapDecodeDataUrlBitmap != null;
            if (this.quickMediaHasArt) {
                this.quickMediaArt.setPadding(0, 0, 0, 0);
                this.quickMediaArt.setImageBitmap(bitmapDecodeDataUrlBitmap);
            } else {
                float f = getResources().getDisplayMetrics().density;
                int iRound = Math.round(32.0f * f);
                this.quickMediaArt.setPadding(iRound, iRound, iRound, iRound);
                this.quickMediaArt.setImageDrawable(mediaArtPlaceholder(f));
            }
        }
        if (this.quickMediaPlayPause != null) {
            this.quickMediaPlayPause.setImageDrawable(mediaGlyph(getResources().getDisplayMetrics().density, true, this.quickMediaPlaying ? "pause" : "play"));
            this.quickMediaPlayPause.setContentDescription(this.quickMediaPlaying ? "Pause" : "Play");
        }
        applyQuickMediaAppChip(lastMediaPayload.optString("appIcon", ""), strOptString3);
    }

    private void updateSlotAnchors() {
        Rect rectPageOriginRect = pageOriginRect();
        applyAnchorRect(this.launchAnchor, toWindowRect(leftFreeformBounds(), rectPageOriginRect));
        applyAnchorRect(this.mediaLaunchAnchor, toWindowRect(rightFreeformBounds(), rectPageOriginRect));
    }

    private Rect usableDisplayRect() {
        Point point = new Point();
        try {
            getWindowManager().getDefaultDisplay().getRealSize(point);
        } catch (Exception e) {
        }
        if (point.x <= 0 || point.y <= 0) {
            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            point.set(displayMetrics.widthPixels, displayMetrics.heightPixels);
        }
        Rect rect = new Rect(0, 0, point.x, point.y);
        Rect rect2 = new Rect(rect);
        try {
            View decorView = getWindow() != null ? getWindow().getDecorView() : null;
            WindowInsets rootWindowInsets = decorView != null ? decorView.getRootWindowInsets() : null;
            if (rootWindowInsets != null) {
                this.widestNavInsetPx = Math.max(this.widestNavInsetPx, rootWindowInsets.getSystemWindowInsetLeft());
                this.widestTopInsetPx = Math.max(this.widestTopInsetPx, rootWindowInsets.getSystemWindowInsetTop());
                rect2.left += this.widestNavInsetPx;
                rect2.top += this.widestTopInsetPx;
                rect2.right -= rootWindowInsets.getSystemWindowInsetRight();
                rect2.bottom -= rootWindowInsets.getSystemWindowInsetBottom();
            }
        } catch (Exception e2) {
        }
        return (rect2.width() < 320 || rect2.height() < 240) ? rect : rect2;
    }

    private boolean usesDarkIconPlate(String str) {
        String strIcon;
        boolean zEquals = "__gwm_hub".equals(str);
        DockAppOverrides dockAppOverrides = this.dockAppOverrides;
        if (zEquals) {
            strIcon = dockAppOverrides != null ? this.dockAppOverrides.icon(str) : null;
            return strIcon == null || DockAppOverrides.isDarkPlateSlug(strIcon);
        }
        strIcon = dockAppOverrides != null ? this.dockAppOverrides.icon(str) : null;
        if (strIcon != null) {
            return DockAppOverrides.isDarkPlateSlug(strIcon);
        }
        return usesGwmEmblem(str) || "com.beantechs.energyassistant".equalsIgnoreCase(str);
    }

    private boolean usesGwmEmblem(String str) {
        return isGwmApp(str) && !"com.beantechs.energyassistant".equalsIgnoreCase(str);
    }

    private static String viewerUrl() {
        return "https://appassets.androidplatform.net/assets/www/index.html?android&assets=vehicle-console-v47-aa-tbt-gmaps" + (isProbablyEmulator() ? "&demo=1" : "&demo=0");
    }

    private int washForCardView(View view) {
        Integer num;
        Object tag = view.getTag();
        if (!(tag instanceof String)) {
            return 0;
        }
        String str = (String) tag;
        if (str.startsWith("bottomCard:") && (num = this.lastDrivingWash.get(str.substring("bottomCard:".length()))) != null) {
            return num.intValue();
        }
        return 0;
    }

    private static int withAlpha(int i, int i2) {
        return (Math.max(0, Math.min(255, i2)) << 24) | (16777215 & i);
    }

    private Drawable workspaceBackGlyph(float f, int i) {
        int iMax = Math.max(8, Math.round(22.0f * f));
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(iMax, iMax, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        Paint paint = new Paint(1);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth((iMax * 2.2f) / 24.0f);
        paint.setColor(i);
        float f2 = iMax / 24.0f;
        Path path = new Path();
        path.moveTo(19.0f * f2, f2 * 12.0f);
        path.lineTo(f2 * 5.0f, f2 * 12.0f);
        path.moveTo(f2 * 11.0f, 6.0f * f2);
        path.lineTo(5.0f * f2, 12.0f * f2);
        path.lineTo(11.0f * f2, 18.0f * f2);
        canvas.drawPath(path, paint);
        return new BitmapDrawable(getResources(), bitmapCreateBitmap);
    }

    private FrameLayout wrapLauncherIconPlate(ImageView imageView, int i, float f) {
        return wrapLauncherIconPlate(imageView, i, f, false);
    }

    private FrameLayout wrapLauncherIconPlate(ImageView imageView, int i, float f, boolean z) {
        int iDockPlatePx = dockPlatePx(i, f);
        FrameLayout frameLayout = new FrameLayout(this);
        frameLayout.setClipChildren(false);
        frameLayout.setClipToPadding(false);
        FrameLayout frameLayout2 = new FrameLayout(this);
        frameLayout2.setTag("iconPlate");
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(iDockPlatePx, iDockPlatePx);
        layoutParams.gravity = 49;
        frameLayout2.setLayoutParams(layoutParams);
        frameLayout2.setBackground(makeLauncherIconPlateDrawable(f, z));
        frameLayout2.setElevation(7.0f * f);
        frameLayout2.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        frameLayout2.setClipToOutline(true);
        int iRound = Math.round(iDockPlatePx * launcherIconInsetFrac());
        frameLayout2.setPadding(iRound, iRound, iRound, iRound);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        frameLayout2.addView(imageView, new FrameLayout.LayoutParams(-1, -1));
        frameLayout.addView(frameLayout2);
        return frameLayout;
    }

    boolean dispatchDesktopSwitcherHit(MotionEvent motionEvent) {
        if (this.webView == null) {
            return false;
        }
        int[] iArr = new int[2];
        this.webView.getLocationOnScreen(iArr);
        MotionEvent motionEventObtain = MotionEvent.obtain(motionEvent);
        motionEventObtain.offsetLocation((motionEvent.getRawX() - iArr[0]) - motionEvent.getX(), (motionEvent.getRawY() - iArr[1]) - motionEvent.getY());
        try {
            return this.webView.dispatchTouchEvent(motionEventObtain);
        } finally {
            motionEventObtain.recycle();
        }
    }

    @Override // android.app.Activity, android.view.Window.Callback
    public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        if (motionEvent != null && hasOverlayWindow() && motionEvent.getActionMasked() == 1) {
            raiseOverlayTasksSoon(motionEvent.getY() <= ((float) chromeReservePx()) ? 700L : 0L);
        }
        return super.dispatchTouchEvent(motionEvent);
    }

    @Override // android.app.Activity
    protected void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
        if (i != 1001 || this.pendingFiles == null) {
            return;
        }
        this.pendingFiles.onReceiveValue(i2 == -1 ? WebChromeClient.FileChooserParams.parseResult(i2, intent) : null);
        this.pendingFiles = null;
    }

    @Override // android.app.Activity
    public void onBackPressed() {
        if (this.webView.canGoBack()) {
            this.webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Toda abertura de app a partir daqui passa por este ponto. Abrir a tela de fábrica pelo
     * launcher 3D é escolha do motorista (rádio, mídia), então a guarda que mantém o launcher 3D
     * como tela inicial deixa ela ficar (Alpha 7.8, GuardaDoLauncher3D).
     */
    @Override
    public void startActivity(Intent intent, Bundle options) {
        br.com.opengw.managers.GuardaDoLauncher3D.aoAbrirPorEscolha(intent);
        super.startActivity(intent, options);
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().addFlags(128);
        this.uiMode = readUiModePref();
        enterImmersiveMode();
        this.webView = new WebView(this);
        // A chave é do PROCESSO inteiro, não deste WebView: ligada aqui, ela abria no build de
        // produção a depuração remota de todos os WebViews do app (painel, HUD), que o App.onCreate
        // só liga em debug. Quem tem adb injetaria JS com acesso às pontes do carro.
        WebView.setWebContentsDebuggingEnabled(br.com.opengw.BuildConfig.DEBUG);
        int i = getSharedPreferences("opengw_lancador", 0).getInt("shellBg", -16777216);
        this.webView.setBackgroundColor(0);
        this.webView.setLayerType(0, null);
        this.webView.setOverScrollMode(2);
        this.webView.setVerticalScrollBarEnabled(false);
        this.webView.setHorizontalScrollBarEnabled(false);
        WebSettings settings = this.webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setBlockNetworkImage(false);
        settings.setBlockNetworkLoads(false);
        settings.setMixedContentMode(0);
        settings.setCacheMode(-1);
        this.webView.addJavascriptInterface(new TelemetryBridge(), "TelemetryBridge");
        this.webView.addJavascriptInterface(new AppLauncherBridge(), "AppLauncherBridge");
        this.webView.addJavascriptInterface(new MediaBridge(), "MediaBridge");
        this.tripRecorder = TripRecorder.get(this);
        this.webView.addJavascriptInterface(new TripBridge(this.tripRecorder), "TripBridge");
        this.placeGlance = new PlaceGlance(this, this.mainHandler, str -> {
            this.telemetryCache.put("app.location.place", str);
            if (this.webView == null) {
                return;
            }
            this.webView.evaluateJavascript(String.format("if(window.onCarDataUpdate){window.onCarDataUpdate('%s','%s');}", "app.location.place".replace("'", "\\'"), str.replace("'", "\\'")), null);
        });
        this.placeGlance.start();
        this.webView.setWebViewClient(new WebViewClient() {
            @Override // android.webkit.WebViewClient
            public void onPageFinished(WebView webView, String str) {
                Log.i("H6Viewer", "Page finished: " + str);
                // Unica saida se a pagina pedir a faixa escondida e depois recarregar: o pedido morre
                // com a pagina que o fez. Quem ainda precisar dela escondida torna a pedir.
                MainActivity.this.applyLauncherHidden(false);
                MainActivity.this.notifyViewerShellLayout();
                MainActivity.this.mainHandler.postDelayed(() -> {
                    try {
                        MainActivity.this.mediaNowPlaying.start();
                        MainActivity.this.mediaNowPlaying.pushNow();
                    } catch (Throwable th) {
                        Log.w("H6Viewer", "mediaNowPlaying fallback start failed", th);
                    }
                }, 20000L);
                webView.evaluateJavascript("JSON.stringify({ready:document.readyState,title:document.title,body:document.body&&document.body.innerText.slice(0,160),scripts:document.scripts.length,three:typeof THREE})", obj -> Log.i("H6Viewer", "Page state: " + ((String) obj)));
            }

            /**
             * O processo de render do WebView morreu (falta de memória com o modelo 3D carregado,
             * ou falha do driver de GPU). Sem este método o Android MATA O APP INTEIRO — e o
             * launcher mora no mesmo processo do Shizuku, do painel e da barra, que cairiam juntos
             * por causa da tela inicial. Devolvendo true o app sobrevive; este WebView é que não
             * serve mais (a documentação proíbe reusá-lo), então a Activity é recriada com um novo.
             */
            @Override // android.webkit.WebViewClient
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                MainActivity.this.aoPerderORenderer(view, detail != null && detail.didCrash());
                return true;
            }

            @Override // android.webkit.WebViewClient
            public WebResourceResponse shouldInterceptRequest(WebView webView, WebResourceRequest webResourceRequest) {
                InputStream byteArrayInputStream;
                Uri url = webResourceRequest.getUrl();
                if (!"appassets.androidplatform.net".equals(url.getHost()) || !url.getPath().startsWith("/assets/")) {
                    return super.shouldInterceptRequest(webView, webResourceRequest);
                }
                String strSubstring = url.getPath().substring("/assets/".length());
                try {
                    InputStream inputStreamOpen = MainActivity.this.getAssets().open(strSubstring);
                    String strMimeType = MainActivity.mimeType(strSubstring);
                    String str = MainActivity.needsUtf8(strSubstring) ? "UTF-8" : null;
                    HashMap map = new HashMap();
                    map.put("Access-Control-Allow-Origin", "*");
                    if ("video/mp4".equals(strMimeType)) {
                        byte[] allBytes = MainActivity.readAllBytes(inputStreamOpen);
                        inputStreamOpen.close();
                        map.put("Content-Length", Integer.toString(allBytes.length));
                        map.put("Accept-Ranges", "none");
                        byteArrayInputStream = new ByteArrayInputStream(allBytes);
                    } else {
                        byteArrayInputStream = inputStreamOpen;
                    }
                    return new WebResourceResponse(strMimeType, str, 200, "OK", map, byteArrayInputStream);
                } catch (IOException e) {
                    Log.e("H6Viewer", "Missing packaged asset: " + strSubstring, e);
                    return null;
                }
            }
        });
        this.webView.setWebChromeClient(new WebChromeClient() {
            @Override // android.webkit.WebChromeClient
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.e("H6Viewer", "JS " + consoleMessage.messageLevel() + " " + consoleMessage.sourceId() + ":" + consoleMessage.lineNumber() + " " + consoleMessage.message());
                return true;
            }

            @Override // android.webkit.WebChromeClient
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> valueCallback, WebChromeClient.FileChooserParams fileChooserParams) {
                if (MainActivity.this.pendingFiles != null) {
                    MainActivity.this.pendingFiles.onReceiveValue(null);
                }
                MainActivity.this.pendingFiles = valueCallback;
                try {
                    MainActivity.this.startActivityForResult(fileChooserParams.createIntent(), 1001);
                    return true;
                } catch (RuntimeException e) {
                    MainActivity.this.pendingFiles = null;
                    return false;
                }
            }
        });
        this.rootLayout = new FrameLayout(this);
        this.rootLayout.setBackgroundColor(i);
        getWindow().setBackgroundDrawable(new ColorDrawable(i));
        this.rootLayout.setLayoutParams(new WindowManager.LayoutParams(-1, -1));
        this.rootLayout.addView(this.webView, new FrameLayout.LayoutParams(-1, -1));
        setupBootHud(this.rootLayout);
        setupSplashSkip(this.rootLayout);
        setupSplashFade(this.rootLayout);
        setupNativeLauncherUI(this.rootLayout);
        this.mediaNowPlaying.attach(this, new MediaNowPlaying.Callback() {
            @Override // com.havalh6.viewer.MediaNowPlaying.Callback
            public void onPosition(long j) {
                MainActivity.this.notifyMediaPosition(j);
            }

            @Override // com.havalh6.viewer.MediaNowPlaying.Callback
            public void onUpdate(JSONObject jSONObject) {
                MainActivity.this.notifyMediaNowPlaying(jSONObject);
                MainActivity.this.mainHandler.post(() -> {
                    MainActivity.this.updateQuickMediaCard(jSONObject);
                });
            }
        });
        try {
            IntentFilter intentFilter = new IntentFilter("com.haval.vehicle.EVENT_CHANGED");
            intentFilter.addAction("br.com.opengw.CAR_DATA_UPDATE");
            registerReceiver(this.telemetryReceiver, intentFilter);
        } catch (Exception e) {
            Log.w("H6Viewer", "Car telemetry receiver not registered", e);
        }
        try {
            IntentFilter intentFilter2 = new IntentFilter();
            intentFilter2.addAction("android.intent.action.TIME_TICK");
            intentFilter2.addAction("android.intent.action.TIME_SET");
            intentFilter2.addAction("android.intent.action.TIMEZONE_CHANGED");
            intentFilter2.addAction("android.intent.action.LOCALE_CHANGED");
            registerReceiver(this.clockEnvironmentReceiver, intentFilter2);
        } catch (Exception e2) {
            Log.w("H6Viewer", "Clock environment receiver not registered", e2);
        }
        scheduleTelemetrySnapshotRequests();
        try {
            registerReceiver(this.taskResolvedReceiver, new IntentFilter("br.com.opengw.ACTION_TASK_RESOLVED"));
        } catch (Exception e3) {
            Log.w("H6Viewer", "Task id receiver not registered", e3);
        }
        try {
            IntentFilter intentFilter3 = new IntentFilter("android.intent.action.PACKAGE_REMOVED");
            intentFilter3.addAction("android.intent.action.PACKAGE_FULLY_REMOVED");
            intentFilter3.addDataScheme("package");
            registerReceiver(this.packageRemovedReceiver, intentFilter3);
        } catch (Exception e4) {
            Log.w("H6Viewer", "Package removed receiver not registered", e4);
        }
        setContentView(this.rootLayout);
        navRailReservePx();
        this.rootLayout.addOnLayoutChangeListener((view, i9, i2, i3, i4, i5, i6, i7, i8) -> {
            if (i9 == i5 && i2 == i6 && i3 == i7 && i4 == i8) {
                return;
            }
            this.mainHandler.post(() -> this.notifyViewerShellLayout());
        });
        // Recriada depois de o renderizador cair: não há estado de WebView para restaurar (o morto não
        // salva nada), então carrega do zero — sem a vinheta, que já passou uma vez nesta partida.
        boolean aposQueda = bundle != null && bundle.getBoolean(ESTADO_RENDERER_PERDIDO, false);
        if (bundle == null || aposQueda) {
            String strViewerUrl = viewerUrl();
            if (aposQueda || (getIntent() != null && getIntent().getBooleanExtra("nosplash", false))) {
                strViewerUrl = strViewerUrl + "&nosplash";
            }
            this.webView.loadUrl(strViewerUrl);
        } else {
            this.webView.restoreState(bundle);
        }
        ensureFreeformSettings();
        loadShellPrefs();
        updateAppsOnlyChrome();
        updateSlotAnchors();
        registerOverlayTaskListener();
        maybeLaunchFreeformFromIntent(getIntent());
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        stopOverlayWatchdog();
        unregisterOverlayTaskListener();
        if (this.mediaAudioViz != null) {
            this.mediaAudioViz.release();
            this.mediaAudioViz = null;
        }
        this.mainHandler.removeCallbacks(this.mediaVizPoll);
        this.mediaNowPlaying.stop();
        dismissDockEditMenu();
        if (this.projectionPresence != null) {
            this.projectionPresence.stop();
        }
        if (this.placeGlance != null) {
            this.placeGlance.stop();
        }
        if (this.tripRecorder != null) {
            this.tripRecorder.persistSoon();
        }
        if (this.pinBoundsRunnable != null) {
            this.mainHandler.removeCallbacks(this.pinBoundsRunnable);
        }
        if (this.pinMediaBoundsRunnable != null) {
            this.mainHandler.removeCallbacks(this.pinMediaBoundsRunnable);
        }
        if (this.raiseOverlayRunnable != null) {
            this.mainHandler.removeCallbacks(this.raiseOverlayRunnable);
        }
        dismissAppsFabOverlay();
        DesktopSwitcherHitService.applyFromViewer(this, 0, 0, 0, 0, false);
        try {
            unregisterReceiver(this.telemetryReceiver);
        } catch (Exception e) {
        }
        try {
            unregisterReceiver(this.clockEnvironmentReceiver);
        } catch (Exception e2) {
        }
        try {
            unregisterReceiver(this.taskResolvedReceiver);
        } catch (Exception e3) {
        }
        try {
            unregisterReceiver(this.packageRemovedReceiver);
        } catch (Exception e4) {
        }
        if (this.pendingFiles != null) {
            this.pendingFiles.onReceiveValue(null);
        }
        this.webView.stopLoading();
        this.webView.setWebChromeClient(null);
        this.webView.setWebViewClient(null);
        this.webView.destroy();
        super.onDestroy();
    }

    @Override // android.app.Activity
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        maybeLaunchFreeformFromIntent(intent);
    }

    @Override // android.app.Activity
    protected void onPause() {
        dismissDockEditMenu();
        if (!hasOverlayWindow()) {
            this.webView.onPause();
        }
        super.onPause();
    }

    @Override // android.app.Activity
    public void onRequestPermissionsResult(int i, String[] strArr, int[] iArr) {
        super.onRequestPermissionsResult(i, strArr, iArr);
        if (i == 7102) {
            boolean z = false;
            for (int i2 : iArr) {
                if (i2 == 0) {
                    z = true;
                }
            }
            Log.w("H6Viewer", "PlaceGlance location permission " + (z ? "granted" : "denied"));
            if (!z || this.placeGlance == null) {
                return;
            }
            this.placeGlance.pokeNow();
        }
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        this.webView.onResume();
        enterImmersiveMode();
        this.mediaNowPlaying.start();
        this.mainHandler.removeCallbacks(this.mediaVizPoll);
        this.mainHandler.post(this.mediaVizPoll);
        notifyViewerShellLayout();
        if (this.desktopHitWanted) {
            DesktopSwitcherHitService.applyFromViewer(this, this.desktopHitX, this.desktopHitY, this.desktopHitW, this.desktopHitH, true);
        }
        keepOverlayTasksOnTop();
        syncOverlaySlots(false);
        this.mainHandler.postDelayed(() -> {
            syncOverlaySlots(true);
        }, 500L);
    }

    @Override // android.app.Activity
    protected void onSaveInstanceState(Bundle bundle) {
        if (this.rendererPerdido) {
            bundle.putBoolean(ESTADO_RENDERER_PERDIDO, true);
        } else {
            this.webView.saveState(bundle);
        }
        super.onSaveInstanceState(bundle);
    }

    /**
     * Ver onRenderProcessGone. Tira o WebView morto da tela e recria a Activity, que monta um WebView
     * novo em onCreate. Três quedas em cinco minutos = algo que volta a derrubar logo ao carregar
     * (o modelo 3D estourando a memória, por exemplo): espera 30 s antes de tentar de novo, em vez
     * de recriar em laço disputando memória com o resto do carro.
     */
    void aoPerderORenderer(WebView morto, boolean falhou) {
        long agora = SystemClock.elapsedRealtime();
        QUEDAS_DO_RENDERER_MS[proximaQuedaDoRenderer] = agora;
        proximaQuedaDoRenderer = (proximaQuedaDoRenderer + 1) % QUEDAS_DO_RENDERER_MS.length;
        int recentes = 0;
        for (long t : QUEDAS_DO_RENDERER_MS) {
            if (t != 0 && agora - t < JANELA_DAS_QUEDAS_MS) {
                recentes++;
            }
        }
        long espera = recentes >= QUEDAS_DO_RENDERER_MS.length ? ESPERA_APOS_QUEDAS_SEGUIDAS_MS : 0L;
        Log.e("H6Viewer", "Renderizador do WebView perdido (falhou=" + falhou + ", quedas em 5 min=" + recentes
                + "); recriando a tela inicial em " + espera + " ms");
        if (morto != this.webView || this.rendererPerdido) {
            return;
        }
        this.rendererPerdido = true;
        ViewGroup pai = (ViewGroup) morto.getParent();
        if (pai != null) {
            pai.removeView(morto);
        }
        // destroy() num WebView destruído é inofensivo; o onDestroy chama de novo sem problema, e as
        // chamadas que ainda chegarem até a recriação (evaluateJavascript etc.) viram nada.
        morto.destroy();
        this.mainHandler.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                recreate();
            }
        }, espera);
    }

    /**
     * O sistema avisa que a memória está apertando (ou que a tela inicial saiu da frente). O lado web
     * solta o que recria sozinho quando precisar (openGwClient.js, __openGwAliviarMemoria).
     */
    @Override // android.app.Activity
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (this.webView == null || this.rendererPerdido || level < TRIM_MEMORY_RUNNING_LOW) {
            return;
        }
        this.webView.evaluateJavascript("try{if(window.__openGwAliviarMemoria)window.__openGwAliviarMemoria(" + level + ");}catch(e){}", null);
    }

    @Override // android.app.Activity
    protected void onStop() {
        super.onStop();
        if (isChangingConfigurations() || isOverlayLaunching()) {
            return;
        }
        View decorView = getWindow() != null ? getWindow().getDecorView() : null;
        if (decorView == null || decorView.getWindowVisibility() != 0) {
            DesktopSwitcherHitService.applyFromViewer(this, 0, 0, 0, 0, false);
            dismissAllOverlays();
        }
    }

    @Override // android.app.Activity, android.view.Window.Callback
    public void onWindowFocusChanged(boolean z) {
        super.onWindowFocusChanged(z);
        if (z) {
            enterImmersiveMode();
            if (hasOverlayWindow()) {
                applyLauncherFocusPolicy();
                raiseOverlayTasksSoon();
            }
            syncOverlaySlots(false);
        }
    }
}
