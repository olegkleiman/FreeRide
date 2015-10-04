package com.labs.okey.freeride;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.crashlytics.android.answers.LoginEvent;
import com.crashlytics.android.answers.SignUpEvent;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.gson.JsonObject;
import com.labs.okey.freeride.adapters.ModesPeersAdapter;
import com.labs.okey.freeride.gcm.GCMHandler;
import com.labs.okey.freeride.model.FRMode;
import com.labs.okey.freeride.model.User;
import com.labs.okey.freeride.utils.Globals;
import com.labs.okey.freeride.utils.IRecyclerClickListener;
import com.labs.okey.freeride.utils.RoundedDrawable;
import com.labs.okey.freeride.utils.WAMSVersionTable;
import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.authentication.MobileServiceAuthenticationProvider;
import com.microsoft.windowsazure.mobileservices.authentication.MobileServiceUser;
import com.microsoft.windowsazure.notifications.NotificationsManager;

import io.fabric.sdk.android.Fabric;
import io.fabric.sdk.android.services.common.Crash;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;

public class MainActivity extends BaseActivity
        implements WAMSVersionTable.IVersionMismatchListener,
                          IRecyclerClickListener {

    static final int REGISTER_USER_REQUEST = 1;
    private static final String LOG_TAG = "FR.Main";
    public static MobileServiceClient wamsClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        try {
            // Needed to detect HashCode for FB registration
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(),
                    PackageManager.GET_SIGNATURES);
        }
        catch (PackageManager.NameNotFoundException ex) {
            Log.e(LOG_TAG, ex.toString());
        }

