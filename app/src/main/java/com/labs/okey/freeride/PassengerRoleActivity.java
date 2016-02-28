package com.labs.okey.freeride;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.CallSuper;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.annotation.UiThread;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextSwitcher;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.AlertDialogWrapper;
import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.facebook.AccessToken;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.labs.okey.freeride.adapters.WiFiPeersAdapter2;
import com.labs.okey.freeride.model.GFCircle;
import com.labs.okey.freeride.model.Join;
import com.labs.okey.freeride.model.WifiP2pDeviceUser;
import com.labs.okey.freeride.utils.ClientSocketHandler;
import com.labs.okey.freeride.utils.Globals;
import com.labs.okey.freeride.utils.GroupOwnerSocketHandler;
import com.labs.okey.freeride.utils.IInitializeNotifier;
import com.labs.okey.freeride.utils.IRecyclerClickListener;
import com.labs.okey.freeride.utils.IRefreshable;
import com.labs.okey.freeride.utils.ITrace;
import com.labs.okey.freeride.utils.RoundedDrawable;
import com.labs.okey.freeride.utils.UiThreadExecutor;
import com.labs.okey.freeride.utils.WAMSVersionTable;
import com.labs.okey.freeride.utils.wifip2p.IConversation;
import com.labs.okey.freeride.utils.wifip2p.P2pConversator;
import com.labs.okey.freeride.utils.wifip2p.P2pPreparer;
import com.microsoft.windowsazure.mobileservices.MobileServiceException;
import com.microsoft.windowsazure.mobileservices.table.MobileServiceTable;

import junit.framework.Assert;

