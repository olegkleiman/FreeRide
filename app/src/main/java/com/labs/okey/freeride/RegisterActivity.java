package com.labs.okey.freeride;

import android.accounts.AccountManager;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
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

import com.afollestad.materialdialogs.MaterialDialog;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
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
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.plus.Plus;
import com.google.android.gms.plus.model.people.Person;
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
import com.twitter.sdk.android.Twitter;
import com.twitter.sdk.android.core.Callback;
import com.twitter.sdk.android.core.Result;
import com.twitter.sdk.android.core.TwitterApiClient;
import com.twitter.sdk.android.core.TwitterAuthConfig;
import com.twitter.sdk.android.core.TwitterCore;
import com.twitter.sdk.android.core.TwitterException;
import com.twitter.sdk.android.core.TwitterSession;
import com.twitter.sdk.android.core.identity.TwitterAuthClient;
import com.twitter.sdk.android.core.identity.TwitterLoginButton;

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
        implements ConfirmRegistrationFragment.RegistrationDialogListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

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
    TwitterLoginButton          mTwitterloginButton;

    private User                mNewUser;
    private String              mAccessToken;
    private String              mAccessTokenSecret; // used by Twitter
    private boolean             mAddNewUser = true;

    private static final int    RC_SIGN_IN = 9001; // Used by Google+
    private static final int    REQUEST_CODE_RESOLVE_ERR = 9002;

    MaterialDialog              mGoogleProgressDialog;

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

            LinearLayout loginLayout = (LinearLayout) findViewById(R.id.login_form);
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

    // Implementation of GoogleApiClient.ConnectionCallbacks
    // for Google Play

    // After this callback, the application can make requests on other methods provided
    // by the client and expect that no user intervention is required to call methods
    // that use account and scopes provided to the client constructor.
    @Override
    public void onConnected(Bundle bundle) {
        if( mGoogleProgressDialog != null )
            mGoogleProgressDialog.dismiss();

        // This approach (described at https://developers.google.com/identity/sign-in/android/v1/people)is deprecated!
        // When app will be migrated to use Azure App Services and hence
        // it will be possible to use play services ver. 8.3 or greater,
        // change this approach to new one as described here: https://developers.google.com/identity/sign-in/android/
        String email = Plus.AccountApi.getAccountName(Globals.googleApiClient);
        getTokenAndLogin(email);
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        if( result.hasResolution() ) {
            try {
                result.startResolutionForResult(this, REQUEST_CODE_RESOLVE_ERR);
            } catch(IntentSender.SendIntentException ex) {
                // There was an error with the resolution intent. Try again.
                Globals.googleApiClient.connect();
            }
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

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

        if( Answers.getInstance() != null )
            Answers.getInstance().logCustom(new CustomEvent(getString(R.string.registration_answer_name)));

//        mDigitsAuthCallback = new AuthCallback() {
//            @Override
//            public void success(DigitsSession session, String phoneNumber)
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

        final ContentResolver contentResolver = this.getContentResolver();

        // Twitter stuff
        mTwitterloginButton = (TwitterLoginButton) findViewById(R.id.twitter_login);
        mTwitterloginButton.setCallback(new Callback<TwitterSession>() {
            @Override
            public void success(Result<TwitterSession> result) {

                TwitterSession session = Twitter.getSessionManager().getActiveSession();

                mAccessToken = result.data.getAuthToken().token;
                mAccessTokenSecret = result.data.getAuthToken().secret;

                TwitterApiClient twitterApiClient = TwitterCore.getInstance().getApiClient();
                twitterApiClient.getAccountService().verifyCredentials(false, false, new Callback<com.twitter.sdk.android.core.models.User>() {

                    @Override
                    public void success(Result<com.twitter.sdk.android.core.models.User> userResult) {

                        mNewUser = new User();
                        String userID = userResult.data.idStr;
                        mNewUser.setRegistrationId(Globals.TWITTER_PROVIDER_FOR_STORE + userID);
                        String userName = userResult.data.name;
                        String[] unTokens = userName.split(" ");
                        mNewUser.setFirstName(unTokens[0]);
                        mNewUser.setLastName(unTokens[1]);

                        String android_id = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID);
                        mNewUser.setDeviceId(android_id);

                        mNewUser.setPictureURL(userResult.data.profileImageUrl.replace("_normal", "_bigger"));

                        saveProviderAccessToken(Globals.TWITTER_PROVIDER, userID);
                    }

                    @Override
                    public void failure(TwitterException e) {

                    }
                });

                TwitterAuthClient authClient = new TwitterAuthClient();
                authClient.requestEmail(session, new Callback<String>() {
                    @Override
                    public void success(Result<String> result) {
                        mNewUser.setEmail(result.data);
                        mNewUser.save(getApplicationContext());


                        new VerifyAccountTask().execute();
                    }

                    @Override
                    public void failure(TwitterException error) {
                        Toast.makeText(RegisterActivity.this, error.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void failure(TwitterException exception) {
                // Do something on failure
            }
        });

        // Google+ stuff
//        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
//                .requestEmail()
//                .requestServerAuthCode(getString(R.string.server_client_id), false)
//                .requestIdToken(getString(R.string.server_client_id))
//                .build();
//
//        Globals.googleApiClient = new GoogleApiClient.Builder(this)
////                .enableAutoManage(this /* FragmentActivity */,
////                        new GoogleApiClient.OnConnectionFailedListener() {
////                            @Override
////                            public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
////                                Log.d(LOG_TAG, "onConnectionFailed:" + connectionResult);
////                            }
////                        })
//                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
//                .build();
//        Globals.googleApiClient.connect();

        Globals.googleApiClient = new GoogleApiClient.Builder(this)
                                .addApi(Plus.API)
                //.enableAutoManage(this, this)
                                .addScope(Plus.SCOPE_PLUS_PROFILE)
                                //.addScope(Plus.SCOPE_PLUS_LOGIN)
                                .addConnectionCallbacks(this)
                                .addOnConnectionFailedListener(this)
                .build();

        SignInButton googleSignInButton = (SignInButton)findViewById(R.id.google_login);
        googleSignInButton.setColorScheme(SignInButton.COLOR_DARK);
        googleSignInButton.setSize(SignInButton.SIZE_WIDE);
        googleSignInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!Globals.googleApiClient.isConnected()) {
                    mGoogleProgressDialog.show();
                    Globals.googleApiClient.connect();

                }
            }
        });
        mGoogleProgressDialog = new MaterialDialog.Builder(this)
                .title("Connecting to Google API")
                .content("Please wait")
                .progress(true, 0)
                .build();

        // Microsoft (Live) stuff
        final ImageButton oneDriveButton = (ImageButton) this.findViewById(R.id.msa_login);
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
                                          }
                                      }

                                      @Override
                                      public void onError(LiveOperationException exception, LiveOperation operation) {
                                          Log.e(LOG_TAG, exception.getLocalizedMessage());
                                      }
                                  });
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

        mFBLoginButton = (LoginButton) findViewById(R.id.fb_login);
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