//        PassengerFace pf1 = new PassengerFace("1");
//        PassengerFace pf2 = new PassengerFace("2");
//        PassengerFace pf3 = new PassengerFace("3");
//        PassengerFace pf4 = new PassengerFace("4");
//        Globals.passengerFaces.add(pf1);
//        Globals.passengerFaces.add(pf2);
//        Globals.passengerFaces.add(pf3);
//        Globals.passengerFaces.add(pf4);
//
//        int mDepth = Globals.passengerFaces.size();
//        faceapiUtils.dumpVerificationMatrix(mDepth);
//
//        for(int i = 0; i < mDepth; i++ ) {
//            for (int j = i; j < mDepth; j++) {
//
//                if (i == j)
//                    continue;
//
//                PassengerFace _pf1 = Globals.passengerFaces.get(i);
//                PassengerFace _pf2 = Globals.passengerFaces.get(j);
//
//                float matValue = Globals.verificationMat.get(i, j);
//                if( matValue == 0.0f) {
//                    Globals.verificationMat.set(i, j, 2.0f);
//                    Globals.verificationMat.set(j, i, 2.0f);
//                }
//
//            }
//        }
//
//        faceapiUtils.dumpVerificationMatrix(mDepth);

        super.onCreate(savedInstanceState);
        try {
            Fabric.with(this, new Crashlytics());
            Crashlytics.log(Log.VERBOSE, LOG_TAG, getString(R.string.log_start));


        } catch(Exception e) {
            Log.e(LOG_TAG, e.getMessage());
        }

        setContentView(R.layout.activity_main);

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String userRegistrationId = sharedPrefs.getString(Globals.USERIDPREF, "");
        if( userRegistrationId.isEmpty() ) {

            Intent intent = new Intent(MainActivity.this, RegisterActivity.class);
            startActivityForResult(intent, REGISTER_USER_REQUEST);

            Answers.getInstance().logCustom(new CustomEvent("Registration"));

            // To be continued on onActivityResult()

        } else {

            NotificationsManager.handleNotifications(this, Globals.SENDER_ID,
                    GCMHandler.class);

            String accessToken = sharedPrefs.getString(Globals.TOKENPREF, "");

            // Don't mess with BaseActivity.wamsInit();
            wamsInit(accessToken);

            WAMSVersionTable wamsVersionTable = new WAMSVersionTable(this, this);
            try {

                PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
                String packageVersionName = info.versionName;
                if (!packageVersionName.isEmpty()) {

                    StringTokenizer tokens = new StringTokenizer(packageVersionName, ".");
                    if( tokens.countTokens() > 0 ) {
                        int majorPackageVersion = Integer.parseInt(tokens.nextToken());
                        int minorPackageVersion = Integer.parseInt(tokens.nextToken());

                        wamsVersionTable.compare(majorPackageVersion, minorPackageVersion);
                    }
                }

            }catch(PackageManager.NameNotFoundException ex) {
                if( Crashlytics.getInstance() != null)
                    Crashlytics.logException(ex);

                Log.e(LOG_TAG, ex.getMessage());
            }

            setupUI(getString(R.string.title_activity_main), "");

            //throw new RuntimeException("This is a test crash");
        }
    }

    //
    // Implementation of IVersionMismatchListener
    //
    public void mismatch(int major, int minor, final String url){
        try {

            new MaterialDialog.Builder(this)
                    .title(getString(R.string.new_version_title))
                    .content(getString(R.string.new_version_conent))
                    .positiveText(R.string.yes)
                    .negativeText(R.string.no)
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setData(Uri.parse(url));
                            //intent.setDataAndType(Uri.parse(url), "application/vnd.android.package-archive");
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                    })
                    .show();
        } catch (Exception e) {
            if( Crashlytics.getInstance() != null)
                Crashlytics.logException(e);

            // better that catch the exception here would be use handle to send events the activity
            Log.e(LOG_TAG, e.getMessage());
        }
    }

    public void match() {

    }

    public void connectionFailure(Exception ex) {

        if( ex != null ) {

            View v = findViewById(R.id.drawer_layout);
            Snackbar.make(v, ex.getMessage(), Snackbar.LENGTH_LONG);
        }

    }

    protected void setupUI(String title, String subTitle) {
        super.setupUI(title, subTitle);

        try {
            User user = User.load(this);

            ImageView imageAvatar = (ImageView) findViewById(R.id.userAvatarView);

            Drawable drawable =
                    (Globals.drawMan.userDrawable(this,
                            "1",
                            user.getPictureURL())).get();
            if( drawable != null ) {
                drawable = RoundedDrawable.fromDrawable(drawable);
                ((RoundedDrawable) drawable)
                        .setCornerRadius(Globals.PICTURE_CORNER_RADIUS)
                        .setBorderColor(Color.WHITE)
                        .setBorderWidth(Globals.PICTURE_BORDER_WIDTH)
                        .setOval(true);

                imageAvatar.setImageDrawable(drawable);
            }
        } catch (Exception e) {
            if( Crashlytics.getInstance() != null)
                Crashlytics.logException(e);

            Log.e(LOG_TAG, e.getMessage());
        }

        RecyclerView recycler = (RecyclerView)findViewById(R.id.recyclerViewModes);
        recycler.setHasFixedSize(true);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setItemAnimator(new DefaultItemAnimator());

        List<FRMode> modes = new ArrayList<>();
        FRMode mode1 = new FRMode();
        mode1.setName( getString(R.string.mode_name_driver));
        mode1.setImageId(R.drawable.driver64);
        modes.add(mode1);
        FRMode mode2 = new FRMode();
        mode2.setName(getString(R.string.mode_name_passenger));
        mode2.setImageId(R.drawable.passenger64);
        modes.add(mode2);

        ModesPeersAdapter adapter = new ModesPeersAdapter(this, modes);
        recycler.setAdapter(adapter);
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        Log.d(LOG_TAG, "onDestroy");

        NotificationsManager.stopHandlingNotifications(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        switch( requestCode ) {
            case REGISTER_USER_REQUEST: {
                if (resultCode == RESULT_OK) {
                    Bundle bundle = data.getExtras();
                    String accessToken = bundle.getString(Globals.TOKENPREF);

                    wamsInit(accessToken);
                    NotificationsManager.handleNotifications(this, Globals.SENDER_ID,
                            GCMHandler.class);
                    setupUI(getString(R.string.title_activity_main), "");

                }
            }
            break;

        }
    }

    public void wamsInit(String accessToken){
        try {
            wamsClient = new MobileServiceClient(
                    Globals.WAMS_URL,
                    Globals.WAMS_API_KEY,
                    this);

            final JsonObject body = new JsonObject();
            body.addProperty("access_token", accessToken);

            new AsyncTask<Void, Void, Void>() {

                Exception mEx;

                @Override
                protected void onPostExecute(Void result){

                    if( mEx != null ) {
                        Toast.makeText(MainActivity.this,
                                mEx.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                protected Void doInBackground(Void... voids) {

                    try {
                        MobileServiceUser mobileServiceUser =
                                wamsClient.login(MobileServiceAuthenticationProvider.Facebook,
                                        body).get();
                        saveUser(mobileServiceUser);
                    } catch(ExecutionException | InterruptedException ex ) {
                        mEx = ex;
                        if( Crashlytics.getInstance() != null)
                            Crashlytics.logException(ex);
                        Log.e(LOG_TAG, ex.getMessage());
                    }

                    return null;
                }
            }.execute();
        } catch(MalformedURLException ex ) {
            if( Crashlytics.getInstance() != null)
                Crashlytics.logException(ex);

            Log.e(LOG_TAG, ex.getMessage() + " Cause: " + ex.getCause());
        }
    }

    private void saveUser(MobileServiceUser mobileServiceUser) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(Globals.WAMSTOKENPREF, mobileServiceUser.getAuthenticationToken());
        editor.putString(Globals.USERIDPREF, mobileServiceUser.getUserId());
        editor.apply();
    }

    //
    // Implementation of IRecyclerClickListener
    //
    @Override
    public void clicked(View view, int position) {
        switch( position ) {
            case 1:
                onDriverClicked(view);
                break;

            case 2:
                onPassengerClicked(view);
                break;
        }
    }

    public void onDriverClicked(View v) {
        Intent intent = new Intent(this, DriverRoleActivity.class);
        startActivity(intent);
    }

    public void onPassengerClicked(View v) {
        try {
            Intent intent = new Intent(this, PassengerRoleActivity.class);
            startActivity(intent);
        } catch(Exception ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
