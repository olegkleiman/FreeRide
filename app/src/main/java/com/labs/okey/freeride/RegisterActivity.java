package com.labs.okey.freeride;

import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.Profile;
import com.facebook.ProfileTracker;
import com.facebook.appevents.AppEventsLogger;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.labs.okey.freeride.fragments.ConfirmRegistrationFragment;
import com.labs.okey.freeride.fragments.RegisterCarsFragment;
import com.labs.okey.freeride.model.GeoFence;
import com.labs.okey.freeride.model.User;
import com.labs.okey.freeride.utils.Globals;
import com.labs.okey.freeride.utils.JsonKeys;
import com.labs.okey.freeride.utils.wamsUtils;
import com.microsoft.live.LiveAuthClient;
import com.microsoft.live.LiveAuthException;
import com.microsoft.live.LiveAuthListener;
import com.microsoft.live.LiveConnectClient;
import com.microsoft.live.LiveConnectSession;
import com.microsoft.live.LiveOperation;
import com.microsoft.live.LiveOperationException;
import com.microsoft.live.LiveOperationListener;
import com.microsoft.live.LiveStatus;
import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.MobileServiceList;
import com.microsoft.windowsazure.mobileservices.table.MobileServiceTable;
import com.microsoft.windowsazure.mobileservices.table.query.Query;
import com.microsoft.windowsazure.mobileservices.table.sync.MobileServiceSyncTable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

