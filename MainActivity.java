package com.motionboxing.game;

import android.Manifest;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
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
import java.io.InputStream;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

public class MainActivity extends Activity {
    private WebView webView;
    private RewardedAd rewardedAd;
    private boolean adShowing = false;
    private boolean rewardEarned = false;
    // Official Google rewarded test unit. Replace ONLY when ready for production.
    private static final String TEST_REWARDED_UNIT = "ca-app-pub-3940256099942544/5224354917";
    private void notifyAd(String status) {
        if (webView != null) webView.evaluateJavascript(
            "window.rpRewardedAdResult && window.rpRewardedAdResult('" + status + "')", null);
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
        if (rewardedAd == null) { loadRewarded(); notifyAd("unavailable"); return; }
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
    private PermissionRequest cameraRequest;
    private String currentOrientation = "unspecified";
    private static final int CAMERA_CODE = 1001;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512);
        webView = new WebView(this);
        setContentView(webView);
        MobileAds.initialize(this, initializationStatus -> loadRewarded());
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface public void setOrientation(String orientation) {
                runOnUiThread(() -> {
                    currentOrientation = orientation;
                    applyOrientation();
                });
            }
        }, "Android");
        // Bridge is restricted to the app's bundled trusted page, not arbitrary web content.
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface public void showRewardedAd() {
                runOnUiThread(() -> {
                    String current = webView == null ? "" : webView.getUrl();
                    if (!"https://appassets.androidplatform.net/assets/index.html".equals(current)) return;
                    showRewarded();
                });
            }
        }, "RewardedAds");
        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                String prefix = "https://appassets.androidplatform.net/assets/";
                if (!url.startsWith(prefix)) return super.shouldInterceptRequest(view, request);
                String path = url.substring(prefix.length());
                if (path.contains("..")) return null;
                try {
                    InputStream data = getAssets().open(path);
                    String type = path.endsWith(".html") ? "text/html" : path.endsWith(".js") ? "application/javascript" : "text/plain";
                    return new WebResourceResponse(type, "UTF-8", data);
                } catch (Exception e) { return null; }
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean asksVideo = false;
                    for (String r : request.getResources()) if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) asksVideo = true;
                    if (!asksVideo) { request.deny(); return; }
                    if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        cameraRequest = request;
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_CODE);
                    } else request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                });
            }
        });
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html");
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
    @Override protected void onResume() {
        super.onResume();
        applyOrientation();
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == CAMERA_CODE && cameraRequest != null) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
                cameraRequest.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            else cameraRequest.deny();
            cameraRequest = null;
        }
    }
    @Override public void onBackPressed() {
        if (webView != null) webView.evaluateJavascript("exit()", null); else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if (webView != null) { webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
