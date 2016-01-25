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
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.Profile;
import com.facebook.appevents.AppEventsLogger;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;
import com.labs.okey.freeride.fragments.ConfirmRegistrationFragment;
import com.labs.okey.freeride.fragments.RegisterCarsFragment;
import com.labs.okey.freeride.model.GeoFence;
import com.labs.okey.freeride.model.User;
import com.labs.okey.freeride.utils.Globals;
import com.labs.okey.freeride.utils.wamsUtils;
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
import java.util.concurrent.ExecutionException;

public class RegisterActivity extends FragmentActivity
        implements ConfirmRegistrationFragment.RegistrationDialogListener{

    private static final String LOG_TAG = "FR.Register";

    private final String PENDING_ACTION_BUNDLE_KEY = "com.labs.okey.freeride:PendingAction";

    User                        mNewUser;

    private CallbackManager     mFBCallbackManager;
    private LoginButton         mFBLoginButton;

//    DigitsAuthButton        mDigitsButton;
//    private AuthCallback    mDigitsAuthCallback;
//    public AuthCallback     getAuthCallback(){
//        return mDigitsAuthCallback;
//    }
//    TwitterLoginButton      mTwitterloginButton;

    String                  mAccessToken;
    String                  mAcessTokenSecret; // used by Twitter
    private boolean         mAddNewUser = true;

    private AsyncTask<User, Void, Void> mCheckUsersTask = new AsyncTask<User, Void, Void>() {

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
        protected Void doInBackground(User... params) {

            User _user = params[0];

            String regID = _user.getRegistrationId();
            try {

                MobileServiceList<User> _users =
                        usersTable.where().field("registration_id").eq(regID)
                                .execute().get();

                if( _users.size() >= 1 )
                    mAddNewUser = false;

                saveProviderAccessToken(Globals.TWITTER_PROVIDER);

            } catch (InterruptedException | ExecutionException ex) {
                mEx = ex;
                Log.e(LOG_TAG, ex.getMessage());
            }

            return null;
        }
    };

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

        // FB stuff
        mFBCallbackManager = CallbackManager.Factory.create();

        mFBLoginButton = (LoginButton) findViewById(R.id.loginButton);
        mFBLoginButton.setReadPermissions("email");

        final ContentResolver contentResolver = this.getContentResolver();

        // Callback registration
        mFBLoginButton.registerCallback(mFBCallbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {

                mAccessToken = loginResult.getAccessToken().getToken();

                final Profile profile = Profile.getCurrentProfile();

                mNewUser = new User();
                mNewUser.setRegistrationId(Globals.FB_PROVIDER_FOR_STORE + profile.getId());

                mNewUser.setFirstName(profile.getFirstName());
                mNewUser.setLastName(profile.getLastName());
                String pictureURL = "http://graph.facebook.com/" + profile.getId() + "/picture?width=100&height=100";
                mNewUser.setPictureURL(pictureURL);

                String android_id = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID);
                mNewUser.setDeviceId(android_id);
                mNewUser.setPlatform(Globals.PLATFORM);

                GraphRequest request = GraphRequest.newMeRequest(
                        loginResult.getAccessToken(),
                        new GraphRequest.GraphJSONObjectCallback() {
                            @Override
                            public void onCompleted(
                                    JSONObject object,
                                    GraphResponse response) {

                                try {
                                    JSONObject gUser = response.getJSONObject();
                                    String email = gUser.getString("email");
                                    mNewUser.setEmail(email);

                                    new AsyncTask<Void, Void, Void>() {

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

                                            String regID = Globals.FB_PROVIDER_FOR_STORE + profile.getId();
                                            try {

                                                saveProviderAccessToken(Globals.FB_PROVIDER);

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
                                    }.execute();

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
        mFBCallbackManager.onActivityResult(requestCode, resultCode, data);
        //mTwitterloginButton.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    private void saveProviderAccessToken(String provider) {

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = sharedPrefs.edit();

        editor.putString(Globals.REG_PROVIDER_PREF, provider);
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

                        Intent returnIntent = new Intent();
                        returnIntent.putExtra(Globals.TOKENPREF, mAccessToken);
                        returnIntent.putExtra(Globals.TOKENSECRETPREF, mAcessTokenSecret);
                        setResult(RESULT_OK, returnIntent);
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
                        int bufferLength = 0;

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
