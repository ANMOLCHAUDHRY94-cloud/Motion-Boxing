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

public class MainActivity extends Activity {
    private WebView webView;
    private PermissionRequest cameraRequest;
    private String currentOrientation = "unspecified";
    private static final int CAMERA_CODE = 1001;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512);
        webView = new WebView(this);
        setContentView(webView);
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