import net.steamcrafted.loadtoast.LoadToast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class PassengerRoleActivity extends BaseActivityWithGeofences
    implements ITrace,
        Handler.Callback,
        IRecyclerClickListener,
        IRefreshable,
        WifiP2pManager.ConnectionInfoListener,
        P2pConversator.IPeersChangedListener,
        WAMSVersionTable.IVersionMismatchListener,
        IInitializeNotifier, // used for geo-fence initialization
        android.location.LocationListener,
        // Added for Google Map support within sliding panel
        OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks
{

    private final String                LOG_TAG = getClass().getSimpleName();

    private String                      mUserID;

    final int                           MAKE_PICTURE_REQUEST = 1;
    // handled  in onActivityResult

    private Boolean                     mDriversShown;
    private TextSwitcher                mTextSwitcher;
    private GoogleMap                   mGoogleMap;
    private Circle                      meCircle;

    private MobileServiceTable<Join>    joinsTable;

    private P2pPreparer                 mP2pPreparer;
    private P2pConversator              mP2pConversator;

    private MaterialDialog              mSearchDriverDialog;
    private CountDownTimer              mSearchDriverCountDownTimer;
    private Integer                     mCountDiscoveryFailures = 0;
    private Integer                     mCountDiscoveryTrials = 1;

    private WiFiPeersAdapter2           mDriversAdapter;
    public ArrayList<WifiP2pDeviceUser> mDrivers = new ArrayList<>();

    private Handler handler = new Handler(this);
    public Handler getHandler() {
        return handler;
    }

    private String                      mRideCode;
    private String                      mDriverName;
    private URI                         mPictureURI;
    private UUID                        mFaceId;

    private Location                    mCurrentLocation;
    private long                        mLastLocationUpdateTime = System.currentTimeMillis();

    @Override
    @CallSuper
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FacebookSdk.sdkInitialize(getApplicationContext());
        setContentView(R.layout.activity_passenger);

        setupUI(getString(R.string.title_activity_passenger_role), "");

        wamsInit(false); // without auto-update for this activity

        geoFencesInit();

        joinsTable = getMobileServiceClient().getTable("joins", Join.class);

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        mUserID = sharedPrefs.getString(Globals.USERIDPREF, "");

        if( savedInstanceState != null ) {

            if( savedInstanceState.containsKey(Globals.PARCELABLE_KEY_RIDE_CODE) ) {
                mRideCode = savedInstanceState.getString(Globals.PARCELABLE_KEY_RIDE_CODE);
            }

            if( savedInstanceState.containsKey(Globals.PARCELABLE_KEY_DRIVERS) ) {
                ArrayList<WifiP2pDeviceUser> drivers = savedInstanceState.getParcelableArrayList(Globals.PARCELABLE_KEY_DRIVERS);
                if( drivers != null ) {

                    mDrivers.addAll(drivers);
                    mDriversAdapter.notifyDataSetChanged();
                }
            }
        } else {
            refresh();
        }

    }

    private void geoFencesInit() {
        ListenableFuture<ArrayList<GFCircle>> transformFuture = _initGeofences();
        Futures.addCallback(transformFuture, new FutureCallback<ArrayList<GFCircle>>() {
            @Override
            public void onSuccess(final ArrayList<GFCircle> result) {

                String msg = getGFenceForLocation(mCurrentLocation);
                mTextSwitcher.setText(msg);

                for (GFCircle gfCircle : result) {

                    CircleOptions circleOpt = new CircleOptions()
                            .center(new LatLng(gfCircle.getX(), gfCircle.getY()))
                            .radius(gfCircle.getRadius())
                            .strokeColor(Color.CYAN)
                            .fillColor(Color.TRANSPARENT);
                    mGoogleMap.addCircle(circleOpt);
                }

            }

            @Override
            public void onFailure(Throwable t) {
                if (Crashlytics.getInstance() != null) {
                    Crashlytics.logException(t);
                }
            }
        }, new UiThreadExecutor());
    }

    @UiThread
    protected void setupUI(String title, String subTitle) {
        super.setupUI(title, subTitle);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.gf_map);
        mapFragment.getMapAsync(this);

        RecyclerView driversRecycler = (RecyclerView) findViewById(R.id.recyclerViewDrivers);
        driversRecycler.setHasFixedSize(true);
        driversRecycler.setLayoutManager(new LinearLayoutManager(this));
        driversRecycler.setItemAnimator(new DefaultItemAnimator());

        mDriversAdapter = new WiFiPeersAdapter2(this,
                R.layout.drivers_header,
                R.layout.row_devices,
                mDrivers);
        driversRecycler.setAdapter(mDriversAdapter);

        mDriversShown = false;

        mTextSwitcher = (TextSwitcher) findViewById(R.id.passenger_monitor_text_switcher);
        Animation in = AnimationUtils.loadAnimation(this, R.anim.push_up_in);
        Animation out = AnimationUtils.loadAnimation(this, R.anim.push_up_out);
        mTextSwitcher.setInAnimation(in);
        mTextSwitcher.setOutAnimation(out);
        // Set the initial text without an animation
        String currentMonitorStatus = getString(R.string.geofence_outside_title);
        mTextSwitcher.setCurrentText(currentMonitorStatus);

        Globals.setMonitorStatus(currentMonitorStatus);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {

        mGoogleMap = googleMap;
        mGoogleMap.getUiSettings().setZoomControlsEnabled(true);
        mGoogleMap.getUiSettings().setMyLocationButtonEnabled(true);
    }

    @Override
    @CallSuper
    public void onResume() {
        super.onResume();

        try {
            mCurrentLocation = getCurrentLocation(this);// check Location Permission inside!

            // Global flag 'inGeofenceArea' is updated inside getGFenceForLocation()
            String msg = getGFenceForLocation(mCurrentLocation);
            mTextSwitcher.setCurrentText(msg);

            startLocationUpdates(this, this);

        } catch (SecurityException ex) {

            // Returns true if app has requested this permission previously
            // and the user denied the request
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                mTextSwitcher.setCurrentText(getString(R.string.permission_location_denied));
                Log.d(LOG_TAG, getString(R.string.permission_location_denied));

            } else {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        Globals.LOCATION_PERMISSION_REQUEST);

                // to be continued on onRequestPermissionsResult() in permissionsHandler's activity
            }

        }

    }

    @Override
    @CallSuper
    public void onPause() {

        try {
            stopDiscovery(null);
        } catch (Exception ex) {
            if( Crashlytics.getInstance() != null )
                Crashlytics.log(ex.getMessage());

            if( !ex.getMessage().isEmpty() )
                Log.e(LOG_TAG, ex.getMessage());
        }

        Globals.clearMyPassengerIds();
        Globals.clearMyPassengers();

        try {
            stopLocationUpdates(this);
        } catch (SecurityException sex) {
            Log.e(LOG_TAG, "n/a");
        }

        super.onPause();
    }

    @Override
    @CallSuper
    protected void onStop() {

        // Ride Code will be re-newed on next activity's launch
        mRideCode = null;

        super.onStop();
    }

    // With conjunction of singleTop declaration in manifest
    // used for handle notifications
    @Override
    public void onNewIntent(Intent intent) {

        //Intent intent = getIntent();
        String action = intent.getAction();

        // Is launched from Notification?
        if (action != null &&
                action.equals(Globals.ACTION_CONFIRM) &&
                intent.getType() == null) {

            if (intent.hasExtra(Globals.NOTIFICATION_ID_EXTRA)) {
                int notificationID = intent.getIntExtra(Globals.NOTIFICATION_ID_EXTRA, -1);
                cancelNotification();

                clicked(null, -1); // "simulate" confirmation dialog
            }
        }

        super.onNewIntent(intent);
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        try {

            switch (requestCode) {

                case Globals.LOCATION_PERMISSION_REQUEST: {

                    if (grantResults.length > 0
                            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                        mCurrentLocation = getCurrentLocation(this);
                        startLocationUpdates(this, this);
                    }
                }
                break;

                case Globals.CAMERA_PERMISSION_REQUEST: {
                    if (grantResults.length > 0
                            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        onCameraCVInternal(null);
                    } else {
                        mTextSwitcher.setCurrentText(getString(R.string.permission_camera_denied));
                        Log.d(LOG_TAG, getString(R.string.permission_camera_denied));
                    }
                }
                break;

            }

        } catch (Exception ex) {
            Log.e(LOG_TAG, ex.getMessage());

        }

    }

    //
    // IInitializeNotifier implemntation
    //
    @Override
    public void initialized(Object what) {

        String msg = getGFenceForLocation(mCurrentLocation);
        mTextSwitcher.setText(msg);
    }

    //
    // Implementation of LocationListener
    //
    @Override
    public void onLocationChanged(Location location) {

        if (!Globals.DEBUG_WITHOUT_GEOFENCES) {

            if (!isAccurate(location)) {
                Log.d(LOG_TAG, getString(R.string.location_inaccurate));
                return;
            }

            mCurrentLocation = location;

            LatLng latLng = new LatLng(location.getLatitude(),
                    location.getLongitude());
            // Showing the current location in Google Map
            if( meCircle != null )
                meCircle.remove();
            CircleOptions circleOpt = new CircleOptions()
                    .center(latLng)
                    .radius(10)
                    .strokeColor(Color.CYAN)
                    .strokeWidth(1)
                    .fillColor(Color.RED);
            meCircle = mGoogleMap.addCircle(circleOpt);
            mGoogleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
            mGoogleMap.animateCamera(CameraUpdateFactory.zoomTo(17));

            // Global flag 'inGeofenceArea' is updated inside getGFenceForLocation()
            String msg = getGFenceForLocation(location);

            TextView textView = (TextView) mTextSwitcher.getCurrentView();
            String msgRepeat = textView.getText().toString();

            if (Globals.isInGeofenceArea()) {
                mLastLocationUpdateTime = System.currentTimeMillis();

                // Send notification and log the transition details.
                if (Globals.getRemindGeofenceEntrance()) {

                    Globals.clearRemindGeofenceEntrance();

                    sendNotification(msg, PassengerRoleActivity.class);
                }

            } else {
                long elapsed = System.currentTimeMillis() - mLastLocationUpdateTime;
                if (mLastLocationUpdateTime != 0 // for the first-time
                        && elapsed < Globals.GF_OUT_TOLERANCE) {

                    Globals.setInGeofenceArea(true);

                    msg = msgRepeat;
                }

            }

            mTextSwitcher.setCurrentText(msg);
        }
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == MAKE_PICTURE_REQUEST) {
            switch (resultCode) {

                case RESULT_OK: {

                    if (data != null) {
                        Bundle extras = data.getExtras();

                        FloatingActionButton passengerPicture = (FloatingActionButton) this.findViewById(R.id.join_ride_button);
                        passengerPicture.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.green)));

                        Bitmap bmp = extras.getParcelable(getString(R.string.detection_face_bitmap));
                        if (bmp != null) {
                            Drawable drawable = new BitmapDrawable(this.getResources(), bmp);

                            drawable = RoundedDrawable.fromDrawable(drawable);
                            ((RoundedDrawable) drawable)
                                    .setCornerRadius(Globals.PICTURE_CORNER_RADIUS)
                                    .setBorderColor(Color.WHITE)
                                    .setBorderWidth(0)
                                    .setOval(true);

                            passengerPicture.setImageDrawable(drawable);
                        }

                        mPictureURI = (URI) extras.getSerializable(getString(R.string.detection_face_uri));
                        mFaceId = (UUID) extras.getSerializable(getString(R.string.detection_face_id));

                        mTextSwitcher.setText(getString(R.string.instruction_make_additional_selfies));
                    }


                }
                break;

                case RESULT_CANCELED: { // Distinguishing between successful connection
                    // and just pressing back from there.
                    refresh();
                }
                break;

                case RESULT_FIRST_USER: { // Any exceptions were occurred inside CameraCV Activity

                    if (data != null) {
                        Bundle extras = data.getExtras();
                        String message = extras.getString(getString(R.string.detection_exception));
                        if (message == null)
                            message = getString(R.string.detection_general_exception);

                        new MaterialDialog.Builder(this)
                                .content(message)
                                .title(R.string.detection_error)
                                .iconRes(R.drawable.ic_exclamation)
                                .positiveText(android.R.string.ok)
                                .show();
                    }
                }
                break;

            }
        }
    }

    @UiThread
    private void showCountDownDialog() {
        try {

//            final BallView waitView = (BallView)findViewById(R.id.wait_search_driver);
//            waitView.setVisibility(View.VISIBLE);

            mSearchDriverDialog = new MaterialDialog.Builder(this)
                    .title(R.string.passenger_progress_dialog)
                    .content(R.string.please_wait)
                    .iconRes(R.drawable.ic_wait)
                    .cancelable(false)
                    .autoDismiss(false)
                            //.progress(false, Globals.PASSENGER_DISCOVERY_PERIOD, true)
                    .progress(true, 0)
                    .negativeText(android.R.string.cancel)
                    .onNegative(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            dialog.dismiss();
                            showRideCodePane(R.string.ride_code_dialog_content,
                                    Color.BLACK);
                        }
                    })
                    .show();

            if (mSearchDriverCountDownTimer == null) {

                mSearchDriverCountDownTimer = new CountDownTimer(Globals.PASSENGER_DISCOVERY_PERIOD * 1000, 1000) {

                    public void onTick(long millisUntilFinished) {

                        Log.d(LOG_TAG,
                                String.format("CountDown tick. Remains %d sec. Drivers size: %d",
                                        millisUntilFinished, mDrivers.size()));

                        if (mDrivers.size() != 0) {
                            this.cancel();
                            //waitView.setVisibility(View.GONE);
                            mSearchDriverDialog.dismiss();

                            Log.d(LOG_TAG, "Cancelling timer");
                        } else {
                            if (!mSearchDriverDialog.isIndeterminateProgress())
                                mSearchDriverDialog.incrementProgress(1);
                        }
                    }

                    public void onFinish() {

                        try {
                            mSearchDriverDialog.dismiss();
                            //waitView.setVisibility(View.GONE);
                        } catch (IllegalArgumentException ex) {
                            // Safely dismiss when called due to
                            // 'Not attached to window manager'.
                            // In this case the activity just was passed by
                            // to some other activity
                        }

                        if (mDrivers.size() == 0) {

                            if (mCountDiscoveryTrials++ > Globals.MAX_DISCOVERY_TRIALS) {
                                showRideCodePane(R.string.ride_code_dialog_content,
                                        Color.BLACK);
                                mCountDiscoveryTrials = 1;
                            } else {
                                refresh();
                            }
                        }

                    }
                };
            }

            mSearchDriverCountDownTimer.start();

        } catch (Exception ex) {
            if (Crashlytics.getInstance() != null)
                Crashlytics.logException(ex);

            Log.e(LOG_TAG, ex.getMessage());
        }

    }

    @UiThread
    private void showRideCodePane(@StringRes int contentStringResId,
                                  @ColorInt int contentColor) {

        try {
            String dialogContent = getString(contentStringResId);

            new MaterialDialog.Builder(this)
                    .onNegative((new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            refresh();
                        }
                    }))
                    .onNeutral((new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {

                        }
                    }))
                    .title(R.string.ride_code_title)
                    .content(dialogContent)
                    .positiveText(android.R.string.ok)
                    .neutralText(R.string.code_try_later)
                    .negativeText(R.string.code_retry_action)
                    .contentColor(contentColor)
                    .inputType(InputType.TYPE_NUMBER_VARIATION_NORMAL | InputType.TYPE_CLASS_NUMBER)
                    .inputRange(Globals.RIDE_CODE_INPUT_LENGTH, Globals.RIDE_CODE_INPUT_LENGTH)
                    .input(R.string.ride_code_hint,
                            R.string.ride_code_refill,
                            new MaterialDialog.InputCallback() {
                                @Override
                                public void onInput(MaterialDialog dialog, CharSequence input) {
                                    mRideCode = input.toString();
                                    onSubmitCode();
                                }
                            }

                    ).show();
        } catch (Exception ex) {

            Log.e(LOG_TAG, ex.getMessage());
        }
    }

    public void onCameraCV(View view) {

        try {
            checkCameraAndStoragePermissions();
            onCameraCVInternal(view);
        } catch (SecurityException ex) {

            // Returns true if app has requested this permission previously 
            // and the user denied the request 
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {

                Toast.makeText(this, getString(R.string.permission_camera_denied), Toast.LENGTH_LONG).show();
                Log.d(LOG_TAG, getString(R.string.permission_camera_denied));

            } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Toast.makeText(this, getString(R.string.permission_storage_denied), Toast.LENGTH_LONG).show();
                Log.d(LOG_TAG, getString(R.string.permission_storage_denied));
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        Globals.CAMERA_PERMISSION_REQUEST);
            }
        }
    }

    private void onCameraCVInternal(View v) {

        // Only allow participation request from monitored areas
        if (!Globals.isInGeofenceArea()) {
            new MaterialDialog.Builder(this)
                    .title(R.string.geofence_outside_title)
                    .content(R.string.geofence_outside)
                    .positiveText(R.string.geofence_positive_answer)
                    .negativeText(R.string.geofence_negative_answer)
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            Globals.setRemindGeofenceEntrance();
                        }
                    })
                    .show();

            return;
        }

        final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean bShowSelfieDescription = sharedPrefs.getBoolean(Globals.SHOW_SELFIE_DESC, true);

        if (bShowSelfieDescription) {

            new MaterialDialog.Builder(this)
                    .title(getString(R.string.selfie))
                    .content(getString(R.string.selfie_content))
                    .iconRes(R.drawable.ic_smart_selfie)
                    .positiveText(android.R.string.ok)
                    .negativeText(R.string.selfie_desc_not_show_again)
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {

                            Intent intent = new Intent(PassengerRoleActivity.this,
                                    CameraCVActivity.class);
                            //intent.putExtra("RIDE_CODE", Globals.getRideCode());
                            startActivityForResult(intent, MAKE_PICTURE_REQUEST);
                        }

                        @Override
                        public void onNegative(MaterialDialog dialog) {

                            SharedPreferences.Editor editor = sharedPrefs.edit();
                            editor.putBoolean(Globals.SHOW_SELFIE_DESC, false);
                            editor.apply();

                            Intent intent = new Intent(PassengerRoleActivity.this,
                                    CameraCVActivity.class);
                            startActivityForResult(intent, MAKE_PICTURE_REQUEST);
                        }
                    })
                    .show();
        } else {
            Intent intent = new Intent(this, CameraCVActivity.class);
            startActivityForResult(intent, MAKE_PICTURE_REQUEST);

        }

    }

    //
    // Implementation of IVersionMismatchListener
    //
    @Override
    public void mismatch(int majorLast, int minorLast, final String url) {
        try {
            new MaterialDialog.Builder(this)
                    .title(getString(R.string.new_version_title))
                    .content(getString(R.string.new_version_conent))
                    .positiveText(android.R.string.yes)
                    .negativeText(android.R.string.no)
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
        } catch (MaterialDialog.DialogException e) {
            // better that catch the exception here would be use handle to send events the activity
        }

    }

    @Override
    public void connectionFailure(Exception ex) {
        if (ex != null) {

            View v = findViewById(R.id.drawer_layout);
            Snackbar.make(v, ex.getMessage(), Snackbar.LENGTH_LONG);
        }
    }

    @Override
    public void match() {

    }

    //
    // Implementation of IRecyclerClickListener
    //
    @Override
    public void clicked(View view, int position) {

        if (!Globals.isInGeofenceArea()) {
            new MaterialDialog.Builder(this)
                    .title(R.string.geofence_outside_title)
                    .content(R.string.geofence_outside)
                    .positiveText(R.string.geofence_positive_answer)
                    .negativeText(R.string.geofence_negative_answer)
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            Globals.setRemindGeofenceEntrance();
                        }
                    })
                    .show();
        } else {

            if (position >= 0
                    && mRideCode == null) {
                // Ride code was stored if the activity is invoked from notification
                // In this case the position is -1 because this
                // function is called manually (from within onNewIntent())
                Assert.assertNotNull(mDrivers);

                WifiP2pDeviceUser driverDevice = mDrivers.get(position);
                Assert.assertNotNull(driverDevice);

                mRideCode = driverDevice.getRideCode();
                mDriverName = driverDevice.getUserName();
            }

            DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialogInterface, int which) {
                    if (which == DialogInterface.BUTTON_POSITIVE) {

                        //findViewById(R.id.join_ride_button).setVisibility(View.INVISIBLE);

                        FloatingActionButton passengerPicture = (FloatingActionButton) findViewById(R.id.join_ride_button);
                        passengerPicture.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.ColorAccent)));
                        Drawable drawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_action_camera, null);
                        passengerPicture.setImageDrawable(drawable);

                        onSubmitCode();
                    }
                }
            };

            StringBuilder sb = new StringBuilder(getString(R.string.passenger_confirm));
            if (mDriverName != null) {
                sb.append(" ");
                getString(R.string.with);
                sb.append(" ");
                sb.append(mDriverName);
            }
            sb.append("?");

            new AlertDialogWrapper.Builder(this)
                    .setTitle(sb.toString())
                    .setNegativeButton(android.R.string.no, dialogClickListener)
                    .setPositiveButton(android.R.string.yes, dialogClickListener)
                    .show();
        }

    }

    private Runnable thanksRunnable = new Runnable() {
        @Override
        public void run() {
            new MaterialDialog.Builder(PassengerRoleActivity.this)
                    .title(R.string.thanks)
                    .content(R.string.confirmation_accepted)
                    .cancelable(false)
                    .positiveText(android.R.string.ok)
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            //finish();
                        }
                    })
                    .show();
        }
    };

    public void onSubmitCode() {

        // Only allow participation request from monitored areas
        if (!Globals.isInGeofenceArea()) {
            new MaterialDialog.Builder(this)
                    .title(R.string.geofence_outside_title)
                    .content(R.string.geofence_outside)
                    .positiveText(R.string.geofence_positive_answer)
                    .negativeText(R.string.geofence_negative_answer)
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            Globals.setRemindGeofenceEntrance();
                        }
                    })
                    .show();

            return;
        }

        final String android_id = Settings.Secure.getString(this.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        final View v = findViewById(R.id.passenger_internal_layout);

        new AsyncTask<Void, Void, Void>() {

            Exception mEx;
            LoadToast lt;

            @Override
            protected void onPreExecute() {

                lt = new LoadToast(PassengerRoleActivity.this);
                lt.setText(getString(R.string.processing));
                Display display = getWindow().getWindowManager().getDefaultDisplay();
                Point size = new Point();
                display.getSize(size);
                lt.setTranslationY(size.y / 2);
                lt.show();

            }

            @Override
            protected void onPostExecute(Void result) {

                cancelNotification();

                // Prepare to play sound loud :)
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                int sb2value = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, sb2value / 2, 0);

                CustomEvent confirmEvent = new CustomEvent(getString(R.string.passenger_confirmation_answer_name));
                confirmEvent.putCustomAttribute("User", getUser().getFullName());

                if (mEx != null) {

                    confirmEvent.putCustomAttribute("Error", 0);

                    try {
                        MobileServiceException mse = (MobileServiceException) mEx.getCause();
                        int responseCode = 0;
                        if (mse.getCause() instanceof UnknownHostException) {
                            responseCode = 503; // Some artificially: usually 503 means
                            // 'Service Unavailable'.
                            // To this extent, we mean 'Connection lost'
                        } else {

                            responseCode = mse.getResponse().getStatus().getStatusCode();
                        }

                        switch (responseCode) {

                            case 403: { // HTTP 'Forbidden' means than IDs of the
                                // driver and passenger are same
                                showRideCodePane(R.string.ride_same_ids,
                                        Color.RED);

                                lt.error();
                                beepError.start();
                            }
                            break;

                            case 404: { // HTTP 'Not found' means 'no such ride code'
                                // i.e.
                                // try again with appropriate message
                                showRideCodePane(R.string.ride_code_wrong,
                                        Color.RED);

                                lt.error();
                                beepError.start();
                            }
                            break;

                            case 409: {// HTTP 'Conflict'
                                // picture required
                                // Ride code was successfully validated,
                                // but selfie is required

                                lt.success();

                                onCameraCV(null);
                            }
                            break;

                            case 503: { // HTTP 'Service Unavailable' interpreted as 'Connection Lost'
                                // Try again
                                showRideCodePane(R.string.connection_lost,
                                        Color.RED);
                                lt.error();
                                beepError.start();
                            }
                            break;

                            default:
                                lt.error();
                                Toast.makeText(PassengerRoleActivity.this,
                                        mEx.getMessage(),
                                        Toast.LENGTH_LONG).show();
                                break;
                        }
                    } catch (Exception ex) {
                        if (Crashlytics.getInstance() != null)
                            Crashlytics.logException(ex);

                        if (!ex.getMessage().isEmpty())
                            Log.e(LOG_TAG, ex.getMessage());
                    }

                } else {

                    confirmEvent.putCustomAttribute("Success", 1);

                    lt.success();
                    beepSuccess.start();

                    getHandler().postDelayed(thanksRunnable, 1500);

                }

                Answers.getInstance().logCustom(confirmEvent);

            }

            @Override
            protected Void doInBackground(Void... voids) {

                try {
                    Join _join = new Join();
                    _join.setWhenJoined(new Date());
                    if (mPictureURI != null && !mPictureURI.toString().isEmpty())
                        _join.setPictureURL(mPictureURI.toString());
                    if (mFaceId != null && !mFaceId.toString().isEmpty())
                        _join.setFaceId(mFaceId.toString());
                    _join.setRideCode(mRideCode);
                    String currentGeoFenceName = Globals.get_currentGeoFenceName();
                    _join.setGFenceName(currentGeoFenceName);
                    _join.setDeviceId(android_id);

                    try {
                        Location loc = getCurrentLocation(PassengerRoleActivity.this);
                        if (loc != null) {
                            _join.setLat((float) loc.getLatitude());
                            _join.setLon((float) loc.getLongitude());
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, e.getMessage());
                    }
                    // The rest of params are set within WAMS insert script

                    joinsTable.insert(_join).get();

                } catch (ExecutionException | InterruptedException ex) {

                    mEx = ex;
                    if (Crashlytics.getInstance() != null)
                        Crashlytics.logException(ex);

                    Log.e(LOG_TAG, ex.getMessage());
                }

                return null;
            }
        }.execute();
    }

    //
    // Implementation of IRefreshable
    //
    @Override
    @UiThread
    public void refresh() {
        mDrivers.clear();
        mDriversAdapter.notifyDataSetChanged();

        final ImageButton btnRefresh = (ImageButton) findViewById(R.id.btnRefresh);
        if (btnRefresh != null) // This may happens because
            // the button is actually created by adapter
            btnRefresh.setVisibility(View.GONE);
        final ProgressBar progress_refresh = (ProgressBar) findViewById(R.id.progress_refresh);
        if (progress_refresh != null)
            progress_refresh.setVisibility(View.VISIBLE);

        try {
            stopDiscovery(new Runnable() {
                @Override
                public void run() {

                    startDiscovery(PassengerRoleActivity.this,
                            getUser().getRegistrationId(),
                            ""); // empty ride code!

                    getHandler().postDelayed(
                            new Runnable() {
                                @Override
                                public void run() {
                                    if (btnRefresh != null)
                                        btnRefresh.setVisibility(View.VISIBLE);
                                    if (progress_refresh != null)
                                        progress_refresh.setVisibility(View.GONE);
                                }
                            },
                            Globals.PASSENGER_DISCOVERY_PERIOD * 1000);

                    showCountDownDialog();

                }
            });

        } catch (Exception ex) {
            if (Crashlytics.getInstance() != null)
                Crashlytics.log(ex.getLocalizedMessage());

            Log.e(LOG_TAG, ex.getLocalizedMessage());
        }

    }

    private void startDiscovery(final P2pConversator.IPeersChangedListener peersListener,
                                final String userID,
                                final String rideCode) {

        mP2pPreparer = new P2pPreparer(this);
        mP2pPreparer.prepare(new P2pPreparer.P2pPreparerListener() {
            @Override
            public void prepared() {
                Map<String, String> record = new HashMap<>();
                record.put(Globals.TXTRECORD_PROP_PORT, Globals.SERVER_PORT);
                if (!rideCode.isEmpty())
                    record.put(Globals.TXTRECORD_PROP_RIDECODE, rideCode);
                record.put(Globals.TXTRECORD_PROP_USERID, userID);

                mP2pConversator = new P2pConversator(PassengerRoleActivity.this,
                        (IConversation) mP2pPreparer,
                        getHandler());
                mP2pConversator.startConversation(record, peersListener);

            }

            @Override
            public void interrupted() {

            }
        });
    }

    private void stopDiscovery(final Runnable r) throws Exception {
        if (mP2pPreparer == null && r != null) {
            r.run();
            return;
        }

        mP2pPreparer.undo(new Runnable() {
            @Override
            public void run() {
                try {
                    if (mP2pConversator != null) {
                        mP2pConversator.stopConversation();
                    }

                    if (r != null)
                        r.run();

                } catch (Exception ex) {
                    Log.e(LOG_TAG, ex.getLocalizedMessage());
                }
            }
        });
    }

    @Override
    public boolean handleMessage(Message msg) {
        String strMessage;

        switch (msg.what) {
            case Globals.TRACE_MESSAGE:
                Bundle bundle = msg.getData();
                strMessage = bundle.getString("message");
                trace(strMessage);
                break;

            case Globals.MESSAGE_READ:
                byte[] buffer = (byte[]) msg.obj;
                strMessage = new String(buffer);
                trace(strMessage);
                break;

            case Globals.MESSAGE_DISCOVERY_FAILED:
                if (mSearchDriverDialog != null && mSearchDriverDialog.isShowing()) {
                    mSearchDriverDialog.dismiss();
                    mSearchDriverCountDownTimer.cancel();

                    if (mCountDiscoveryFailures++ < Globals.MAX_ALLOWED_DISCOVERY_FAILURES) {
                        mSearchDriverDialog = null;
                        refresh();
                    } else {
                        mCountDiscoveryFailures = 0;
                        showRideCodePane(R.string.discovery_failure,
                                Color.RED);
                    }
                }
                break;
        }

        return true;

    }

    @Override
    public void trace(final String status) {

    }

    @Override
    public void alert(String message, final String actionIntent) {
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialogInterface, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    startActivity(new Intent(actionIntent));
                }
            }
        };

        new AlertDialogWrapper.Builder(this)
                .setTitle(message)
                .setNegativeButton(android.R.string.no, dialogClickListener)
                .setPositiveButton(android.R.string.yes, dialogClickListener)
                .show();
    }

    //
    // Implementations of P2pConversator.IPeersChangedListener
    //
    @Override
    public void addDeviceUser(final WifiP2pDeviceUser device) {

        if (device.getRideCode() == null)
            return;

        String remoteUserID = device.getUserId();
        if (remoteUserID == null || remoteUserID.isEmpty()) {
            // remote user id was not transmitted
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mDriversAdapter.add(device);
                    mDriversAdapter.notifyDataSetChanged();
                }
            });
        } else {

            String[] tokens = remoteUserID.split(":");
            Assert.assertTrue(tokens.length == 2);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mDriversAdapter.add(device);
                    mDriversAdapter.notifyDataSetChanged();

                    // remove 'type code' menu item
                    mDriversShown = true;
                }
            });

            AccessToken fbAccessToken = AccessToken.getCurrentAccessToken();
            GraphRequest request = GraphRequest.newGraphPathRequest(
                    fbAccessToken,
                    tokens[1],
                    new GraphRequest.Callback() {
                        @Override
                        public void onCompleted(GraphResponse response) {

                            try {

                                JSONObject object = response.getJSONObject();
                                if (response.getError() == null) {
                                    String userName = (String) object.get("name");
                                    device.setUserName(userName);

                                    mDriversAdapter.replaceItem(device);
                                    mDriversAdapter.notifyDataSetChanged();
                                } else {
                                    if (Crashlytics.getInstance() != null)
                                        Crashlytics.log(response.getError().getErrorMessage());

                                    Log.e(LOG_TAG, response.getError().getErrorMessage());
                                }

                            } catch (JSONException e) {
                                Log.e(LOG_TAG, e.getLocalizedMessage());
                            }
                        }
                    });

            Bundle parameters = new Bundle();
            parameters.putString("fields", "id,name");
            request.setParameters(parameters);
            request.executeAsync();
        }
    }


    //
    // Implementation of WifiP2pManager.ConnectionInfoListener
    //
    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo p2pInfo) {

        Thread handler = null;

        if (p2pInfo.isGroupOwner) {

            try {
                handler = new GroupOwnerSocketHandler(this.getHandler());
                handler.start();
            } catch (IOException e) {
                trace("Failed to create a server thread - " + e.getMessage());
            }

        } else {

            handler = new ClientSocketHandler(
                    this.getHandler(),
                    p2pInfo.groupOwnerAddress,
                    this,
                    "!!!Message from PASSENGER!!!");
            handler.start();
            trace("Client socket opened.");

//            android.os.Handler h = new android.os.Handler();
//
//            Runnable r = new Runnable() {
//                @Override
//                public void run() {
//
//                    new WiFiUtil.ClientAsyncTask(context, p2pInfo.groupOwnerAddress,
//                                        "From client").execute();
//                }
//            };
//
//            h.postDelayed(r, 2000); // let to server to open the socket in advance

        }

    }

}
