package com.nowfound.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.messaging.FirebaseMessaging;
import android.util.Log;
import android.Manifest;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "NowFound";
    private static final String URL = "https://smart-qr.free.nf/smart-qr/home.php";
    private static final int PERM_CODE = 100;

    private WebView webView;
    private LinearLayout noInternetView;
    private String fcmToken = "";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Root Layout — XML layout नाही वापरत
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        // WebView
        webView = new WebView(this);
        LinearLayout.LayoutParams wpLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        webView.setLayoutParams(wpLp);

        // No Internet View
        noInternetView = new LinearLayout(this);
        noInternetView.setOrientation(LinearLayout.VERTICAL);
        noInternetView.setGravity(Gravity.CENTER);
        noInternetView.setBackgroundColor(Color.WHITE);
        noInternetView.setVisibility(android.view.View.GONE);
        LinearLayout.LayoutParams niLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        noInternetView.setLayoutParams(niLp);

        TextView msg = new TextView(this);
        msg.setText("📡 Internet नाही!\nकृपया इंटरनेट तपासा.");
        msg.setTextSize(18);
        msg.setGravity(Gravity.CENTER);
        msg.setTextColor(Color.DKGRAY);
        msg.setPadding(40, 0, 40, 40);

        Button retry = new Button(this);
        retry.setText("पुन्हा प्रयत्न करा");
        retry.setOnClickListener(v -> {
            if (isOnline()) {
                noInternetView.setVisibility(android.view.View.GONE);
                webView.setVisibility(android.view.View.VISIBLE);
                webView.loadUrl(URL);
            }
        });

        noInternetView.addView(msg);
        noInternetView.addView(retry);
        root.addView(webView);
        root.addView(noInternetView);
        setContentView(root);

        // Permissions
        requestPerms();

        // FCM Token
        FirebaseMessaging.getInstance().getToken()
            .addOnSuccessListener(t -> { fcmToken = t; Log.d(TAG, "FCM: " + t); });

        // WebView Settings
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setAllowFileAccess(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setBuiltInZoomControls(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // JS Bridge
        webView.addJavascriptInterface(new JSBridge(), "AndroidBridge");

        // Camera/Mic for WebRTC
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest r) {
                runOnUiThread(() -> r.grant(r.getResources()));
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                v.loadUrl(r.getUrl().toString()); return true;
            }
            @Override
            public void onPageFinished(WebView v, String u) {
                if (!fcmToken.isEmpty()) {
                    v.evaluateJavascript("window.fcmToken='" + fcmToken + "';" +
                        "if(typeof receiveFCMToken==='function'){receiveFCMToken('" + fcmToken + "');}", null);
                }
            }
            @Override
            public void onReceivedError(WebView v, int c, String d, String u) {
                noInternetView.setVisibility(android.view.View.VISIBLE);
                webView.setVisibility(android.view.View.GONE);
            }
        });

        if (isOnline()) webView.loadUrl(URL);
        else { noInternetView.setVisibility(android.view.View.VISIBLE); webView.setVisibility(android.view.View.GONE); }
    }

    public class JSBridge {
        @JavascriptInterface
        public String getToken() { return fcmToken; }
    }

    private void requestPerms() {
        String[] p = Build.VERSION.SDK_INT >= 33 ? new String[]{
            Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS, Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        } : new String[]{
            Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS, Manifest.permission.ACCESS_FINE_LOCATION
        };
        ActivityCompat.requestPermissions(this, p, PERM_CODE);
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return nc != null && (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            || nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else moveTaskToBack(true);
    }
}
