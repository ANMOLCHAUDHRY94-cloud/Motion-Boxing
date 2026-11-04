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
    @Override public void onBackPressed() {
        if (webView == null) { super.onBackPressed(); return; }
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
