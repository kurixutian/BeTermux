package com.termux.app.activities;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import com.termux.shared.termux.TermuxConstants;

/** Basic embedded browser for viewing help pages. */
public final class HelpActivity extends AppCompatActivity {

    WebView mWebView;
    private HelpLoadingState mLoadingState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mWebView = new WebView(this);
        WebSettings settings = mWebView.getSettings();
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        mLoadingState = new HelpLoadingState();
        setContentView(HelpActivityCompose.createView(this, mWebView, mLoadingState));
        mWebView.clearCache(true);

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.equals(TermuxConstants.TERMUX_WIKI_URL) || url.startsWith(TermuxConstants.TERMUX_WIKI_URL + "/")) {
                    // Inline help.
                    mLoadingState.updateLoading(true);
                    return false;
                }

                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException e) {
                    // Android TV does not have a system browser.
                    mLoadingState.updateLoading(true);
                    return false;
                }
                return true;
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                mLoadingState.updateLoading(false);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                mLoadingState.updateLoading(false);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request == null || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && request.isForMainFrame())) {
                    mLoadingState.updateLoading(false);
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                mLoadingState.updateLoading(false);
            }
        });
        mWebView.loadUrl(TermuxConstants.TERMUX_WIKI_URL);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (mWebView != null && mWebView.canGoBack()) {
            if (mLoadingState != null) {
                mLoadingState.updateLoading(false);
            }
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mWebView != null) {
            mWebView.stopLoading();
            ViewParent parent = mWebView.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(mWebView);
            }
            mWebView.destroy();
            mWebView = null;
        }
    }

}
