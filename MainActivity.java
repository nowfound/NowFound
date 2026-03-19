package com.nowfound.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.webkit.*;
import android.widget.*;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.firebase.messaging.FirebaseMessaging;
import android.util.Log;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "NowFound";
    private static final String WEBSITE_URL = "https://smart-qr.free.nf/smart-qr/home.php";
    private static final int PERMISSION_REQUEST_CODE = 100;

    private WebView webView;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout noInternetLayout;
    private String fcmToken = "";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        noInternetLayout = findViewById(R.id.noInternetLayout);
        Button retryBtn = findViewById(R.id.retryButton);

        // Permissions मागा
        requestAppPermissions();

        // FCM Token आधी मिळवा
        getFCMToken();

        // WebView Settings
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // JavaScript Bridge — App → Website
        webView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");

        // Camera & Mic permission for WebRTC
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }

            @Override
            public void onProgressChanged(WebView view, int progress) {
                progressBar.setProgress(progress);
                progressBar.setVisibility(progress < 100 ? View.VISIBLE : View.GONE);
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                swipeRefresh.setRefreshing(false);
                progressBar.setVisibility(View.GONE);

                // Page load झाल्यावर FCM Token website ला पाठवा
                injectFCMToken(view);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode,
                                        String description, String failingUrl) {
                swipeRefresh.setRefreshing(false);
                showNoInternet();
            }
        });

        // Swipe to Refresh
        swipeRefresh.setColorSchemeResources(R.color.colorPrimary);
        swipeRefresh.setOnRefreshListener(() -> {
            if (isInternetAvailable()) {
                hideNoInternet();
                webView.reload();
            } else {
                swipeRefresh.setRefreshing(false);
                showNoInternet();
            }
        });

        // Retry Button
        retryBtn.setOnClickListener(v -> {
            if (isInternetAvailable()) {
                hideNoInternet();
                loadWebsite();
            } else {
                showNoInternet();
            }
        });

        // Website Load
        if (isInternetAvailable()) {
            loadWebsite();
        } else {
            showNoInternet();
        }
    }

    // FCM Token मिळवा
    private void getFCMToken() {
        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    fcmToken = task.getResult();
                    Log.d(TAG, "FCM Token: " + fcmToken);
                } else {
                    Log.e(TAG, "FCM Token failed", task.getException());
                }
            });
    }

    // FCM Token WebView मध्ये inject करा
    private void injectFCMToken(WebView view) {
        if (fcmToken != null && !fcmToken.isEmpty()) {
            // window.fcmToken set करा
            String js = "javascript:(function() {" +
                "window.fcmToken = '" + fcmToken + "';" +
                // Database मध्ये `id` field आहे — user_id नाही
                "var userId = document.getElementById('id') ? " +
                "document.getElementById('id').value : " +
                "localStorage.getItem('id');" +
                "if(typeof saveFCMToken === 'function') {" +
                "   saveFCMToken('" + fcmToken + "', userId);" +
                "}" +
                "console.log('FCM Token injected: " + fcmToken + "');" +
                "})()";
            view.evaluateJavascript(js, null);
        }
    }

    // JavaScript Interface — Website → App
    public class WebAppInterface {
        @JavascriptInterface
        public String getFCMToken() {
            return fcmToken;
        }

        @JavascriptInterface
        public void tokenSaved(String status) {
            Log.d(TAG, "Token save status: " + status);
        }
    }

    private void requestAppPermissions() {
        String[] permissions;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            permissions = new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.MODIFY_AUDIO_SETTINGS,
                Manifest.permission.POST_NOTIFICATIONS
            };
        } else {
            permissions = new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.MODIFY_AUDIO_SETTINGS
            };
        }

        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }

    private void loadWebsite() {
        progressBar.setVisibility(View.VISIBLE);
        webView.loadUrl(WEBSITE_URL);
    }

    private void showNoInternet() {
        webView.setVisibility(View.GONE);
        noInternetLayout.setVisibility(View.VISIBLE);
    }

    private void hideNoInternet() {
        webView.setVisibility(View.VISIBLE);
        noInternetLayout.setVisibility(View.GONE);
    }

    private boolean isInternetAvailable() {
        ConnectivityManager cm =
            (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkCapabilities caps =
                cm.getNetworkCapabilities(cm.getActiveNetwork());
            return caps != null && (
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            // App बंद करू नका — background मध्ये ठेवा
            moveTaskToBack(true);
        }
    }
}
