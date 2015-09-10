package com.labs.okey.freeride;

import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
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

import com.facebook.AppEventsLogger;
import com.facebook.FacebookAuthorizationException;
import com.facebook.FacebookException;
import com.facebook.FacebookOperationCanceledException;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.UiLifecycleHelper;
import com.facebook.model.GraphUser;
import com.facebook.widget.LoginButton;
import com.labs.okey.freeride.fragments.ConfirmRegistrationFragment;
import com.labs.okey.freeride.fragments.RegisterCarsFragment;
import com.labs.okey.freeride.model.GFence;
import com.labs.okey.freeride.model.User;
import com.labs.okey.freeride.utils.Globals;
import com.labs.okey.freeride.utils.wamsUtils;
import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.MobileServiceList;
import com.microsoft.windowsazure.mobileservices.table.MobileServiceTable;
import com.microsoft.windowsazure.mobileservices.table.query.Query;
import com.microsoft.windowsazure.mobileservices.table.sync.MobileServiceSyncTable;

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
    private final String fbProvider = "fb";

    private UiLifecycleHelper uiHelper;
    private LoginButton mFBLoginButton;

    private GraphUser fbUser;

    String mAccessToken;

    private Session.StatusCallback callback = new Session.StatusCallback() {
        @Override
        public void call(Session session, SessionState state, Exception exception) {
            onSessionStateChange(session, state, exception);
        }
    };

    @Override
    public void onDialogPositiveClick(DialogFragment dialog, final User user) {
        final String android_id = Settings.Secure.getString(this.getContentResolver(),
                Settings.Secure.ANDROID_ID);

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected void onPostExecute(Void result) {
                saveFBUser(fbUser);
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
        setContentView(R.layout.activity_register);

        uiHelper = new UiLifecycleHelper(this, callback);
        uiHelper.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            String name = savedInstanceState.getString(PENDING_ACTION_BUNDLE_KEY);
            if( name != null && name.isEmpty() )
                pendingAction = PendingAction.valueOf(name);
        }

        setContentView(R.layout.activity_register);
        Toolbar toolbar = (Toolbar) findViewById(R.id.fastride_toolbar);
        toolbar.setTitleTextColor(getResources().getColor(R.color.white));
        toolbar.setTitle(getString(R.string.title_activity_register));

        // FB stuff
        mFBLoginButton = (LoginButton) findViewById(R.id.loginButton);
        mFBLoginButton.setReadPermissions("email");

        mFBLoginButton.setUserInfoChangedCallback(new LoginButton.UserInfoChangedCallback() {
            @Override
            public void onUserInfoFetched(final GraphUser user) {
                if (user != null) {
                    RegisterActivity.this.fbUser = user;

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

                            String regID = Globals.FB_PROVIDER_FOR_STORE + user.getId();
                            try {
                                MobileServiceList<User> _users =
                                        usersTable.where().field("registration_id").eq(regID)
                                                .execute().get();

                                if (_users.getTotalCount() >= 1) {
                                    User registeredUser = _users.get(0);

//                                    new AlertDialogWrapper.Builder(RegisterActivity.this)
//                                            .setTitle(R.string.dialog_confirm_registration)
//                                            .setMessage(R.string.registration_already_performed)
//                                            .autoDismiss(true)
//                                            .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
//                                                @Override
//                                                public void onClick(DialogInterface dialog, int which) {
//                                                    //dialog.dismiss();
//                                                }
//                                            })
//                                            .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
//                                                @Override
//                                                public void onClick(DialogInterface dialog, int which) {
//                                                    //dialog.dismiss();
//                                                }
//                                            }).show();

                                    //ConfirmRegistrationFragment dialog =
                                    new ConfirmRegistrationFragment()
                                            .setUser(registeredUser)
                                            .show(getFragmentManager(), "RegistrationDialogFragment");

                                    // Just prevent body execution with onPostExecute().
                                    // Normal flow continues from positive button handler.
                                    mEx = new Exception();
                                } else {

                                    saveFBUser(user);
                                }

                            } catch (InterruptedException | ExecutionException ex) {
                                mEx = ex;
                                Log.e(LOG_TAG, ex.getMessage());
                            }

                            return null;
                        }
                    }.execute();

                }
            }
        });

        mFBLoginButton.setOnErrorListener(new LoginButton.OnErrorListener() {

            @Override
            public void onError(FacebookException error) {
                String msg = getResources().getString(R.string.fb_error_msg)
                        + error.getMessage().trim();

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
        uiHelper.onResume();

        // Call the 'activateApp' method to log an app event for use in analytics and advertising reporting.  Do so in
        // the onResume methods of the primary Activities that an app may be launched into.
        AppEventsLogger.activateApp(this);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        uiHelper.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onPause() {
        super.onPause();
        uiHelper.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        uiHelper.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        uiHelper.onSaveInstanceState(outState);
    }

    private void saveFBUser(GraphUser fbUser) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(Globals.FB_USERNAME_PREF, fbUser.getFirstName());
        editor.putString(Globals.REG_PROVIDER_PREF, fbProvider);
        editor.putString(Globals.FB_LASTNAME_PREF, fbUser.getLastName());
        editor.putString(Globals.TOKENPREF, mAccessToken);

        editor.apply();
    }

    private void handlePendingAction() {
        pendingAction = PendingAction.NONE;
    }

    private void onSessionStateChange(Session session, SessionState state, Exception exception) {
        if (pendingAction != PendingAction.NONE &&
                (exception instanceof FacebookOperationCanceledException ||
                        exception instanceof FacebookAuthorizationException)) {
//                new AlertDialog.Builder(RegisterActivity.this)
//                    .setTitle(R.string.cancelled)
//                    .setMessage(R.string.permission_not_granted)
//                    .setPositiveButton(R.string.ok, null)
//                    .show();
            pendingAction = PendingAction.NONE;
        } else if (state == SessionState.OPENED_TOKEN_UPDATED) {
            handlePendingAction();
        } else if( state == SessionState.OPENED ) {
            mAccessToken = session.getAccessToken();
        }

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

                final User newUser = new User();

                newUser.setRegistrationId(Globals.FB_PROVIDER_FOR_STORE + fbUser.getId());
                newUser.setFirstName(fbUser.getFirstName());
                newUser.setLastName(fbUser.getLastName());
                String pictureURL = "http://graph.facebook.com/" + fbUser.getId() + "/picture?width=100&height=100";
                newUser.setPictureURL(pictureURL);
                newUser.setEmail((String) fbUser.getProperty("email"));
                newUser.setPhone(txtUser.getText().toString());
                CheckBox cbUsePhone = (CheckBox)findViewById(R.id.cbUsePhone);
                newUser.setUsePhone(cbUsePhone.isChecked());

                String android_id = Settings.Secure.getString(this.getContentResolver(),
                        Settings.Secure.ANDROID_ID);
                newUser.setDeviceId(android_id);

                newUser.setPlatform(Globals.PLATFORM);

                newUser.save(this);

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
                            usersTable.insert(newUser).get();

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

                        MobileServiceSyncTable<GFence> gFencesSyncTable = wamsClient.getSyncTable("gfences",
                                GFence.class);
                        wamsUtils.sync(wamsClient, "gfences");

                        Query pullQuery = wamsClient.getTable(GFence.class).where();
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
