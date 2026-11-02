package com.motionboxing.game;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.JavascriptInterface;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.net.Uri;

import java.io.InputStream;

public class MainActivity extends Activity {

    private WebView web;
    private PermissionRequest pendingRequest;

    private static final int CAMERA_CODE = 101;
    private static final String GAME_URL = "https://appassets.androidplatform.net/assets/index.html";
    // Google's official rewarded TEST ad unit. Replace with your own before release.
    private static final String TEST_AD_UNIT = "ca-app-pub-3940256099942544/5224354917";
    private RewardedAd rewardedAd;
    private boolean adShowing = false;
    private boolean rewardEarned = false;

    private void sendAdResult(String result) {
        if (web != null) {
            web.evaluateJavascript("window.rpRewardedAdResult && window.rpRewardedAdResult('" + result + "')", null);
        }
    }

    private void loadRewardedAd() {
        RewardedAd.load(this, TEST_AD_UNIT, new AdRequest.Builder().build(),
                new RewardedAdLoadCallback() {
                    @Override public void onAdLoaded(RewardedAd ad) {
                        rewardedAd = ad;
                    }
                    @Override public void onAdFailedToLoad(LoadAdError error) {
                        rewardedAd = null;
                    }
                });
    }

    private void showRewardedAd() {
        if (adShowing) {
            sendAdResult("unavailable");
            return;
        }
        if (rewardedAd == null) {
            loadRewardedAd();
            sendAdResult("unavailable");
            return;
        }
        RewardedAd ad = rewardedAd;
        rewardedAd = null;
        adShowing = true;
        rewardEarned = false;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() {
                adShowing = false;
                sendAdResult(rewardEarned ? "earned" : "dismissed");
                loadRewardedAd();
            }
            @Override public void onAdFailedToShowFullScreenContent(AdError error) {
                adShowing = false;
                sendAdResult("unavailable");
                loadRewardedAd();
            }
        });
        ad.show(this, reward -> rewardEarned = true);
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        setContentView(web);
        MobileAds.initialize(this, status -> runOnUiThread(this::loadRewardedAd));

        WebSettings settings = web.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // Expose only to our trusted bundled game page; never load untrusted pages here.
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface public void showRewardedAd() {
                runOnUiThread(() -> {
                    if (web != null && GAME_URL.equals(web.getUrl())) {
                        MainActivity.this.showRewardedAd();
                    }
                });
            }
        }, "RewardedAds");

        web.setWebViewClient(new WebViewClient() {

            @Override
            public WebResourceResponse shouldInterceptRequest(
                    WebView view,
                    WebResourceRequest request) {

                Uri uri = request.getUrl();

                if ("appassets.androidplatform.net".equals(
                        uri.getHost())) {

                    String path = uri.getPath();

                    if (path != null && path.startsWith("/assets/")) {

                        try {
                            String assetPath =
                                    path.substring("/assets/".length());

                            InputStream stream =
                                    getAssets().open(assetPath);

                            String mime = "text/html";

                            if (assetPath.endsWith(".js")) {
                                mime = "text/javascript";
                            } else if (assetPath.endsWith(".css")) {
                                mime = "text/css";
                            } else if (assetPath.endsWith(".json")) {
                                mime = "application/json";
                            }

                            return new WebResourceResponse(
                                    mime,
                                    "UTF-8",
                                    stream
                            );

                        } catch (Exception e) {
                            return null;
                        }
                    }
                }

                return super.shouldInterceptRequest(view, request);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onPermissionRequest(
                    PermissionRequest request) {

                runOnUiThread(() -> {

                    if (!request.getOrigin().toString()
                            .startsWith(
                              "https://appassets.androidplatform.net/")) {
                        request.deny();
                        return;
                    }

                    boolean cameraRequested = false;

                    for (String resource : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE
                                .equals(resource)) {
                            cameraRequested = true;
                        }
                    }

                    if (!cameraRequested) {
                        request.deny();
                        return;
                    }

                    if (checkSelfPermission(
                            Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED) {

                        request.grant(new String[]{
                                PermissionRequest.RESOURCE_VIDEO_CAPTURE
                        });

                    } else {

                        pendingRequest = request;

                        requestPermissions(
                                new String[]{
                                        Manifest.permission.CAMERA
                                },
                                CAMERA_CODE
                        );
                    }
                });
            }

            @Override
            public void onPermissionRequestCanceled(
                    PermissionRequest request) {

                if (pendingRequest == request) {
                    pendingRequest = null;
                }
            }
        });

        web.loadUrl(
                GAME_URL
        );
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == CAMERA_CODE
                && pendingRequest != null) {

            PermissionRequest request = pendingRequest;
            pendingRequest = null;

            if (grantResults.length > 0
                    && grantResults[0]
                    == PackageManager.PERMISSION_GRANTED) {

                request.grant(new String[]{
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE
                });

            } else {
                request.deny();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (pendingRequest != null) {
            pendingRequest.deny();
            pendingRequest = null;
        }

        if (web != null) {
            web.destroy();
        }

        super.onDestroy();
    }
}







