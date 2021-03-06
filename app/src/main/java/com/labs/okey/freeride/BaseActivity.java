package com.labs.okey.freeride;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.UiThread;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.labs.okey.freeride.adapters.DrawerAccountAdapter;
import com.labs.okey.freeride.model.User;
import com.labs.okey.freeride.utils.WAMSVersionTable;
import com.labs.okey.freeride.utils.wamsUtils;
import com.microsoft.windowsazure.mobileservices.MobileServiceClient;

import java.net.MalformedURLException;
import java.util.StringTokenizer;

/**
 * Created by Oleg on 22-Aug-15.
 */
public class BaseActivity extends AppCompatActivity
        implements GoogleApiClient.OnConnectionFailedListener{

    private static final String LOG_TAG = "FR.baseActivity";
    static final int REQUEST_CODE_RECOVER_PLAY_SERVICES = 1001;

    protected String[] mDrawerTitles;
    protected int DRAWER_ICONS[] = {
            R.drawable.ic_action_start,
            R.drawable.ic_action_myrides,
            R.drawable.ic_action_settings,
            R.drawable.ic_action_tutorial
    };

    RecyclerView mDrawerRecyclerView;

    private User mUser;
    public User getUser() { return mUser; }

    private GoogleApiClient mGoogleApiClient;
    public GoogleApiClient getGoogleApiClient() { return mGoogleApiClient; }

    private MobileServiceClient wamsClient;
    public Boolean isWamsInitialized() { return wamsClient != null; }
    public MobileServiceClient getMobileServiceClient() { return wamsClient; }

    private WAMSVersionTable wamsVersionTable;

    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout mDrawerLayout;

    MediaPlayer beepSuccess;
    MediaPlayer beepError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        buildGoogleApiClient();

        // Connect the Google API client.
        if( mGoogleApiClient != null )
            mGoogleApiClient.connect();


        beepSuccess = MediaPlayer.create(this, R.raw.success);
        beepError = MediaPlayer.create(this, R.raw.error);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        if (mDrawerToggle != null)
            mDrawerToggle.syncState();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mDrawerToggle != null && mDrawerToggle.onOptionsItemSelected(item))
            return true;

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

        GooglePlayServicesUtil.getErrorDialog(connectionResult.getErrorCode(),
                                this, REQUEST_CODE_RECOVER_PLAY_SERVICES).show();
    }

    @Override
    protected void onDestroy() {
        // Disconnecting the Google API client invalidates it.
        if( mGoogleApiClient != null )
            mGoogleApiClient.disconnect();

        super.onDestroy();
    }

    protected synchronized void buildGoogleApiClient() {
        try {

            GoogleApiClient.OnConnectionFailedListener connectionFailedListener = this;

            GoogleApiClient.Builder builder =
                    new GoogleApiClient.Builder(this)
                            .addApi(LocationServices.API);

            if( this instanceof GoogleApiClient.ConnectionCallbacks) {
                GoogleApiClient.ConnectionCallbacks callbacksImplementer =
                        (GoogleApiClient.ConnectionCallbacks)this;
                builder.addConnectionCallbacks(callbacksImplementer);
            }

            builder.addOnConnectionFailedListener(connectionFailedListener);

            mGoogleApiClient = builder.build();

        } catch (Exception ex) {
            Log.e(LOG_TAG, ex.getMessage());
        }

    }

    private static int[] NETWORK_TYPES = {ConnectivityManager.TYPE_WIFI,
            ConnectivityManager.TYPE_ETHERNET};
    public boolean isConnectedToNetwork() {
        ConnectivityManager connManager = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = connManager.getActiveNetworkInfo();

        return (info != null && info.isConnectedOrConnecting());
    }

    @UiThread
    protected void setupUI(String title, String subTitle) {
        Toolbar toolbar = (Toolbar) findViewById(R.id.fastride_toolbar);
        if( toolbar != null ) {
            setSupportActionBar(toolbar);

            ActionBar actionBar = getSupportActionBar();
            if( actionBar != null ) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setHomeButtonEnabled(true);
                actionBar.setDisplayShowTitleEnabled(true);

                actionBar.setTitle(title);
                actionBar.setSubtitle(subTitle);
            }
        }

        mDrawerRecyclerView = (RecyclerView) findViewById(R.id.left_drawer);
        mDrawerRecyclerView.setHasFixedSize(true);
        mDrawerRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mDrawerRecyclerView.setItemAnimator(new DefaultItemAnimator());

        mUser = User.load(this);

        mDrawerTitles = getResources().getStringArray(R.array.drawers_array_drawer);
        DrawerAccountAdapter drawerRecyclerAdapter =
                new DrawerAccountAdapter(this,
                        mDrawerTitles,
                        DRAWER_ICONS,
                        mUser.getFirstName() + " " + mUser.getLastName(),
                        mUser.getEmail(),
                        mUser.getPictureURL());
        mDrawerRecyclerView.setAdapter(drawerRecyclerAdapter);

        final Context ctx = this;

        LinearLayout aboutLayout = (LinearLayout) findViewById(R.id.about_row);
        aboutLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ctx, AboutActivity.class);
                ctx.startActivity(intent);
            }
        });

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // set a custom shadow that overlays the main content when the drawer opens
            mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        }

        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
                R.string.drawer_open, R.string.drawer_close);

        mDrawerToggle.setDrawerIndicatorEnabled(true);
        mDrawerLayout.setDrawerListener(mDrawerToggle);

    }

    public void wamsInit(Boolean withAutoUpdate) {

        try {
            wamsClient = wamsUtils.init(this);

            if (withAutoUpdate) {
                startAutoUpdate();
            }
        } catch(MalformedURLException ex ) {
            Log.e(LOG_TAG, ex.getMessage() + " Cause: " + ex.getCause());
        }

//        try {
//            wamsClient = new MobileServiceClient(
//                    Globals.WAMS_URL,
//                    Globals.WAMS_API_KEY,
//                    this);
//
//            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
//            String userID = sharedPrefs.getString(Globals.USERIDPREF, "");
//            MobileServiceUser wamsUser = new MobileServiceUser(userID);
//
//            String token = sharedPrefs.getString(Globals.WAMSTOKENPREF, "");
//            // According to this article (http://www.thejoyofcode.com/Setting_the_auth_token_in_the_Mobile_Services_client_and_caching_the_user_rsquo_s_identity_Day_10_.aspx)
//            // this should be JWT token, so use WAMS_TOKEN
//            wamsUser.setAuthenticationToken(token);
//
//            wamsClient.setCurrentUser(wamsUser);
//
//            if( withAutoUpdate ) {
//                startAutoUpdate();
//            }
//
//        } catch(MalformedURLException ex ) {
//            Log.e(LOG_TAG, ex.getMessage() + " Cause: " + ex.getCause());
//        }

    }

    private void startAutoUpdate() {
        try {

            WAMSVersionTable.IVersionMismatchListener listener = null;
            if (this instanceof WAMSVersionTable.IVersionMismatchListener) {
                listener = (WAMSVersionTable.IVersionMismatchListener) this;
            }
            wamsVersionTable = new WAMSVersionTable(this, listener);
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            String packageVersionName = info.versionName;
            if (!packageVersionName.isEmpty()) {

                StringTokenizer tokens = new StringTokenizer(packageVersionName, ".");
                if (tokens.countTokens() > 0) {
                    int majorPackageVersion = Integer.parseInt(tokens.nextToken());
                    int minorPackageVersion = Integer.parseInt(tokens.nextToken());
                    wamsVersionTable.compare(majorPackageVersion, minorPackageVersion);
                }
            }

        } catch (PackageManager.NameNotFoundException ex) {

            Log.e(LOG_TAG, ex.getMessage());
        }
    }


}
