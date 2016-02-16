package com.labs.okey.freeride;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.facebook.AccessToken;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.labs.okey.freeride.utils.Globals;

import java.text.DateFormat;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class SplashScreen extends AppCompatActivity {

    private static final String LOG_TAG = "FR.Splash";

    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private View mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };

    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }

        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    private Handler mHandler =  new Handler(Looper.getMainLooper());


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_splash_screen);

        // Setup up Crashlytics as app monitor
        Globals.initializeMonitor(this);

        Globals.InitializeVolley(getApplicationContext());

        mVisible = true;
        mContentView = findViewById(R.id.fullscreen_content);

        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });

        ExecutorService service = Executors.newFixedThreadPool(1);
        ListeningExecutorService executor = MoreExecutors.listeningDecorator(service);

        Callable<Boolean> validationTask = new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return isRegistrationValid();
            }
        };

        ListenableFuture<Boolean> regValidationFuture = executor.submit(validationTask);
        Futures.addCallback(regValidationFuture, new FutureCallback<Boolean>() {
            @Override
            public void onSuccess(final Boolean result) {
                Log.d(LOG_TAG, "Validation succeeded");

                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {

                        Intent intent = (result) ?
                                new Intent(SplashScreen.this, MainActivity.class) :
                                new Intent(SplashScreen.this, RegisterActivity.class);

                        startActivity(intent);
                        finish();
                    }
                }, 3000);
            }

            @Override
            public void onFailure(Throwable t) {
                Log.d(LOG_TAG, "Validation exception thrown");
            }
        });
    }

    private Boolean isRegistrationValid(){

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String userRegistrationId = sharedPrefs.getString(Globals.USERIDPREF, "");
        if( userRegistrationId.isEmpty() )
            return false;

        String provider = sharedPrefs.getString(Globals.REG_PROVIDER_PREF, "");
        if( provider.isEmpty() )
            return false;

        final CountDownLatch latch = new CountDownLatch(1);
        final Boolean[] bRes = new Boolean[1];

        if( provider.equals(Globals.FB_PROVIDER)) {

            FacebookSdk.sdkInitialize(getApplicationContext(), new FacebookSdk.InitializeCallback() {
                @Override
                public void onInitialized() {
                    AccessToken fbAccessToken = AccessToken.getCurrentAccessToken();
                    bRes[0] = (fbAccessToken != null );

                    if( fbAccessToken == null || fbAccessToken.isExpired()) {

                        bRes[0] = false;
                        Log.d(LOG_TAG, "FB Token is null or expired");

//                        AccessToken.refreshCurrentAccessTokenAsync(new AccessToken.AccessTokenRefreshCallback() {
//                            @Override
//                            public void OnTokenRefreshed(AccessToken accessToken) {
//                                AccessToken.setCurrentAccessToken(accessToken);
//                            }
//
//                            @Override
//                            public void OnTokenRefreshFailed(FacebookException exception) {
//
//                            }
//                        });
                    } else {
                        Globals.initializeTokenTracker(getApplicationContext());
                        bRes[0] = true;
                    }


                    latch.countDown();
                }
            });

        } else if(provider.equals(Globals.TWITTER_PROVIDER) ||
                provider.equals(Globals.DIGITS_PROVIDER)) {
            // According to https://dev.twitter.com/oauth/overview/faq ('How long does an access token last?')
            // Twitter (and Digits) tokens are not expired
            bRes[0] = true;
            latch.countDown();
        } else if( provider.equals(Globals.MICROSOFT_PROVIDER) ) {
            // If Microsoft Account tokena are expire?
            bRes[0] = true;
            latch.countDown();
        }


        try {

            latch.await();

        } catch (InterruptedException ex) {
            Log.e(LOG_TAG, ex.getLocalizedMessage());
            return false;
        }

        return bRes[0];
    }

    private Boolean validateFbToken() {

        AccessToken fbAccessToken = AccessToken.getCurrentAccessToken();

        if( fbAccessToken == null || fbAccessToken.isExpired()) {
            Log.d(LOG_TAG, "FB Token is null or expired");
            AccessToken.refreshCurrentAccessTokenAsync(new AccessToken.AccessTokenRefreshCallback() {
                @Override
                public void OnTokenRefreshed(AccessToken accessToken) {
                    AccessToken.setCurrentAccessToken(accessToken);
                }

                @Override
                public void OnTokenRefreshFailed(FacebookException exception) {

                }
            });
            return false;
        } else {
            DateFormat df = DateFormat.getDateTimeInstance();
            Log.d(LOG_TAG, "Token is valid. Will expire at " + df.format(fbAccessToken.getExpires()));

            Globals.initializeTokenTracker(getApplicationContext());

            return true;
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }
}