//    private Boolean googleHandleSignInResult(GoogleSignInResult result) {
//        if ( !result.isSuccess())
//            return false;
//
//        mNewUser = new User();
//
//        GoogleSignInAccount acct = result.getSignInAccount();
//        if( acct == null )
//            return false;
//
//        mAccessToken = acct.getIdToken();
//        String regId = acct.getId();
//        mNewUser.setRegistrationId(Globals.GOOGLE_PROVIDER_FOR_STORE + regId);
//        saveProviderAccessToken(Globals.GOOGLE_PROVIDER, regId);
//
//        mNewUser.setFullName(acct.getDisplayName());
//        mNewUser.setEmail(acct.getEmail());
//        if (acct.getPhotoUrl() != null)
//            mNewUser.setPictureURL(acct.getPhotoUrl().toString());
//
//        final ContentResolver contentResolver = this.getContentResolver();
//        String android_id = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID);
//        mNewUser.setDeviceId(android_id);
//        mNewUser.setPlatform(Globals.PLATFORM);
//
//        new VerifyAccountTask().execute();
//
//        return true;
//
//    }

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
    protected void onStart() {
        super.onStart();

//        if( !Globals.googleApiClient.isConnected() )
//            Globals.googleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if( Globals.googleApiClient.isConnected() )
            Globals.googleApiClient.disconnect();
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

            String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
            getTokenAndLogin(accountName);

        } else if( requestCode == REQUEST_CODE_RESOLVE_ERR && resultCode == RESULT_OK ) {
            // Make sure the app is not already connected or attempting to connect
            if (!Globals.googleApiClient.isConnecting() &&
                    !Globals.googleApiClient.isConnected())
            Globals.googleApiClient.connect();
        }
        else if( requestCode == TwitterAuthConfig.DEFAULT_AUTH_REQUEST_CODE ) {
            mTwitterloginButton.onActivityResult(requestCode, resultCode, data);
        }

    }

    static final String GOOGLE_SCOPE_TAKE2 = "audience:server:client_id:";

    private void getTokenAndLogin(String accountName) {
        final String GOOGLE_ID_TOKEN_SCOPE = GOOGLE_SCOPE_TAKE2 + getString(R.string.server_client_id);
        // Getting Google token (thru the call to GoogleAuthUtil.getToken)
        // requires the substantial network IO, and therefore it it running off the UI thread
        new GetTokenAndLoginTask(GOOGLE_ID_TOKEN_SCOPE, accountName).execute((Void)null);

    }

    class GetTokenAndLoginTask extends AsyncTask<Void, Void, Void> {
        String mScope;
        String mEmail;

        public GetTokenAndLoginTask(String scope, String email) {
            this.mScope = scope;
            this.mEmail = email;
        }

        @Override
        protected void onPostExecute(Void res) {
            final ContentResolver contentResolver = getApplicationContext().getContentResolver();

            String android_id = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID);
            if( mNewUser != null ) {
                mNewUser.setDeviceId(android_id);
                mNewUser.setPlatform(Globals.PLATFORM);

                new VerifyAccountTask().execute();
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {

                mAccessToken = GoogleAuthUtil.getToken(getApplicationContext(), mEmail, mScope);
                String userId = GoogleAuthUtil.getAccountId(getApplicationContext(), mEmail);

                mNewUser = new User();

                mNewUser.setEmail(mEmail);
                mNewUser.setRegistrationId(Globals.GOOGLE_PROVIDER_FOR_STORE + userId);

                Person person = Plus.PeopleApi.getCurrentPerson(Globals.googleApiClient);
                if( person != null ) {
                    if( person.hasImage() )
                        mNewUser.setPictureURL(person.getImage().getUrl());
                    if( person.hasName() ) {
                        Person.Name _name =  person.getName();
                        mNewUser.setFirstName(_name.getGivenName());
                        mNewUser.setLastName(_name.getFamilyName());
                    }

                }


                saveProviderAccessToken(Globals.GOOGLE_PROVIDER, userId);

            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
            }
            return null;
        }
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
        if( mAccessTokenSecret != null && !mAccessTokenSecret.isEmpty() )
            editor.putString(Globals.TOKENSECRETPREF, mAccessTokenSecret);

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
