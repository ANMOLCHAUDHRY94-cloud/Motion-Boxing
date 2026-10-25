package com.motionboxing.game;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        setContentView(web);

        WebSettings settings = web.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

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
                "https://appassets.androidplatform.net/assets/index.html"
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