public class RegisterActivity extends FragmentActivity
        implements ConfirmRegistrationFragment.RegistrationDialogListener{

    private final String LOG_TAG = getClass().getSimpleName();
    private final String PENDING_ACTION_BUNDLE_KEY = "com.labs.okey.freeride:PendingAction";

    private CallbackManager     mFBCallbackManager;
    private LoginButton         mFBLoginButton;
    private ProfileTracker      mFbProfileTracker;

//    DigitsAuthButton        mDigitsButton;
//    private AuthCallback    mDigitsAuthCallback;
//    public AuthCallback     getAuthCallback(){
//        return mDigitsAuthCallback;
//    }
//    TwitterLoginButton      mTwitterloginButton;

    private User                mNewUser;
    private String              mAccessToken;
    private String              mAcessTokenSecret; // used by Twitter
    private boolean             mAddNewUser = true;

    private GoogleApiClient     mGoogleApiClient;
    private static final int    RC_SIGN_IN = 9001; // Used by Google+

    @Override
    public void onDialogPositiveClick(DialogFragment dialog, final User user) {
        final String android_id = Settings.Secure.getString(this.getContentResolver(),
                Settings.Secure.ANDROID_ID);

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected void onPostExecute(Void result) {
                showRegistrationForm();
                findViewById(R.id.btnRegistrationNext).setVisibility(View.VISIBLE);
            }

            @Override
            protected Void doInBackground(Void... voids) {

                user.setDeviceId(android_id);
                user.setPlatform(Globals.PLATFORM);

                try {
                    usersTable.delete(user).get();
                } catch (InterruptedException | ExecutionException ex) {
                    Log.e(LOG_TAG, ex.getMessage());
                }

                return null;
            }
        }.execute();
    }

    @Override
    public void onDialogNegativeClick(DialogFragment dialog) {

    }

    private enum PendingAction {
        NONE,
        POST_PHOTO,
        POST_STATUS_UPDATE
    }

    private PendingAction pendingAction = PendingAction.NONE;

    // 'Users' table is defined with 'Anybody with the Application Key'
    // permissions for READ and INSERT operations, so no authentication is
    // required for adding new user to it
    MobileServiceTable<User> usersTable;

    class VerifyAccountTask extends AsyncTask<Void, Void, Void> {

        Exception mEx;
        ProgressDialog progress;

        @Override
        protected void onPreExecute() {

            LinearLayout loginLayout = (LinearLayout) findViewById(R.id.fb_login_form);
            if (loginLayout != null)
                loginLayout.setVisibility(View.GONE);

            progress = ProgressDialog.show(RegisterActivity.this,
                    getString(R.string.registration_add_status),
                    getString(R.string.registration_add_status_wait));
        }

        @Override
        protected void onPostExecute(Void result) {
            progress.dismiss();

            if (mEx == null)
                showRegistrationForm();

        }

        @Override
        protected Void doInBackground(Void... params) {

            String regID = mNewUser.getRegistrationId();
            try {
                MobileServiceList<User> _users =
                        usersTable.where().field("registration_id").eq(regID)
                                .execute().get();

                if (_users.size() >= 1) {
                    User _user = _users.get(0);

                    if (_user.compare(mNewUser))
                        mAddNewUser = false;
                }

            } catch (InterruptedException | ExecutionException ex) {
                mEx = ex;
                Log.e(LOG_TAG, ex.getMessage());
            }

            return null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FacebookSdk.sdkInitialize(getApplicationContext());
        setContentView(R.layout.activity_register);

        if (savedInstanceState != null) {
            String name = savedInstanceState.getString(PENDING_ACTION_BUNDLE_KEY);
            if( name != null && name.isEmpty() )
                pendingAction = PendingAction.valueOf(name);
        }

        setContentView(R.layout.activity_register);
        Toolbar toolbar = (Toolbar) findViewById(R.id.fastride_toolbar);
        toolbar.setTitleTextColor(getResources().getColor(R.color.white));
        toolbar.setTitle(getString(R.string.title_activity_register));

//        if( Answers.getInstance() != null )
//            Answers.getInstance().logCustom(new CustomEvent(getString(R.string.registration_answer_name)));

//        mDigitsAuthCallback = new AuthCallback() {
//            @Override
//            public void success(DigitsSession session, String phoneNumber) {
////                SessionRecorder.recordSessionActive("Login: digits account active", session);
//            }
//
//            @Override
//            public void failure(DigitsException exception) {
//                // Do something on failure
//            }
//        };

        // Twitter Digits stuff
//        try {
//            mDigitsButton = (DigitsAuthButton) findViewById(R.id.digits_auth_button);
//            //mDigitsButton.setAuthTheme(android.R.style.Theme_Material);
//            mDigitsButton.setCallback(mDigitsAuthCallback);
//        } catch(Exception ex) {
//            Log.e(LOG_TAG, ex.getMessage());
//        }
//
//        // Twitter stuff
//        mTwitterloginButton = (TwitterLoginButton) findViewById(R.id.twitter_login_button);
//        mTwitterloginButton.setCallback(new Callback<TwitterSession>() {
//            @Override
//            public void success(Result<TwitterSession> result) {
//
//                mAccessToken = result.data.getAuthToken().token;
//                mAcessTokenSecret = result.data.getAuthToken().secret;
//
//                TwitterAuthClient authClient = new TwitterAuthClient();
//
//                TwitterApiClient twitterApiClient = TwitterCore.getInstance().getApiClient();
//                twitterApiClient.getAccountService().verifyCredentials(false, false, new Callback<com.twitter.sdk.android.core.models.User>(){
//
//                    @Override
//                    public void success(Result<com.twitter.sdk.android.core.models.User> userResult) {
//
//                        mNewUser = new User();
//                        mNewUser.setRegistrationId(Globals.TWITTER_PROVIDER_FOR_STORE + userResult.data.idStr);
//                        String userName = userResult.data.name;
//                        String[] unTokens = userName.split(" ");
//                        mNewUser.setFirstName(unTokens[0]);
//                        mNewUser.setLastName(unTokens[1]);
//                        mNewUser.setEmail(userResult.data.email);
//
//                        //mNewUser.setPictureURL(userResult.data.profileImageUrl);
//                        mNewUser.setPictureURL(userResult.data.profileImageUrl.replace("_normal", "_bigger"));
//
//                        mCheckUsersTask.execute(mNewUser);
//                    }
//
//                    @Override
//                    public void failure(TwitterException e) {
//
//                    }
//                });
//
//            }
//
//            @Override
//            public void failure(TwitterException exception) {
//                // Do something on failure
//            }
//        });

        final ContentResolver contentResolver = this.getContentResolver();

        // Google+ stuff
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(getString(R.string.server_client_id))
                .build();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */,
                        new GoogleApiClient.OnConnectionFailedListener() {
                            @Override
                            public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                                Log.d(LOG_TAG, "onConnectionFailed:" + connectionResult);
                            }
                        })
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();

        findViewById(R.id.sign_in_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
                startActivityForResult(signInIntent, RC_SIGN_IN);
            }
        });

        // Microsoft (Live) stuff
        final ImageButton oneDriveButton = (ImageButton) this.findViewById(R.id.query_vroom);
        oneDriveButton.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(final View v) {
              Globals.liveAuthClient = new LiveAuthClient(getApplicationContext(),
                                                          Globals.MICROSOFT_CLIENT_ID);

              Globals.liveAuthClient.login(RegisterActivity.this,
                        Arrays.asList(Globals.LIVE_SCOPES),
                        new LiveAuthListener() {
                          @Override
                          public void onAuthComplete(LiveStatus status,
                                                     LiveConnectSession session,
                                                     Object userState) {
                              if (status == LiveStatus.CONNECTED) {

                                  mAccessToken = session.getAuthenticationToken();
                                  LiveConnectClient connectClient = new LiveConnectClient(session);

                                  mNewUser = new User();

                                  connectClient.getAsync("me", new LiveOperationListener() {
                                      @Override
                                      public void onComplete(LiveOperation operation) {
                                          JSONObject result = operation.getResult();
                                          if (!result.has(JsonKeys.ERROR)) {

                                              String userID = result.optString(JsonKeys.ID);
                                              saveProviderAccessToken(Globals.MICROSOFT_PROVIDER, userID);

                                              mNewUser.setRegistrationId(Globals.MICROSOFT_PROVIDER_FOR_STORE + userID);

                                              mNewUser.setFirstName(result.optString(JsonKeys.FIRST_NAME));
                                              mNewUser.setLastName(result.optString(JsonKeys.LAST_NAME));

                                              JSONObject emails = result.optJSONObject(JsonKeys.EMAILS);
                                              String email = emails.optString("account");
                                              Log.e(LOG_TAG, email);
                                              mNewUser.setEmail(email);

                                              String android_id = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID);
                                              mNewUser.setDeviceId(android_id);
                                              mNewUser.setPlatform(Globals.PLATFORM);
                                          } else {
                                              JSONObject error = result.optJSONObject(JsonKeys.ERROR);
                                              String code = error.optString(JsonKeys.CODE);
                                              String message = error.optString(JsonKeys.MESSAGE);
                                              Toast.makeText(RegisterActivity.this, code + ": " + message, Toast.LENGTH_LONG).show();
                                          }
                                      }

                                      @Override
                                      public void onError(LiveOperationException exception, LiveOperation operation) {
                                          Toast.makeText(RegisterActivity.this, exception.getLocalizedMessage(),
                                                         Toast.LENGTH_LONG).show();
                                          Log.e(LOG_TAG, exception.getLocalizedMessage());
                                      }
                                  });

                                  connectClient.getAsync("me/picture", new LiveOperationListener() {
                                      @Override
                                      public void onComplete(LiveOperation operation) {
                                          JSONObject result = operation.getResult();
                                          if (!result.has(JsonKeys.ERROR)) {

                                              String pictureURI = result.optString(JsonKeys.LOCATION);
                                              mNewUser.setPictureURL(pictureURI);

                                              new VerifyAccountTask().execute();

//                                              new AsyncTask<Void, Void, Void>() {
//
//                                                  Exception mEx;
//                                                  ProgressDialog progress;
//
//                                                  @Override
//                                                  protected void onPreExecute() {
//
//                                                      LinearLayout loginLayout = (LinearLayout) findViewById(R.id.fb_login_form);
//                                                      if (loginLayout != null)
//                                                          loginLayout.setVisibility(View.GONE);
//
//                                                      progress = ProgressDialog.show(RegisterActivity.this,
//                                                              getString(R.string.registration_add_status),
//                                                              getString(R.string.registration_add_status_wait));
//                                                  }
//
//                                                  @Override
//                                                  protected void onPostExecute(Void result) {
//                                                      progress.dismiss();
//
//                                                      if (mEx == null)
//                                                          showRegistrationForm();
//
//                                                  }
//
//                                                  @Override
//                                                  protected Void doInBackground(Void... params) {
//
//                                                      // At this point userIs was already saved
//                                                      String regID = mNewUser.getRegistrationId();
//                                                      try {
//                                                          MobileServiceList<User> _users =
//                                                                  usersTable.where().field("registration_id").eq(regID)
//                                                                          .execute().get();
//
//                                                          if (_users.size() >= 1) {
//                                                              User _user = _users.get(0);
//
//                                                              if (_user.compare(mNewUser))
//                                                                  mAddNewUser = false;
//                                                          }
//
//                                                      } catch (InterruptedException | ExecutionException ex) {
//                                                          mEx = ex;
//                                                          Log.e(LOG_TAG, ex.getMessage());
//                                                      }
//
//                                                      return null;
//                                                  }
//                                              }.execute();

                                          }
                                      }

                                      @Override
                                      public void onError(LiveOperationException exception, LiveOperation operation) {
                                          Log.e(LOG_TAG, exception.getLocalizedMessage());
                                      }
                                  });
                              } else {

                              }
                          }

                          @Override
                          public void onAuthError(LiveAuthException exception, Object userState) {
                              Toast.makeText(RegisterActivity.this,
                                      exception.getError(), Toast.LENGTH_LONG).show();
                          }
                        });
          }
        });

        // FB stuff
        mFBCallbackManager = CallbackManager.Factory.create();

        mFBLoginButton = (LoginButton) findViewById(R.id.loginButton);
        mFBLoginButton.setReadPermissions("email");

        // Callback registration
        mFBLoginButton.registerCallback(mFBCallbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(final LoginResult loginResult) {

                mAccessToken = loginResult.getAccessToken().getToken();

                mNewUser = new User();

                if (Profile.getCurrentProfile() == null) {

                    mFbProfileTracker = new ProfileTracker() {
                        @Override
                        protected void onCurrentProfileChanged(Profile oldProfile, final Profile profile) {

                            mNewUser.setRegistrationId(Globals.FB_PROVIDER_FOR_STORE + profile.getId());
                            mNewUser.setFirstName(profile.getFirstName());
                            mNewUser.setLastName(profile.getLastName());
                            String pictureURI = profile.getProfilePictureUri(100, 100).toString();
                            mNewUser.setPictureURL(pictureURI);

                            String android_id = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID);
                            mNewUser.setDeviceId(android_id);
                            mNewUser.setPlatform(Globals.PLATFORM);

                            completeFBRegistration(loginResult.getAccessToken(), profile.getId());
                            mFbProfileTracker.stopTracking();
                        }
                    };
                    mFbProfileTracker.startTracking();
                } else {
                    Profile profile = Profile.getCurrentProfile();

                    mNewUser.setRegistrationId(Globals.FB_PROVIDER_FOR_STORE + profile.getId());
                    mNewUser.setFirstName(profile.getFirstName());
                    mNewUser.setLastName(profile.getLastName());
                    String pictureURI = profile.getProfilePictureUri(100, 100).toString();
                    mNewUser.setPictureURL(pictureURI);

                    String android_id = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID);
                    mNewUser.setDeviceId(android_id);
                    mNewUser.setPlatform(Globals.PLATFORM);

                    completeFBRegistration(loginResult.getAccessToken(), profile.getId());
                }
            }

            @Override
            public void onCancel() {
                // App code
            }

            @Override
            public void onError(FacebookException exception) {
                String msg = getResources().getString(R.string.fb_error_msg)
                        + exception.getMessage().trim();

                new AlertDialog.Builder(RegisterActivity.this)
                        .setTitle(getResources().getString(R.string.fb_error))
                        .setMessage(msg)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });

        try{
            usersTable = new MobileServiceClient(
                    Globals.WAMS_URL,
                    Globals.WAMS_API_KEY,
                    this)
                    .getTable("users", User.class);

        } catch(MalformedURLException ex ) {
            Log.e(LOG_TAG, ex.getMessage() + " Cause: " + ex.getCause());
        }
    }

    private void googleHandleSignInResult(GoogleSignInResult result) {
        if (result.isSuccess()) {

            mNewUser = new User();

            GoogleSignInAccount acct = result.getSignInAccount();

            mAccessToken = acct.getIdToken();
            String regId = acct.getId();
            mNewUser.setRegistrationId(Globals.GOOGLE_PROVIDER_FOR_STORE + regId);
            saveProviderAccessToken(Globals.GOOGLE_PROVIDER, regId);

            mNewUser.setFullName(acct.getDisplayName());
            mNewUser.setEmail(acct.getEmail());
            if( acct.getPhotoUrl() != null )
                mNewUser.setPictureURL(acct.getPhotoUrl().toString());

            final ContentResolver contentResolver = this.getContentResolver();
            String android_id = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID);
            mNewUser.setDeviceId(android_id);
            mNewUser.setPlatform(Globals.PLATFORM);

            new VerifyAccountTask().execute();

//            new AsyncTask<Void, Void, Void>() {
//
//                Exception mEx;
//                ProgressDialog progress;
//
//                @Override
//                protected void onPreExecute() {
//
//                    LinearLayout loginLayout = (LinearLayout) findViewById(R.id.fb_login_form);
//                    if (loginLayout != null)
//                        loginLayout.setVisibility(View.GONE);
//
//                    progress = ProgressDialog.show(RegisterActivity.this,
//                            getString(R.string.registration_add_status),
//                            getString(R.string.registration_add_status_wait));
//                }
//
//                @Override
//                protected void onPostExecute(Void result) {
//                    progress.dismiss();
//
//                    if (mEx == null)
//                        showRegistrationForm();
//
//                }
//
//                @Override
//                protected Void doInBackground(Void... params) {
//
//                    // At this point userIs was already saved
//                    String regID = Globals.GOOGLE_PROVIDER_FOR_STORE + mNewUser.getRegistrationId();
//                    try {
//                        MobileServiceList<User> _users =
//                                usersTable.where().field("registration_id").eq(regID)
//                                        .execute().get();
//
//                        if (_users.size() >= 1) {
//                            User _user = _users.get(0);
//
//                            if (_user.compare(mNewUser))
//                                mAddNewUser = false;
//                        }
//
//                    } catch (InterruptedException | ExecutionException ex) {
//                        mEx = ex;
//                        Log.e(LOG_TAG, ex.getMessage());
//                    }
//
//                    return null;
//                }
//            }.execute();
        }
    }

    private void completeFBRegistration(AccessToken accessToken, final String regId){
        GraphRequest request = GraphRequest.newMeRequest(
                accessToken,
                new GraphRequest.GraphJSONObjectCallback() {
                    @Override
                    public void onCompleted(
                            JSONObject object,
                            GraphResponse response) {

                        try {
                            JSONObject gUser = response.getJSONObject();
                            String email = gUser.getString("email");
                            mNewUser.setEmail(email);

                            String regID = Globals.FB_PROVIDER_FOR_STORE + regId;
                            saveProviderAccessToken(Globals.FB_PROVIDER, regID);

                            new VerifyAccountTask().execute();

//                            new AsyncTask<Void, Void, Void>() {
//
//                                Exception mEx;
//                                ProgressDialog progress;
//
//                                @Override
//                                protected void onPreExecute() {
//
//                                    LinearLayout loginLayout = (LinearLayout) findViewById(R.id.fb_login_form);
//                                    if (loginLayout != null)
//                                        loginLayout.setVisibility(View.GONE);
//
//                                    progress = ProgressDialog.show(RegisterActivity.this,
//                                            getString(R.string.registration_add_status),
//                                            getString(R.string.registration_add_status_wait));
//                                }
//
//                                @Override
//                                protected void onPostExecute(Void result) {
//                                    progress.dismiss();
//
//                                    if (mEx == null)
//                                        showRegistrationForm();
//
//                                }
//
//                                @Override
//                                protected Void doInBackground(Void... params) {
//
//                                    String regID = Globals.FB_PROVIDER_FOR_STORE + regId;
//                                    try {
//
//                                        saveProviderAccessToken(Globals.FB_PROVIDER, regID);
//
//                                        MobileServiceList<User> _users =
//                                                usersTable.where().field("registration_id").eq(regID)
//                                                        .execute().get();
//
//                                        if (_users.size() >= 1) {
//                                            User _user = _users.get(0);
//
//                                            if (_user.compare(mNewUser))
//                                                mAddNewUser = false;
//                                        }
//
//                                    } catch (InterruptedException | ExecutionException ex) {
//                                        mEx = ex;
//                                        Log.e(LOG_TAG, ex.getMessage());
//                                    }
//
//                                    return null;
//                                }
//                            }.execute();

                        } catch (JSONException ex) {
                            Log.e(LOG_TAG, ex.getLocalizedMessage());
                        }

                    }
                });
        Bundle parameters = new Bundle();
        parameters.putString("fields", "id,name,email");
        request.setParameters(parameters);
        request.executeAsync();
    }

    @Override
    public void onResume() {
        super.onResume();
//        uiHelper.onResume();

        // Call the 'activateApp' method to log an app event for use in analytics and advertising reporting.  Do so in
        // the onResume methods of the primary Activities that an app may be launched into.
        AppEventsLogger.activateApp(this);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if( FacebookSdk.isFacebookRequestCode(requestCode) )
            mFBCallbackManager.onActivityResult(requestCode, resultCode, data);
        else if( requestCode == RC_SIGN_IN ) { // Google
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            googleHandleSignInResult(result);
        }
        //mTwitterloginButton.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if( mFbProfileTracker != null && mFbProfileTracker.isTracking() )
            mFbProfileTracker.stopTracking();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    private void saveProviderAccessToken(String provider, String userID) {

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = sharedPrefs.edit();

        editor.putString(Globals.REG_PROVIDER_PREF, provider);
        editor.putString(Globals.USERIDPREF, userID);
        editor.putString(Globals.TOKENPREF, mAccessToken);
        if( mAcessTokenSecret != null && !mAcessTokenSecret.isEmpty() )
            editor.putString(Globals.TOKENSECRETPREF, mAcessTokenSecret);

        editor.apply();
    }

    private void handlePendingAction() {
        pendingAction = PendingAction.NONE;
    }

    private void showRegistrationForm() {
        LinearLayout form = (LinearLayout)findViewById(R.id.register_form);
        form.setVisibility(View.VISIBLE);
        View buttonNext = findViewById(R.id.btnRegistrationNext);
        buttonNext.setVisibility(View.VISIBLE);
    }

    private void hideRegistrationForm() {
        LinearLayout form = (LinearLayout)findViewById(R.id.register_form);
        form.setVisibility(View.GONE);
    }

    boolean bCarsFragmentDisplayed = false;

    public void onRegisterNext(View v){

        if( !bCarsFragmentDisplayed ) {

            EditText txtUser = (EditText) findViewById(R.id.phone);
            if (txtUser.getText().toString().isEmpty()) {

                String noPhoneNumber = getResources().getString(R.string.no_phone_number);
                txtUser.setError(noPhoneNumber);
                return;
            }

            try {

                mNewUser.setPhone(txtUser.getText().toString());
                CheckBox cbUsePhone = (CheckBox)findViewById(R.id.cbUsePhone);
                mNewUser.setUsePhone(cbUsePhone.isChecked());

                mNewUser.save(this);

                new AsyncTask<Void, Void, Void>() {

                    Exception mEx;
                    ProgressDialog progress;

                    @Override
                    protected void onPreExecute() {
                        progress = ProgressDialog.show(RegisterActivity.this,
                                getString(R.string.registration_add_title),
                                getString(R.string.registration_add_status));
                    }

                    @Override
                    protected void onPostExecute(Void result){
                        progress.dismiss();

                        //if( mEx == null )

                        hideRegistrationForm();

                        FragmentManager fragmentManager = getFragmentManager();
                        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

                        RegisterCarsFragment fragment = new RegisterCarsFragment();
                        fragmentTransaction.add(R.id.register_cars_form, fragment);
                        fragmentTransaction.commit();

                        bCarsFragmentDisplayed = true;
                        Button btnNext = (Button)findViewById(R.id.btnRegistrationNext);
                        btnNext.setVisibility(View.VISIBLE);
                        btnNext.setText(R.string.registration_finish);
                    }

                    @Override
                    protected Void doInBackground(Void... voids) {
                        try {

                            // 'Users' table is defined with 'Anybody with the Application Key'
                            // permissions for READ and INSERT operations, so no authentication is
                            // required for adding new user to it
                            if( mAddNewUser )
                                usersTable.insert(mNewUser).get();

                        } catch (InterruptedException | ExecutionException e) {
                            mEx = e;
                        }

                        return null;
                    }
                }.execute();

//                // 'Users' table is defined with 'Anybody with the Application Key'
//                // permissions for READ and INSERT operations, so no authentication is
//                // required for adding new user to it
//                usersTable.insert(newUser, new TableOperationCallback<User>() {
//                    @Override
//                    public void onCompleted(User user, Exception e, ServiceFilterResponse serviceFilterResponse) {
//                        progress.dismiss();
//
//                        if( e != null ) {
//                            Toast.makeText(RegisterActivity.this,
//                                    e.getMessage(), Toast.LENGTH_LONG).show();
//                        } else {
//
//                            hideRegistrationForm();
//
//                            FragmentManager fragmentManager = getFragmentManager();
//                            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
//
//                            RegisterCarsFragment fragment = new RegisterCarsFragment();
//                            fragmentTransaction.add(R.id.register_cars_form, fragment);
//                            fragmentTransaction.commit();
//
//                            bCarsFragmentDisplayed = true;
//                            Button btnNext = (Button)findViewById(R.id.btnRegistrationNext);
//                            btnNext.setVisibility(View.VISIBLE);
//                            btnNext.setText(R.string.registration_finish);
//                        }
//                    }
//                });

            } catch(Exception ex){
                Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
            }

        } else { // Finish

            final View view = findViewById(R.id.register_cars_form);

            new AsyncTask<Void, String, Void>() {

                Exception mEx;

                ProgressDialog progressDialog;
                @Override
                protected void onPreExecute() {

                    super.onPreExecute();

                    progressDialog = ProgressDialog.show(RegisterActivity.this,
                            getString(R.string.download_data),
                            getString(R.string.download_geofences_desc));
                }

                @Override
                protected void onPostExecute(Void result){

                    if( progressDialog != null ) {
                        progressDialog.dismiss();
                        progressDialog = null;
                    }

                    if( mEx == null ) {

                        Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                        startActivity(intent);
                        finish();

                    } else {
                        Snackbar snackbar =
                                Snackbar.make(view, mEx.getMessage(), Snackbar.LENGTH_LONG);
                        snackbar.setActionTextColor(getResources().getColor(R.color.white));
                        //snackbar.setDuration(8000);
                        snackbar.show();
                    }
                }

                @Override
                protected void onProgressUpdate(String... progress) {
                    progressDialog.setMessage(progress[0]);
                }

                @Override
                protected Void doInBackground(Void... voids) {

                    try {
                        mEx = null;

                        MobileServiceClient wamsClient =
                                new MobileServiceClient(
                                        Globals.WAMS_URL,
                                        Globals.WAMS_API_KEY,
                                        getApplicationContext());

                        MobileServiceSyncTable<GeoFence> gFencesSyncTable = wamsClient.getSyncTable("geofences",
                                GeoFence.class);
                        wamsUtils.sync(wamsClient, "geofences");

                        Query pullQuery = wamsClient.getTable(GeoFence.class).where();
                        gFencesSyncTable.purge(pullQuery);
                        gFencesSyncTable.pull(pullQuery).get();

                        publishProgress( getString(R.string.download_classifiers_desc) );

                        // Download cascade(s)
                        URL url = new URL(Globals.CASCADE_URL);
                        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                        urlConnection.setRequestMethod("GET");
                        urlConnection.connect();

                        String cascadeName = Uri.parse(Globals.CASCADE_URL).getLastPathSegment();

                        //set the path where we want to save the file
                        File file = new File(getFilesDir(), cascadeName);
                        FileOutputStream fileOutput = new FileOutputStream(file);

                        InputStream inputStream = urlConnection.getInputStream();

                        byte[] buffer = new byte[1024];
                        int bufferLength;

                        while ( (bufferLength = inputStream.read(buffer)) > 0 ) {
                            fileOutput.write(buffer, 0, bufferLength);
                        }
                        fileOutput.close();

                        Globals.setCascadePath(file.getAbsolutePath());

                    } catch(InterruptedException | ExecutionException | IOException ex ) {
                        mEx = ex;
                        Log.e(LOG_TAG, ex.getMessage() + " Cause: " + ex.getCause());
                    }

                    return null;
                }
            }.execute();
        }
    }
}
