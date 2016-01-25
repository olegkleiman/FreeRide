package com.labs.okey.freeride.fragments;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.labs.okey.freeride.R;

/**
 * Created by eli max on 24/01/2016.
 */
public class TermsFragment extends Fragment {

    private static TermsFragment FragmentInstance;

    public static TermsFragment getInstance() {

        if (FragmentInstance == null ) {
            FragmentInstance = new TermsFragment();
        }
        return FragmentInstance;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }


    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {


        View rootView = inflater.inflate(R.layout.fragment_about_terms, container, false);

        WebView webView = (WebView) rootView.findViewById(R.id.webViewTerms);


        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.setWebViewClient(new WebViewClient(){

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {

                view.loadUrl(url);

                return true;
            }
            @Override
            public void onPageFinished(WebView view, final String url) {

            }
        });


        webView.loadUrl("http://maxelmt.wix.com/fastride#!terms-of-service/m9aiq");
        return  rootView;
    }
}
