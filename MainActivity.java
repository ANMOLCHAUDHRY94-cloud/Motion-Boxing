package com.motionboxing.arena;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.InputStream;

public class MainActivity extends Activity {
  private WebView web;
  private PermissionRequest pending;
  private static final int CAMERA_CODE = 101;
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    web = new WebView(this);
    setContentView(web);
    web.getSettings().setJavaScriptEnabled(true);
    web.getSettings().setDomStorageEnabled(true);
    web.getSettings().setMediaPlaybackRequiresUserGesture(false);
    web.setWebViewClient(new WebViewClient() {
      @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        android.net.Uri uri = request.getUrl();
        if ("appassets.androidplatform.net".equals(uri.getHost()) && "/index.html".equals(uri.getPath())) {
          try {
            InputStream stream = getAssets().open("index.html");
            return new WebResourceResponse("text/html", "UTF-8", stream);
          } catch (Exception ignored) { return null; }
        }
        return super.shouldInterceptRequest(view, request);
      }
    });
    web.setWebChromeClient(new WebChromeClient() {
      @Override public void onPermissionRequest(PermissionRequest request) {
        runOnUiThread(() -> {
          if (!"https://appassets.androidplatform.net".equals(request.getOrigin().toString())) { request.deny(); return; }
          boolean cameraRequested = false;
          for (String resource : request.getResources()) if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) cameraRequested = true;
          if (!cameraRequested) { request.deny(); return; }
          if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
          } else {
            pending = request;
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_CODE);
          }
        });
      }
      @Override public void onPermissionRequestCanceled(PermissionRequest request) { if (pending == request) pending = null; }
    });
    web.loadUrl("https://appassets.androidplatform.net/index.html");
  }
  @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(code, permissions, results);
    if (code == CAMERA_CODE && pending != null) {
      PermissionRequest request = pending; pending = null;
      if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
      else request.deny();
    }
  }
  @Override protected void onDestroy() { if (web != null) { web.destroy(); } super.onDestroy(); }
}
