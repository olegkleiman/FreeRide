package com.labs.okey.freeride;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;

import com.facebook.AppEventsLogger;
import com.facebook.FacebookAuthorizationException;
import com.facebook.FacebookOperationCanceledException;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.UiLifecycleHelper;
import com.facebook.model.GraphUser;
import com.facebook.widget.LoginButton;
import com.labs.okey.freeride.fragments.ConfirmRegistrationFragment;
import com.labs.okey.freeride.model.User;
import com.labs.okey.freeride.utils.Globals;
import com.microsoft.windowsazure.mobileservices.MobileServiceList;
import com.microsoft.windowsazure.mobileservices.table.MobileServiceTable;

import java.util.concurrent.ExecutionException;

public class RegisterActivity extends AppCompatActivity {

    private static final String LOG_TAG = "FR.Register";

    private final String PENDING_ACTION_BUNDLE_KEY = "com.maximum.fastride:PendingAction";
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
        editor.putString(Globals.FB_LASTNAME__PREF, fbUser.getLastName());
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
}
