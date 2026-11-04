package com.motionboxing.game;

import android.Manifest;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {

    private static final String APP_HOST = "appassets.androidplatform.net";
    private static final String APP_PAGE = "https://" + APP_HOST + "/assets/index.html";
    private static final String ASSET_PATH_PREFIX = "/assets/";

    // Production Rewarded Ad Unit ID for Real Punch: Motion Boxing.
    private static final String TEST_REWARDED_UNIT = "ca-app-pub-1884949236258434/5835158797";

    private static final int CAMERA_CODE = 1001;

    private WebView webView;
    private OnBackInvokedCallback modernBackCallback;

    // Ads
    private RewardedAd rewardedAd;
    private boolean adShowing = false;
    private boolean rewardEarned = false;
    private ConsentInformation consentInfo;
    private final AtomicBoolean adsInitialized = new AtomicBoolean(false);

    // Camera / orientation
    private PermissionRequest cameraRequest;
    private String currentOrientation = "unspecified";

    // ---------------------------------------------------------------------
    // Ads + consent
    // ---------------------------------------------------------------------

    /** Ask for consent first (UMP). Ads are initialised only when allowed. */
    private void setupConsentAndAds() {
        consentInfo = UserMessagingPlatform.getConsentInformation(this);
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();

        consentInfo.requestConsentInfoUpdate(
            this,
            params,
            () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(this, formError -> {
                if (consentInfo.canRequestAds()) initAds();
            }),
            requestError -> {
                if (consentInfo.canRequestAds()) initAds();
            });

        // User may already have given consent in an earlier session.
        if (consentInfo.canRequestAds()) initAds();
    }

    private void initAds() {
        if (!adsInitialized.compareAndSet(false, true)) return;
        MobileAds.initialize(this, status -> loadRewarded());
    }

    private void notifyAd(String status) {
        if (webView != null) {
            webView.evaluateJavascript(
                "window.rpRewardedAdResult && window.rpRewardedAdResult('" + status + "')", null);
        }
    }

    private void loadRewarded() {
        RewardedAd.load(this, TEST_REWARDED_UNIT, new AdRequest.Builder().build(),
            new RewardedAdLoadCallback() {
                @Override public void onAdLoaded(RewardedAd ad) { rewardedAd = ad; }
                @Override public void onAdFailedToLoad(LoadAdError error) { rewardedAd = null; }
            });
    }

    private void showRewarded() {
        if (adShowing) { notifyAd("unavailable"); return; }
        if (rewardedAd == null) {
            if (adsInitialized.get()) loadRewarded();
            notifyAd("unavailable");
            return;
        }
        RewardedAd ad = rewardedAd;
        rewardedAd = null;
        adShowing = true;
        rewardEarned = false;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() {
                adShowing = false;
                notifyAd(rewardEarned ? "earned" : "dismissed");
                loadRewarded();
            }
            @Override public void onAdFailedToShowFullScreenContent(AdError error) {
                adShowing = false;
                notifyAd("unavailable");
                loadRewarded();
            }
        });
        ad.show(this, reward -> rewardEarned = true);
    }

    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyImmersiveMode();

        webView = new WebView(this);
        setContentView(webView);

        // Android 13+ back gestures, including Android 16 target SDK 36.
        if (Build.VERSION.SDK_INT >= 33) {
            modernBackCallback = this::handleGameBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, modernBackCallback);
        }

        setupConsentAndAds();

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);

        // Orientation bridge (only used by the bundled game page).
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface public void closeApp() {
                runOnUiThread(() -> {
                    if (webView != null && APP_PAGE.equals(webView.getUrl())) {
                        finishAndRemoveTask();
                    }
                });
            }
            @JavascriptInterface public void setOrientation(String orientation) {
                runOnUiThread(() -> {
                    currentOrientation = orientation;
                    applyOrientation();
                });
            }
        }, "Android");

        // Rewarded-ad bridge is restricted to the app's bundled trusted page.
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface public void showRewardedAd() {
                runOnUiThread(() -> {
                    String current = webView == null ? "" : webView.getUrl();
                    if (!APP_PAGE.equals(current)) return;
                    showRewarded();
                });
            }
        }, "RewardedAds");

        webView.setWebViewClient(new WebViewClient() {

            // Never let the game WebView navigate to outside sites.
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !APP_HOST.equals(request.getUrl().getHost());
            }

            // Serve bundled files from /assets/ as https://appassets.androidplatform.net/assets/...
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String urlPath = uri.getPath(); // already URL-decoded ("Dancing%20Basemesh.fbx" -> "Dancing Basemesh.fbx")
                if (!APP_HOST.equals(uri.getHost()) || urlPath == null || !urlPath.startsWith(ASSET_PATH_PREFIX)) {
                    return super.shouldInterceptRequest(view, request);
                }
                String path = urlPath.substring(ASSET_PATH_PREFIX.length());
                if (path.isEmpty() || path.contains("..")) return null;
                try {
                    InputStream data = getAssets().open(path);
                    String type = mimeTypeFor(path);
                    String encoding = type.startsWith("text/") || type.contains("javascript") || type.contains("json")
                        ? "UTF-8" : null;
                    return new WebResourceResponse(type, encoding, data);
                } catch (Exception e) {
                    return null;
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    // Only our own bundled page may use the camera.
                    Uri origin = request.getOrigin();
                    boolean trusted = origin != null
                        && "https".equals(origin.getScheme())
                        && APP_HOST.equals(origin.getHost());
                    if (!trusted) { request.deny(); return; }

                    boolean asksVideo = false;
                    for (String r : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) asksVideo = true;
                    }
                    if (!asksVideo) { request.deny(); return; }

                    if (Build.VERSION.SDK_INT >= 23
                            && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        cameraRequest = request;
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_CODE);
                    } else {
                        request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    }
                });
            }
        });

        webView.loadUrl(APP_PAGE);
    }

    @Override protected void onResume() {
        super.onResume();
        applyOrientation();
        if (webView != null) webView.onResume();
    }

    /** Called for phone calls, notification shade, app switching, etc. -> pause the fight. */
    @Override protected void onPause() {
        if (webView != null) {
            webView.evaluateJavascript(
                "typeof rpPause==='function'&&rpPause()", null);
            webView.onPause();
        }
        super.onPause();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == CAMERA_CODE && cameraRequest != null) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                cameraRequest.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            } else {
                cameraRequest.deny();
            }
            cameraRequest = null;
        }
    }

    /**
     * Back button: step back inside the game screens; leave the app only from the main menu.
     */
    @Override @SuppressWarnings("deprecation") public void onBackPressed() {
        // Legacy button/back handling on Android 12 and earlier.
        if (Build.VERSION.SDK_INT < 33) handleGameBack();
    }

    private void handleGameBack() {
        if (webView == null) { finish(); return; }
        String js = "(function(){"
            + "function v(id){var e=document.getElementById(id);return !!e&&getComputedStyle(e).display!=='none';}"
            + "if(v('game')){exit();return 'handled';}"
            + "if(v('rpScoringPage')){document.getElementById('rpScoringBack').click();return 'handled';}"
            + "if(v('rpLevelSelect')){document.getElementById('rpLevelBack').click();return 'handled';}"
            + "if(v('rpFightModes')){document.getElementById('rpFightBack').click();return 'handled';}"
            + "return 'menu';})()";
        webView.evaluateJavascript(js, value -> {
            if (value == null || !value.contains("handled")) finish();
        });
    }

    @Override protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && modernBackCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(modernBackCallback);
            modernBackCallback = null;
        }
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private void applyImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512);
    }

    private void applyOrientation() {
        if ("portrait".equals(currentOrientation)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else if ("landscape".equals(currentOrientation)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    private static String mimeTypeFor(String path) {
        String p = path.toLowerCase();
        if (p.endsWith(".html") || p.endsWith(".htm")) return "text/html";
        if (p.endsWith(".js") || p.endsWith(".mjs")) return "application/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".json")) return "application/json";
        if (p.endsWith(".wasm")) return "application/wasm";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".mp3")) return "audio/mpeg";
        if (p.endsWith(".ogg")) return "audio/ogg";
        if (p.endsWith(".wav")) return "audio/wav";
        if (p.endsWith(".fbx") || p.endsWith(".glb") || p.endsWith(".bin")) return "application/octet-stream";
        return "text/plain";
    }
}














