package com.labs.okey.freeride;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationProvider;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.Pair;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextSwitcher;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.github.brnunes.swipeablerecyclerview.SwipeableRecyclerViewTouchListener;
import com.labs.okey.freeride.adapters.PassengersAdapter;
import com.labs.okey.freeride.model.Join;
import com.labs.okey.freeride.model.PassengerFace;
import com.labs.okey.freeride.model.Ride;
import com.labs.okey.freeride.model.User;
import com.labs.okey.freeride.model.WifiP2pDeviceUser;
import com.labs.okey.freeride.utils.ClientSocketHandler;
import com.labs.okey.freeride.utils.Globals;
import com.labs.okey.freeride.utils.GroupOwnerSocketHandler;
import com.labs.okey.freeride.utils.IInitializeNotifier;
import com.labs.okey.freeride.utils.IMessageTarget;
import com.labs.okey.freeride.utils.IRecyclerClickListener;
import com.labs.okey.freeride.utils.IRefreshable;
import com.labs.okey.freeride.utils.ITrace;
import com.labs.okey.freeride.utils.IUploader;
import com.labs.okey.freeride.utils.RoundedDrawable;
import com.labs.okey.freeride.utils.WAMSVersionTable;
import com.labs.okey.freeride.utils.WiFiUtil;
import com.labs.okey.freeride.utils.faceapiUtils;
import com.labs.okey.freeride.utils.wamsAddAppeal;
import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.authentication.MobileServiceUser;
import com.microsoft.windowsazure.mobileservices.table.MobileServiceTable;

import junit.framework.Assert;

import net.steamcrafted.loadtoast.LoadToast;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DriverRoleActivity extends BaseActivityWithGeofences
        implements ITrace,
        IMessageTarget,
        Handler.Callback,
        IRefreshable,
        IRecyclerClickListener,
        WiFiUtil.IPeersChangedListener,
        WifiP2pManager.PeerListListener,
        WifiP2pManager.ConnectionInfoListener,
        WAMSVersionTable.IVersionMismatchListener,
        IUploader,
        IInitializeNotifier, // used for geo-fence initialization
        android.location.LocationListener
{

    private static final String                 LOG_TAG = "FR.Driver";

    PassengersAdapter                           mPassengersAdapter;
    SwipeableRecyclerViewTouchListener          mSwipeTouchListener;
    private ArrayList<User>                     mPassengers = new ArrayList<>();
    private int                                 mLastPassengersLength;

    private ArrayList<Integer>                  mCapturedPassengersIDs = new ArrayList<>();

    WiFiUtil                                    mWiFiUtil;

    TextSwitcher                                mTextSwitcher;
    RecyclerView                                mPeersRecyclerView;
    ImageView                                   mImageTransmit;

    private Location                            mCurrentLocation;
    private long                                mLastLocationUpdateTime;

    String                                      mCarNumber;
    Uri                                         mUriPhotoAppeal;
    Ride                                        mCurrentRide;
    int                                         mEmojiID;

    private MobileServiceTable<Ride>            mRidesTable;
    private String                              mRideCode;

    ScheduledExecutorService                    mCheckPasengersTimer = Executors.newScheduledThreadPool(1);
    ScheduledFuture<?>                          mCheckPassengerTimerResult;

//    private Boolean                             mRideCodeUploaded = false;
    private AtomicBoolean                       _mRideCodeUploaded = new AtomicBoolean(false);
//    private final Object                        mRideCodeUploadedLock = new Object();
//    private Boolean isRideCodeUploaded() {
//        synchronized (mRideCodeUploadedLock) {
//            return mRideCodeUploaded;
//        }
//    }
//    private void setRideCodeUploaded(Boolean value) {
//        synchronized (mRideCodeUploadedLock) {
//            mRideCodeUploaded = value;
//        }
//    }

    AsyncTask<Void, Void, Void>                 mWAMSUploadRideCodeTask =  new AsyncTask<Void, Void, Void>() {

                Exception mEx;

                @Override
                protected void onPostExecute(Void result) {

                    if (mEx != null) {

                        Toast.makeText(DriverRoleActivity.this,
                                        mEx.getMessage(), Toast.LENGTH_LONG).show();
                    }
                    else {
                        View v = findViewById(R.id.passenger_snackbar);
                        if( v != null )
                            Snackbar.make(v, R.string.ride_uploaded, Snackbar.LENGTH_SHORT)
                                    .show();
                    }
                }

                @Override
                protected Void doInBackground(Void... voids) {

                    final String tableName = "rides";
                    try {

                        MobileServiceClient wamsClient = getMobileServiceClient(); //wamsUtils.init(getApplicationContext());

                        final Ride ride = new Ride();

                        ride.setRideCode(mRideCode);
                        ride.setCarNumber(mCarNumber);
                        ride.setGFenceName(getCurrentGFenceName());
                        ride.setDriverName(getUser().getFullName());
                        ride.setCreated(new Date());
                        ride.setPictureRequiredByDriver(false);

                        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                        final String userID = sharedPrefs.getString(Globals.USERIDPREF, "");
                        final String token = sharedPrefs.getString(Globals.WAMSTOKENPREF, "");

                        List<Pair<String, String>> parameters = new ArrayList<>();
                        parameters.add(new Pair<>("rideCodeGenerated", "true"));

                        MobileServiceUser wamsUser = new MobileServiceUser(userID);
                        wamsUser.setAuthenticationToken(token);
                        wamsClient.setCurrentUser(wamsUser);

                        MobileServiceTable<Ride> ridesTable = wamsClient.getTable(tableName, Ride.class);
                        mCurrentRide = ridesTable.insert(ride, parameters).get();

                        Assert.assertNotNull(mCurrentRide);

                    } catch (Exception ex)  {
                        if( Crashlytics.getInstance() != null )
                            Crashlytics.logException(ex);

                        mEx = ex;
                        if( !ex.getMessage().isEmpty() )
                            Log.e(LOG_TAG, ex.getMessage());
                    }

                    return null;
                }
            };

    // codes handled in onActivityResult()
    final int WIFI_CONNECT_REQUEST  = 100;// request code for starting WiFi connection
    final int REQUEST_IMAGE_CAPTURE = 1000;

    private Handler handler = new Handler(this);

    public Handler getHandler() {
        return handler;
    }

    MaterialDialog                              mOfflineDialog;
    MaterialDialog                              mAppealDialog;
    private Boolean                             mCabinPictureButtonShown = false;
    private Boolean                             mCabinShown = false;
    private Boolean                             mSubmitButtonShown = false;

    private Runnable                            mEnableCabinPictureButtonRunnable = new Runnable() {

        @Override
        public void run() {

            if( Globals.isInGeofenceArea() ) {
                FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.submit_ride_button);
                fab.setVisibility(View.VISIBLE);

                mTextSwitcher.setText(getString(R.string.instruction_not_enought_passengers));

                mCabinPictureButtonShown = true;
            } else {
                getHandler().postDelayed(mEnableCabinPictureButtonRunnable,
                        Globals.CABIN_PICTURES_BUTTON_SHOW_INTERVAL);
            }
        }
    };



    @Override
    @CallSuper
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_role);

        mOfflineDialog = new MaterialDialog.Builder(this)
                .title(R.string.offline)
                .content(R.string.offline_prompt)
                .iconRes(R.drawable.ic_exclamation)
                .autoDismiss(true)
                .cancelable(false)
                .positiveText(getString(R.string.try_again))
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {

                        if (!isConnectedToNetwork()) {
                            getHandler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    mOfflineDialog.show();
                                }
                            }, 200);
                        } else {
                            setupNetwork();
                        }
                    }
                }).build();

        mAppealDialog = new MaterialDialog.Builder(this)
                .title(R.string.appeal_answer)
                .iconRes(R.drawable.ic_info)
                .positiveText(R.string.appeal_send)
                .negativeText(R.string.appeal_cancel)
                .neutralText(R.string.appeal_another_picture)
                .customView(R.layout.dialog_appeal_answer, false) // do not wrap in scroll
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        sendAppeal();
                    }

                    @Override
                    public void onNegative(MaterialDialog dialog) {
                        finish();
                    }

                    @Override
                    public void onNeutral(MaterialDialog dialog) {
                        onAppealCamera();
                    }
                }).build();

        // Keep device awake when advertising for Wi-Fi Direct
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        forceLTR();

        setupUI(getString(R.string.title_activity_driver_role), "");

        _mRideCodeUploaded.set(false);

//        try {
//            mCurrentLocation = getCurrentLocation(); // SecurityException may come from here
//
//            startLocationUpdates(this);
//        } catch( SecurityException sex) {
//
//            new MaterialDialog.Builder(this)
//                    .title(R.string.permission_lacked_title)
//                    .content(R.string.location_permission_lacked)
//                    .iconRes(R.drawable.ic_exclamation)
//                    .positiveText(R.string.ok)
//                    .onPositive(new MaterialDialog.SingleButtonCallback() {
//                        @Override
//                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
//                            finish();
//                        }
//                    })
//                    .show();
//        }

        if (savedInstanceState != null) {
            wamsInit();
            initGeofences(this); // upon successful return, will be continued on initialized()

            restoreState(savedInstanceState);
        } else {

            if( isConnectedToNetwork() ) {
                wamsInit();
                initGeofences(this);

                setupNetwork();
            }
        }

        if( mWiFiUtil == null ) {
            mWiFiUtil = new WiFiUtil(this);
        }

        mWiFiUtil.deletePersistentGroups();

    }

    private void restoreState(Bundle savedInstanceState) {
        boolean bInitializedBeforeRotation = false;


        if (savedInstanceState.containsKey(Globals.PARCELABLE_KEY_RIDE_CODE)) {
            bInitializedBeforeRotation = true;

            mRideCode = savedInstanceState.getString(Globals.PARCELABLE_KEY_RIDE_CODE);
            _mRideCodeUploaded.set( savedInstanceState.getBoolean(Globals.PARCELABLE_KEY_RIDE_CODE_UPLOADED) );

            TextView txtRideCode = (TextView) findViewById(R.id.txtRideCode);
            txtRideCode.setVisibility(View.VISIBLE);
            txtRideCode.setText(mRideCode);

            TextView txtRideCodeCaption = (TextView) findViewById(R.id.code_label_caption);
            txtRideCodeCaption.setText(R.string.ride_code_label);

            ImageView imageTransmit = (ImageView) findViewById(R.id.img_transmit);
            imageTransmit.setVisibility(View.VISIBLE);
            AnimationDrawable animationDrawable = (AnimationDrawable) imageTransmit.getDrawable();
            animationDrawable.start();
        }

        if (savedInstanceState.containsKey(Globals.PARCELABLE_KEY_CURRENT_RIDE)) {
            bInitializedBeforeRotation = true;
            mCurrentRide = savedInstanceState.getParcelable(Globals.PARCELABLE_KEY_CURRENT_RIDE);
        }

        if (savedInstanceState.containsKey(Globals.PARCELABLE_KEY_PASSENGERS)) {
            bInitializedBeforeRotation = true;
            ArrayList<User> passengers = savedInstanceState.getParcelableArrayList(Globals.PARCELABLE_KEY_PASSENGERS);
            if (passengers != null) {

                mPassengers.addAll(passengers);
                mPassengersAdapter.notifyDataSetChanged();

                for (User passenger : passengers) {
                    Globals.addMyPassenger(passenger);
                }

                if (passengers.size() >= Globals.REQUIRED_PASSENGERS_NUMBER) {
                    FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.submit_ride_button);
                    Context ctx = getApplicationContext();
                    fab.setImageDrawable(ContextCompat.getDrawable(ctx, R.drawable.ic_action_done));
                }

            }

        }

        if (savedInstanceState.containsKey(Globals.PARCELABLE_KEY_CABIN_PICTURES_BUTTON_SHOWN)) {
            bInitializedBeforeRotation = true;

            mCabinPictureButtonShown = savedInstanceState.getBoolean(Globals.PARCELABLE_KEY_CABIN_PICTURES_BUTTON_SHOWN);
            if (mCabinPictureButtonShown)
                findViewById(R.id.submit_ride_button).setVisibility(View.VISIBLE);
            else
                findViewById(R.id.submit_ride_button).setVisibility(View.GONE);
        }

        if (savedInstanceState.containsKey(Globals.PARCELABLE_KEY_CAPTURED_PASSENGERS_IDS)) {
            bInitializedBeforeRotation = true;

            mCapturedPassengersIDs =
                    savedInstanceState.getIntegerArrayList(Globals.PARCELABLE_KEY_CAPTURED_PASSENGERS_IDS);

            if (mCapturedPassengersIDs != null) {
                for (int i = 0; i < mCapturedPassengersIDs.size(); i++) {

                    int nCaptured = mCapturedPassengersIDs.get(i);

                    int fabID = getResources().getIdentifier("passenger" + Integer.toString(i + 1),
                            "id", this.getPackageName());

                    FloatingActionButton fab = (FloatingActionButton) findViewById(fabID);
                    if (fab != null) {
                        if (nCaptured == 1) {

                            fab.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.green)));

                            String parcelableKey = Globals.PARCELABLE_KEY_PASSENGER_PREFIX + (i + 1);
                            if (savedInstanceState.containsKey(parcelableKey)) {
                                Bitmap passengerThumb = savedInstanceState.getParcelable(parcelableKey);
                                fab.setImageBitmap(passengerThumb);
                            } else
                                fab.setImageResource(R.drawable.ic_action_done);
                        } else
                            fab.setImageResource(R.drawable.ic_action_camera);
                    } else {
                        String msg = String.format("onCreate: Passenger FAB %d is not found", i + 1);
                        Log.e(LOG_TAG, msg);
                    }
                }
            }
        }

        if (savedInstanceState.containsKey(Globals.PARCELABLE_KEY_PASSENGERS_FACE_IDS)) {
            bInitializedBeforeRotation = true;

            HashMap<Integer, PassengerFace> hash_map =
                    (HashMap) savedInstanceState.getSerializable(Globals.PARCELABLE_KEY_PASSENGERS_FACE_IDS);

            Globals.set_PassengerFaces(hash_map);

            // See the explanation of covariance in Java here
            // http://stackoverflow.com/questions/6951306/cannot-cast-from-arraylistparcelable-to-arraylistclsprite
//                ArrayList<PassengerFace> _temp = savedInstanceState.getParcelableArrayList(Globals.PARCELABLE_KEY_PASSENGERS_FACE_IDS);
//                Globals.set_PassengerFaces(_temp);
        }

        if (savedInstanceState.containsKey(Globals.PARCELABLE_KEY_APPEAL_PHOTO_URI)) {
            bInitializedBeforeRotation = true;

            String str = savedInstanceState.getString(Globals.PARCELABLE_KEY_APPEAL_PHOTO_URI);
            mUriPhotoAppeal = Uri.parse(str);
        }

        if (savedInstanceState.containsKey(Globals.PARCELABLE_KEY_EMOJIID)) {
            bInitializedBeforeRotation = true;

            mEmojiID = savedInstanceState.getInt(Globals.PARCELABLE_KEY_EMOJIID);
        }

        if (savedInstanceState.containsKey(Globals.PARCELABLE_KEY_DRIVER_CABIN_SHOWN)) {
            bInitializedBeforeRotation = true;

            mCabinShown = savedInstanceState.getBoolean(Globals.PARCELABLE_KEY_DRIVER_CABIN_SHOWN);

            if (mCabinShown)
                showCabinView();
        }

        if (savedInstanceState.containsKey(Globals.PARCELABLE_KEY_SUBMIT_BUTTON_SHOWN)) {
            bInitializedBeforeRotation = true;

            mSubmitButtonShown = savedInstanceState.getBoolean(Globals.PARCELABLE_KEY_SUBMIT_BUTTON_SHOWN);

            if (mSubmitButtonShown)
                showSubmitPicsButton();

        }

        if (savedInstanceState.containsKey(Globals.PARCELABLE_KEY_APPEAL_DIALOG_SHOWN)) {
            bInitializedBeforeRotation = true;

            View view = mAppealDialog.getCustomView();
            if (view != null) {
                ImageView imageViewAppeal = (ImageView) view.findViewById(R.id.imageViewAppeal);
                if (imageViewAppeal != null)
                    imageViewAppeal.setImageURI(mUriPhotoAppeal);
            }
            mAppealDialog.show();
        }

        if( !bInitializedBeforeRotation )
            setupNetwork();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        TextView txtRideCode = (TextView) findViewById(R.id.txtRideCode);
        String rideCode = txtRideCode.getText().toString();
        if( !rideCode.isEmpty() ) {

            outState.putString(Globals.PARCELABLE_KEY_RIDE_CODE, rideCode);
            outState.putBoolean(Globals.PARCELABLE_KEY_RIDE_CODE_UPLOADED, _mRideCodeUploaded.get()); // isRideCodeUploaded());
            outState.putParcelableArrayList(Globals.PARCELABLE_KEY_PASSENGERS, mPassengers);

            outState.putParcelable(Globals.PARCELABLE_KEY_CURRENT_RIDE, mCurrentRide);

            outState.putBoolean(Globals.PARCELABLE_KEY_CABIN_PICTURES_BUTTON_SHOWN, mCabinPictureButtonShown);

            outState.putIntegerArrayList(Globals.PARCELABLE_KEY_CAPTURED_PASSENGERS_IDS, mCapturedPassengersIDs);

            outState.putSerializable(Globals.PARCELABLE_KEY_PASSENGERS_FACE_IDS, Globals.get_PassengerFaces());

            if( mUriPhotoAppeal != null)
                outState.putString(Globals.PARCELABLE_KEY_APPEAL_PHOTO_URI, mUriPhotoAppeal.toString());

            outState.putInt(Globals.PARCELABLE_KEY_EMOJIID, mEmojiID);

            outState.putBoolean(Globals.PARCELABLE_KEY_DRIVER_CABIN_SHOWN, mCabinShown);

            outState.putBoolean(Globals.PARCELABLE_KEY_SUBMIT_BUTTON_SHOWN, mSubmitButtonShown);

            // Store passengers captured thumbnails, if any
            View rootView = findViewById(R.id.cabin_background_layout);
            try {
                for (int i = 1; i <= Globals.REQUIRED_PASSENGERS_NUMBER; i++) {
                    String tag = Integer.toString(i);
                    FloatingActionButton passengerPictureButton = (FloatingActionButton) rootView.findViewWithTag(tag);
                    if (passengerPictureButton != null) {

                        String parcelableKey = Globals.PARCELABLE_KEY_PASSENGER_PREFIX + tag;

                        Drawable drawableThumb = passengerPictureButton.getDrawable();
                        if (drawableThumb instanceof BitmapDrawable) {
                            BitmapDrawable bmpDrawable = (BitmapDrawable) drawableThumb;

                            outState.putParcelable(parcelableKey, bmpDrawable.getBitmap());
                        } else {
                            Bitmap bmp = convertToBitmap(drawableThumb,
                                    drawableThumb.getIntrinsicWidth(),
                                    drawableThumb.getIntrinsicHeight());
                            outState.putParcelable(parcelableKey, bmp);
                        }

                    } else {
                        String msg = String.format("onSaveInstanceState: Passenger FAB %d is not found", i);
                        Log.e(LOG_TAG, msg);
                    }

                }
            }
            catch(Exception ex){

                if( Crashlytics.getInstance() != null )
                    Crashlytics.logException(ex);

                Log.e(LOG_TAG, ex.getMessage());
            }

            if( mAppealDialog.isShowing() ) {
                outState.putBoolean(Globals.PARCELABLE_KEY_APPEAL_DIALOG_SHOWN, true);
            }
        }

        super.onSaveInstanceState(outState);
    }

    //
    // IInitializeNotifier implementation
    //
    @Override
    public void initialized(Object what){

        String msg = getGFenceForLocation(mCurrentLocation);
        mTextSwitcher.setText(msg);

        if (Globals.isInGeofenceArea()) { // set or not set inside getGFenceForLocation()
            //if (!isRideCodeUploaded() && mRideCode != null) {
            if( mRideCode != null && _mRideCodeUploaded.compareAndSet(false, true) ) {
                //setRideCodeUploaded(true);
                mWAMSUploadRideCodeTask.execute();
            }
        }
    }

    private Bitmap convertToBitmap(Drawable drawable, int widthPixels, int heighPixels) {
        Bitmap mutableBitmap = Bitmap.createBitmap(widthPixels, heighPixels, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(mutableBitmap);
        drawable.setBounds(0, 0, widthPixels, heighPixels);
        drawable.draw(canvas);

        return mutableBitmap;
    }

    private void showCabinView() {

        findViewById(R.id.drive_internal_layout).setVisibility(View.GONE);
        findViewById(R.id.driver_status_layout).setVisibility(View.GONE);
        findViewById(R.id.status_strip).setVisibility(View.GONE);

        findViewById(R.id.cabin_background_layout).setVisibility(View.VISIBLE);

        mCabinShown = true;
    }

    public void hideCabinView(View v){

        findViewById(R.id.cabin_background_layout).setVisibility(View.GONE);

        findViewById(R.id.status_strip).setVisibility(View.VISIBLE);
        findViewById(R.id.driver_status_layout).setVisibility(View.VISIBLE);
        findViewById(R.id.drive_internal_layout).setVisibility(View.VISIBLE);

        mCabinShown = false;
    }

    //
    // Implementation of LocationListener
    //
    @Override
    public void onLocationChanged(Location location) {

        if( !Globals.DEBUG_WITHOUT_GEOFENCES ) {

            if (!isAccurate(location)) {
                Globals.setInGeofenceArea(false); // ?
                mTextSwitcher.setCurrentText(getGFenceForLocation(null));
                Log.d(LOG_TAG, getString(R.string.location_inaccurate));
                return;
            }

            mCurrentLocation = location;
            // Global flag 'inGeofenceArea' is updated inside getGFenceForLocation()
            String msg = getGFenceForLocation(location);

            TextView textView = (TextView) mTextSwitcher.getCurrentView();
            String msgRepeat = textView.getText().toString();

            if (Globals.isInGeofenceArea()) {

                mLastLocationUpdateTime = System.currentTimeMillis();

                // Send notification and log the transition details.
                if( Globals.getRemindGeofenceEntrance() ) {

                    Globals.clearRemindGeofenceEntrance();

                    sendNotification(msg, DriverRoleActivity.class);
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

            // Upload generated ride-code (within whole ride) once if geo-fences were initialized
            if ( isGeoFencesInitialized()
                    && mRideCode != null
                    && _mRideCodeUploaded.compareAndSet(false, true) ) {
                mWAMSUploadRideCodeTask.execute();
            }
        } else {

            if ( mRideCode != null && _mRideCodeUploaded.compareAndSet(false, true) ) {
                mWAMSUploadRideCodeTask.execute();
            }
        }

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        String msg = String.format("%s location provider %s",
                provider,
                getLocationProviderStatus(status));
        Log.i(LOG_TAG, msg);
    }

    private String getLocationProviderStatus(int status) {

        String strStatus = "undefined";

        switch(status) {
            case LocationProvider.AVAILABLE:
                strStatus = "Available";
                break;

            case LocationProvider.OUT_OF_SERVICE:
                strStatus = "Out of service";
                break;

            case LocationProvider.TEMPORARILY_UNAVAILABLE:
                strStatus = "Temporarily unavailable";
                break;

        }

        return strStatus;
    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }

    private void startAdvertise(String userID,
                                String userName,
                                String rideCode) {

        if( mWiFiUtil == null )
            mWiFiUtil = new WiFiUtil(this);

        // This will publish the service in DNS-SD and start serviceDiscovery()
        mWiFiUtil.startRegistrationAndDiscovery(this,
                userID,
                userName,
                rideCode,
                getHandler(),
                500);

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
            if( ActivityCompat.shouldShowRequestPermissionRationale(this,
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

        try {

            if (!isConnectedToNetwork()) {
                TextView txtRideCodeLabel = (TextView) findViewById(R.id.code_label_caption);
                txtRideCodeLabel.setText("");
                mOfflineDialog.show();
            } else {

                if (mOfflineDialog != null && mOfflineDialog.isShowing()) {
                    mOfflineDialog.dismiss();

                    setupNetwork();
                }
            }
        } catch(Exception ex){
            Log.e(LOG_TAG, ex.getMessage());
        }

        if( mWiFiUtil != null )
            mWiFiUtil.registerReceiver(this);
    }

    @Override
    @CallSuper
    public void onPause() {

        if( mWiFiUtil != null) {
            mWiFiUtil.unregisterReceiver();
            mWiFiUtil.stopDiscovery();
        }

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
        if( mWiFiUtil != null )
            mWiFiUtil.removeGroup();

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Globals.clearMyPassengerIds();
        Globals.clearMyPassengers();
        //Globals.clearPassengerFaces();

        if( mCheckPassengerTimerResult != null )
            mCheckPassengerTimerResult .cancel(true);

        super.onDestroy();
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults){

        try {

            switch( requestCode ) {

                case Globals.LOCATION_PERMISSION_REQUEST: {

                    if(  grantResults.length > 0
                            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                        mCurrentLocation = getCurrentLocation(this);
                        startLocationUpdates(this, this);
                    }
                }
                break;

                case Globals.CAMERA_PERMISSION_REQUEST : {
                    if (grantResults.length > 0
                            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        onSubmitRideInternal();
                    }
                }
                break;
            }

        } catch (Exception ex) {
            Log.e(LOG_TAG, ex.getMessage());

        }

    }

    public void onButtonPassengerCamera(View v) {

        Intent intent = new Intent(this, CameraCVActivity.class);

        try {
            Object tag = v.getTag();
            if (tag != null) {
                int requestCode = Integer.valueOf((String) tag);
                startActivityForResult(intent, requestCode);
            }
        } catch(Exception ex) {

            if( Crashlytics.getInstance() != null )
                Crashlytics.logException(ex);

            Log.e(LOG_TAG, ex.getMessage());
        }
    }

    private Runnable thanksRunnable = new Runnable() {
        @Override
        public void run() {
            new MaterialDialog.Builder(DriverRoleActivity.this)
                    .title(R.string.thanks)
                    .content(R.string.nofee_request_accepted)
                    .iconRes(R.drawable.ic_info)
                    .cancelable(false)
                    .positiveText(R.string.ok)
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            finish();
                        }
                    })
                    .show();
        }
    };

    public void onSubmitRidePics(View v){

        cancelNotification();

        // Only allow no-fee request from monitored area
        if( !Globals.isInGeofenceArea() ) {

            new MaterialDialog.Builder(this)
                    .title(R.string.geofence_outside_title)
                    .content(R.string.geofence_outside)
                    .positiveText(R.string.geofence_positive_answer)
                    .negativeText(R.string.geofence_negative_answer)
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog){
                            Globals.setRemindGeofenceEntrance();
                        }
                    })
                    .show();

            return;
        }

        // Process the Faces with Oxford FaceAPI
        // Will be continued on finish() from IPictureURLUpdater
        new faceapiUtils(this).execute();
    }

    AsyncTask<Void, Void, Void> updateCurrentRideTask = new AsyncTask<Void, Void, Void>() {

        Exception mEx;
        LoadToast lt;

        @Override
        protected void onPreExecute() {
            lt = new LoadToast(DriverRoleActivity.this);
            lt.setText(getString(R.string.processing));
            Display display = getWindow().getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            lt.setTranslationY(size.y / 2);
            lt.show();
        }

        @Override
        protected Void doInBackground(Void... args) {

            try {
                String currentGeoFenceName = Globals.get_currentGeoFenceName();
                mCurrentRide.setGFenceName(currentGeoFenceName);
                mRidesTable.update(mCurrentRide).get();
            } catch (InterruptedException | ExecutionException e) {
                mEx = e;
                Log.e(LOG_TAG, e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result){

            CustomEvent requestEvent = new CustomEvent(getString(R.string.no_fee_answer_name));
            requestEvent.putCustomAttribute("User", getUser().getFullName());

            if( mEx != null ) {

                lt.error();
                beepError.start();
            }
            else {

                lt.success();
                beepSuccess.start();

                getHandler().postDelayed(thanksRunnable, 1500);

            }

            requestEvent.putCustomAttribute(getString(R.string.answer_approved_attribute), 1);
            Answers.getInstance().logCustom(requestEvent);
        }
    };

    public void onSubmitRide(View view) {

        if (mCurrentRide == null) {
            View v = findViewById(R.id.passenger_snackbar);
            if( v != null )
                Snackbar.make(v, R.string.ride_not_uploaded, Snackbar.LENGTH_SHORT)
                        .show();

            return;
        }

        try {

            checkCameraAndStoragePermissions();
            onSubmitRideInternal();

        } catch(SecurityException sex) {

                // Returns true if app has requested this permission previously 
                // and the user denied the request 
                if( ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                    mTextSwitcher.setCurrentText(getString(R.string.permission_camera_denied));
                    Log.d(LOG_TAG, getString(R.string.permission_camera_denied) );
                } else {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            Globals.CAMERA_PERMISSION_REQUEST);
                }
        }

    }

    private void onSubmitRideInternal() {

        if( mPassengers.size() < Globals.REQUIRED_PASSENGERS_NUMBER )
        {
            showCabinView();
            return;
        }

        boolean bRequestApprovalBySefies = false;

        for(User passenger: Globals.getMyPassengers() ){
            if( passenger.wasSelfPictured() ) {
                bRequestApprovalBySefies = true;
                break;
            }

        }

        if( bRequestApprovalBySefies )
            onSubmitRidePics(null);
        else {

            // Only allow no-fee request from monitored area
            if( !Globals.isInGeofenceArea() ) {

                new MaterialDialog.Builder(this)
                        .title(R.string.geofence_outside_title)
                        .content(R.string.geofence_outside)
                        .positiveText(R.string.geofence_positive_answer)
                        .negativeText(R.string.geofence_negative_answer)
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog){
                                Globals.setRemindGeofenceEntrance();
                            }
                        })
                        .show();

                return;
            }

            mCurrentRide.setApproved(Globals.RIDE_STATUS.APPROVED.ordinal());
            updateCurrentRideTask.execute();
        }

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_driver_role, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        return super.onOptionsItemSelected(item);
    }

    private void wamsInit() {

        wamsInit(true);

        mRidesTable = getMobileServiceClient().getTable("rides", Ride.class);
    }

    private void setupNetwork() {

        int min = 100000;
        int max = 1000000;

        Random r = new Random();
        int rideCode = r.nextInt(max - min + 1) + min;
        mRideCode = Integer.toString(rideCode);

        TextView txtRideCodeCaption = (TextView)findViewById(R.id.code_label_caption);
        txtRideCodeCaption.setText(R.string.ride_code_label);
        TextView txtRideCode = (TextView) findViewById(R.id.txtRideCode);
        txtRideCode.setVisibility(View.VISIBLE);
        txtRideCode.setText(mRideCode);

        mImageTransmit.setVisibility(View.VISIBLE);
        AnimationDrawable animationDrawable = (AnimationDrawable) mImageTransmit.getDrawable();
        animationDrawable.start();

        startAdvertise(getUser().getRegistrationId(),
                        getUser().getFullName(),
                        mRideCode);

        getHandler().postDelayed(mEnableCabinPictureButtonRunnable,
                                 Globals.CABIN_PICTURES_BUTTON_SHOW_INTERVAL);

//        new AsyncTask<Void, Void, Void>(){
//
//            Exception mEx;
//            boolean mAppMode;
//
//            @Override
//            protected void onPostExecute(Void result) {
////                if( !mAppMode ) { // picture is not required by server
////
////                    prepareLayoutForDiscovery();
////                    discoverPassengers();
////
////                } else {
////                    mAdvertiseTask.execute();
////                }
//
//            }
//
//            @Override
//            protected Void doInBackground(Void... voids) {
//
//                try {
//                    MobileServiceList<GlobalSettings> settings =
//                            settingsTable.where().field("s_name").eq("app_mode").execute().get();
//
//                    if( settings.size() > 0 ) {
//                        GlobalSettings _settings = settings.get(0);
//
//                        mAppMode = Boolean.parseBoolean(_settings.getValue());
//                    }
//                } catch(Exception ex) {
//                    mEx = ex;
//                }
//
//                return null;
//            }
//        }.execute();


        mCheckPassengerTimerResult =
        mCheckPasengersTimer.scheduleAtFixedRate(new Runnable() {
                                                     @Override
                                                     public void run() {

                                                         final List<User> passengers = Globals.getMyPassengers();

                                                         if( mLastPassengersLength != passengers.size()
                                                                 || Globals.isPassengerListAlerted() ) {

                                                             mLastPassengersLength = passengers.size();
                                                             Globals.setPassengerListAlerted(false);

                                                             // Update UI on UI thread
                                                             runOnUiThread(new Runnable() {
                                                                 @Override
                                                                 public void run() {
                                                                     mPassengers.clear();
                                                                     mPassengers.addAll(passengers);

                                                                     for(User passenger: mPassengers) {
                                                                         if( passenger.wasSelfPictured() ) {
                                                                             mTextSwitcher.setText(getString(R.string.instruction_advanced_mode));
                                                                             break;
                                                                         }
                                                                     }

                                                                     mPassengersAdapter.notifyDataSetChanged();

                                                                     View v = findViewById(R.id.empty_view);
                                                                     if( v != null )
                                                                         v.setVisibility(View.GONE);

                                                                     if( mLastPassengersLength >= Globals.REQUIRED_PASSENGERS_NUMBER){
                                                                         FloatingActionButton fab = (FloatingActionButton)findViewById(R.id.submit_ride_button);

                                                                         Context ctx =  getApplicationContext();
                                                                         fab.setImageDrawable(ContextCompat.getDrawable(ctx, R.drawable.ic_action_done));

                                                                         mTextSwitcher.setText(getString(R.string.instruction_can_submit_no_fee));
                                                                     }

                                                                 }
                                                             });

                                                         }

                                                     }
                                                 },
                1, // 1-sec delay
                2, // period between successive executions
                TimeUnit.SECONDS);

        List<String> _cars = new ArrayList<>();
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        Set<String> carsSet = sharedPrefs.getStringSet(Globals.CARS_PREF, new HashSet<String>());
        if (carsSet.size() > 0) {
            Iterator<String> iterator = carsSet.iterator();
            while (iterator.hasNext()) {
                String carNumber = iterator.next();
                _cars.add(carNumber);
            }
        }

        String[] cars = new String[_cars.size()];
        cars = _cars.toArray(cars);

        if (cars.length == 0) {
            new MaterialDialog.Builder(this)
                    .title(R.string.edit_car_dialog_caption2)
                    .content(R.string.edit_car_dialog_text)
                    .iconRes(R.drawable.ic_exclamation)
                    .autoDismiss(true)
                    .cancelable(false)
                    .positiveText(getString(R.string.edit_car_button_title2))
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            Intent intent = new Intent(getApplicationContext(),
                                                       SettingsActivity.class);
                            startActivity(intent);
                        }
                    })
                    .show();
        } else if (cars.length > 1) {

            new MaterialDialog.Builder(this)
                    .title(R.string.edit_car_dialog_caption1)
                    .iconRes(R.drawable.ic_info)
                    .autoDismiss(true)
                    .cancelable(false)
                    .items(cars)
                    .positiveText(getString(R.string.edit_car_button_title))
                    .itemsCallback(new MaterialDialog.ListCallback() {
                        @Override
                        public void onSelection(MaterialDialog dialog,
                                                View view,
                                                int which,
                                                CharSequence text) {
                            mCarNumber = text.toString();
                        }
                    })
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            Intent intent = new Intent(getApplicationContext(),
                                    SettingsActivity.class);
                            startActivity(intent);
                        }
                    })
                    .show();
        } else {
            mCarNumber = cars[0];
        }
    }

    @UiThread
    private void showSubmitPicsButton() {

        FloatingActionButton fab = (FloatingActionButton)findViewById(R.id.submit_ride_button_pics);
        Context ctx =  getApplicationContext();
        if( fab != null) {
            fab.setVisibility(View.VISIBLE);
            fab.setImageDrawable(ContextCompat.getDrawable(ctx, R.drawable.ic_action_done));

            mTextSwitcher.setText(getString(R.string.instruction_can_submit_no_fee));

            mSubmitButtonShown = true;
        }
    }

    private void addPassengerFace(int at, UUID faceID, String faceURI) {

        try {

            boolean bPFaceAdded = false;

            PassengerFace pFace;
            pFace = Globals.get_PassengerFace(at);
            if( pFace == null ) {
                pFace = new PassengerFace();

                bPFaceAdded = true;
            }
            pFace.setFaceId(faceID.toString());
            pFace.setPictureUrl(faceURI);

            if( bPFaceAdded )
                Globals.get_PassengerFaces().put(at, pFace);

            int size = 0;
            for (Map.Entry<Integer,PassengerFace> entry : Globals.get_PassengerFaces().entrySet()) {

                PassengerFace pf = entry.getValue();

                if (pf.isInitialized())
                    size++;
            }

            if( size >= Globals.REQUIRED_PASSENGERS_NUMBER ) {
                getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        showSubmitPicsButton();
                    }
                });
            }


        } catch(Exception ex) {

            if( Crashlytics.getInstance() != null )
                Crashlytics.logException(ex);

            Log.e(LOG_TAG, ex.getMessage());
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void forceLTR() {
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 ) {
            View v = findViewById(R.id.driver_status_layout);
            if( v != null )
                v.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

            v = findViewById(R.id.cabin_background_layout);
            if( v!= null )
                v.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            //getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        }
    }

    @Override
    protected void setupUI(String title, String subTitle) {
        super.setupUI(title, subTitle);

        // Prevent memory leak on ImageView
        View v = findViewById(R.id.centerImage);
        v.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        mImageTransmit = (ImageView) findViewById(R.id.img_transmit);

        mPeersRecyclerView = (RecyclerView) findViewById(R.id.recyclerViewPeers);
        mPeersRecyclerView.setHasFixedSize(true);

        Globals.LayoutManagerType passengerLayoutManagerType;

        if( getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT ) {
            mPeersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            passengerLayoutManagerType = Globals.LayoutManagerType.LINEAR_LAYOUT_MANAGER;
        }
        else {
            mPeersRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
            passengerLayoutManagerType = Globals.LayoutManagerType.GRID_LAYOUT_MANAGER;
        }

        mPeersRecyclerView.setItemAnimator(new DefaultItemAnimator());

        mPassengersAdapter = new PassengersAdapter(this,
                                            passengerLayoutManagerType,
                                            R.layout.peers_header,
                                            R.layout.row_passenger,
                                            mPassengers);
        mPeersRecyclerView.setAdapter(mPassengersAdapter);

        mSwipeTouchListener =
                new SwipeableRecyclerViewTouchListener(mPeersRecyclerView,
                        new SwipeableRecyclerViewTouchListener.SwipeListener() {
                            @Override
                            public boolean canSwipe(int position) {
                                return true;
                            }

                            @Override
                            public void onDismissedBySwipeLeft(RecyclerView recyclerView, int[] reverseSortedPositions) {
//                                for (int position : reverseSortedPositions) {
////                                    Toast.makeText(MainActivity.this, mItems.get(position) + " swiped left", Toast.LENGTH_SHORT).show();
//                                    mItems.remove(position);
//                                    mAdapter.notifyItemRemoved(position);
//                                }
//                                mAdapter.notifyDataSetChanged();
                            }

                            @Override
                            public void onDismissedBySwipeRight(RecyclerView recyclerView, int[] reverseSortedPositions) {
//                                for (int position : reverseSortedPositions) {
////                                    Toast.makeText(MainActivity.this, mItems.get(position) + " swiped right", Toast.LENGTH_SHORT).show();
//                                    mItems.remove(position);
//                                    mAdapter.notifyItemRemoved(position);
//                                }
//                                mAdapter.notifyDataSetChanged();
                            }
                        });
        mPeersRecyclerView.addOnItemTouchListener(mSwipeTouchListener);

        mTextSwitcher = (TextSwitcher) findViewById(R.id.monitor_text_switcher);
        Animation in = AnimationUtils.loadAnimation(this, R.anim.push_up_in);
        Animation out = AnimationUtils.loadAnimation(this, R.anim.push_up_out);
        mTextSwitcher.setInAnimation(in);
        mTextSwitcher.setOutAnimation(out);
        // Set the initial text without an animation
        String currentMonitorStatus = getString(R.string.geofence_outside_title);
        mTextSwitcher.setCurrentText(currentMonitorStatus);

        mTextSwitcher.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                Intent intent = new Intent(DriverRoleActivity.this,
                        GFActivity.class);
                startActivity(intent);

                return false;
            }
        });

        Globals.setMonitorStatus(getString(R.string.geofence_outside_title));

        for(int i = 0; i < Globals.REQUIRED_PASSENGERS_NUMBER; i++)
            mCapturedPassengersIDs.add(0);

//        int passengerFacesSize = Globals.get_PassengerFaces().size();
//        if(  passengerFacesSize < Globals.REQUIRED_PASSENGERS_NUMBER) {
//            for (int i = 0; i < Globals.REQUIRED_PASSENGERS_NUMBER; i++) {
//                Globals.add_PassengerFace(new PassengerFace());
//            }
//        }

        if( Globals.REQUIRED_PASSENGERS_NUMBER == 3 ) {
            View view = findViewById(R.id.passenger4);
            if( view != null )
                view.setVisibility(View.GONE);
        }

    }

    private boolean handleTouchEvent(MotionEvent motionEvent){
        switch( motionEvent.getActionMasked() ){

            case MotionEvent.ACTION_MOVE:{
                float deltaX = motionEvent.getRawX();// - mDownX;
                float deltaY = motionEvent.getRawY();// - mDownY;
            }
            break;
        }

        return false;
    }

    //
    // ITrace implementation
    //

    @Override
    public void trace(final String status) {

    }

    @Override
    public void alert(String message, final String actionIntent) {
//        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
//
//            @Override
//            public void onClick(DialogInterface dialogInterface, int which) {
//                if (which == DialogInterface.BUTTON_POSITIVE) {
//                    startActivityForResult(new Intent(actionIntent), WIFI_CONNECT_REQUEST);
//                }
//            }
//        };
//
//        new AlertDialogWrapper.Builder(this)
//                .setTitle(message)
//                .setNegativeButton(R.string.no, dialogClickListener)
//                .setPositiveButton(R.string.yes, dialogClickListener)
//                .show();

        AnimationDrawable animationDrawable = (AnimationDrawable) mImageTransmit.getDrawable();
        if( actionIntent.isEmpty() ) {
            animationDrawable.start();
        } else {
            animationDrawable.stop();
        }

    }

    @Override
    public boolean handleMessage(Message msg) {

        switch (msg.what) {
            case Globals.TRACE_MESSAGE:
                Bundle bundle = msg.getData();
                String strMessage = bundle.getString("message");
                trace(strMessage);
                break;

            case Globals.MESSAGE_READ:
                byte[] buffer = (byte[]) msg.obj;
                strMessage = new String(buffer);
                trace(strMessage);
                break;

        }

        return true;
    }


    //
    // Implementation of IRefreshable
    //
    public void refresh() {
        mPassengers.clear();
        mPassengersAdapter.notifyDataSetChanged();

        final ImageButton btnRefresh = (ImageButton) findViewById(R.id.btnRefresh);
        btnRefresh.setVisibility(View.GONE);
        final ProgressBar progress_refresh = (ProgressBar) findViewById(R.id.progress_refresh);
        progress_refresh.setVisibility(View.VISIBLE);

        TextView txtRideCode = (TextView) findViewById(R.id.txtRideCode);
        String rideCode = txtRideCode.getText().toString();

        startAdvertise(getUser().getRegistrationId(),
                getUser().getFullName(),
                rideCode);

        getHandler().postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        btnRefresh.setVisibility(View.VISIBLE);
                        progress_refresh.setVisibility(View.GONE);
                    }
                },
                5000);
    }

    //
    // Implementation of IPeerClickListener
    //
    public void clicked(View view, int position) {

    }

    public void AppealCamera(){

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {

            try {
                mUriPhotoAppeal = createImageFile();
            } catch (IOException e) {
                Log.e(LOG_TAG, e.getMessage());
            }

            if (mUriPhotoAppeal != null) {

                intent.putExtra(MediaStore.EXTRA_OUTPUT, mUriPhotoAppeal);
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    public void onAppealCamera(){

        MaterialDialog.Builder builder = new MaterialDialog.Builder(this);

        try {

            builder.title(R.string.appeal)
                    .positiveText(R.string.ok)
                    .negativeText(R.string.cancel)
//                    .neutralText(R.string.help)
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            AppealCamera();
                        }

                        @Override
                        public void onNegative(MaterialDialog dialog) {
                            finish();
                        }

//                        @Override
//                        public void onNeutral(MaterialDialog dialog) {
//                            Intent intent = new Intent(getApplicationContext(),
//                                                        TutorialActivity.class);
//                            intent.putExtra(getString(R.string.tutorial_id), Globals.TUTORIAL_Appeal);
//                            startActivity(intent);
//                        }
                    });

            View customDialog = getLayoutInflater().inflate(R.layout.dialog_appeal, null);
            builder.customView(customDialog, false);

            if( mEmojiID == 0 ){
                mEmojiID =  new Random().nextInt(Globals.NUM_OF_EMOJIS)
                        // nextInt() gets number between 0 (inclusive and specified value (exclusive)
                                + 1;
            }

            String uri = "@drawable/emoji_" + Integer.toString(mEmojiID);
            int imageResource = getResources().getIdentifier(uri, "id",  this.getPackageName());
            ImageView emojiImageView = (ImageView)customDialog.findViewById(R.id.appeal_emoji);
            emojiImageView.setImageResource(imageResource);

            builder.show();

        } catch (Exception e) {
            Log.e(LOG_TAG, e.getMessage());
            // better that catch the exception here would be use handle to send events the activity
        }
    }

    public void onClickGoTutorial(View view){

    }

    private Uri createImageFile() throws IOException {

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String photoFileName = "AppealJPEG_" + timeStamp + "_";

        File storageDir = getExternalFilesDir(null);

        File photoFile = File.createTempFile(
                photoFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        return Uri.fromFile(photoFile);
    }

    @Override
    @CallSuper
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        View rootView = findViewById(R.id.cabin_background_layout);
        String tag = Integer.toString(requestCode);

        if( requestCode == REQUEST_IMAGE_CAPTURE
                && resultCode == RESULT_OK) {

            try {

                View view = mAppealDialog.getCustomView();
                if( view == null)
                    return;

                ImageView imageViewAppeal =  (ImageView)view.findViewById(R.id.imageViewAppeal);
                if( imageViewAppeal != null ) {

                    Drawable drawable = imageViewAppeal.getDrawable();
                    if( drawable != null ) {
                        ((BitmapDrawable) drawable).getBitmap().recycle();
                    }

                    // Downsample the image to consume less memory
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 2;
                    Bitmap bitmap = BitmapFactory.decodeFile(mUriPhotoAppeal.getPath(), options);
                    imageViewAppeal.setImageBitmap(bitmap);
                }

                mAppealDialog.show();

            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
            }

        } else if( requestCode == WIFI_CONNECT_REQUEST ) {

                // if( resultCode == RESULT_OK ) {
                // How to distinguish between successful connection
                // and just pressing back from there?
                wamsInit(true);

        } else if( (requestCode >= 1 && requestCode <= 4 )  // passengers selfies
                && resultCode == RESULT_OK ) {

            FloatingActionButton passengerPicture = (FloatingActionButton)rootView.findViewWithTag(tag);
            if( passengerPicture != null ) {
                passengerPicture.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.green)));

                Bundle extras = data.getExtras();
                if( extras != null ) {
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

                    UUID _faceId = (UUID) extras.getSerializable(getString(R.string.detection_face_id));
                    URI faceURI = (URI) extras.getSerializable(getString(R.string.detection_face_uri));
                    if( faceURI != null )
                        addPassengerFace(requestCode - 1, _faceId, faceURI.toString());
                }
            }

            mCapturedPassengersIDs.set(requestCode -1, 1);
        }
    }

    public  void sendAppeal(){
        new wamsAddAppeal(DriverRoleActivity.this,
                            getUser().getFullName(),
                            "appeals",
                            mCurrentRide.Id,
                            getUser().getRegistrationId(),
                            mEmojiID)
                .execute(new File(mUriPhotoAppeal.getPath()));
    }

    //
    // Implementations of WifiUtil.IPeersChangedListener
    //
    @Override
    public void add(final WifiP2pDeviceUser device) {
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                mPassengersAdapter.add(device);
//                mPassengersAdapter.notifyDataSetChanged();
//            }
//        });
    }

    //
    // Implementation of WifiP2pManager.PeerListListener
    // Used to synchronize peers statuses after connection

    @Override
    public void onPeersAvailable(WifiP2pDeviceList list) {
//        for (WifiP2pDevice device : list.getDeviceList()) {
//            WifiP2pDeviceUser d = new WifiP2pDeviceUser(device);
//            d.setUserId(getUser().getRegistrationId());
//            mPassengersAdapter.updateItem(d);
//        }
//
//        if( list.getDeviceList().size() > 0 )
//            mPassengersAdapter.notifyDataSetChanged();
    }

    //
    // Implementation of WifiP2pManager.ConnectionInfoListener
    //

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo p2pInfo) {
        Thread handler;

        mWiFiUtil.requestPeers(this);

         /*
         * The group owner accepts connections using a server socket and then spawns a
         * client socket for every client. This is handled by {@code
         * ServerAsyncTask}
         */
        if (p2pInfo.isGroupOwner) {
            //new WiFiUtil.ServerAsyncTask(this).execute();
            try {
                handler = new GroupOwnerSocketHandler(getHandler());
                handler.start();
                trace("Server socket opened.");
            } catch (IOException e) {
                trace("Failed to create a server thread - " + e.getMessage());
            }
        } else {
            // NOT GroupOwner, Group Owner IP: " + p2pInfo.groupOwnerAddress.getHostAddress());
            handler = new ClientSocketHandler(
                    this.getHandler(),
                    p2pInfo.groupOwnerAddress,
                    this,
                    "!!!Message from DRIVER!!!");
            handler.start();
            trace("Client socket opened.");
        }

        // Optionally may request group info
        //mManager.requestGroupInfo(mChannel, this);


    }

    //
    // Implementation of IVersionMismatchListener
    //
    @Override
    public void mismatch(int major, int minor, final String url) {
        try {
            new MaterialDialog.Builder(getApplicationContext())
                    .title(getString(R.string.new_version_title))
                    .content(getString(R.string.new_version_conent))
                    .iconRes(R.drawable.ic_info)
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
        } catch (MaterialDialog.DialogException e) {
            // better that catch the exception here would be use handle to send events the activity
        }
    }

    @Override
    public void match() {

    }

    @Override
    public void connectionFailure(Exception ex) {

        if (ex != null) {

            View v = findViewById(R.id.drawer_layout);
            Snackbar.make(v, ex.getMessage(), Snackbar.LENGTH_LONG);
        }

    }

    //
    // Implementation of GoogleApiClient.ConnectionCallbacks
    //
    @Override
    public void onConnectionSuspended(int i) {

    }

    //
    // Implementation of IPictureURLUpdater
    //
    @Override
    public void update(String url) {
    }



    @Override
    public void finished(int task_tag, boolean success) {

        switch( task_tag ) {

            case Globals.FACE_VERIFY_TASK_TAG: {

                Globals.verificationMat.loadIdentity(); // restore Matrix

                // Upload pictures as joins
                new AsyncTask<Void, Void, Void>() {

                    Exception mEx;

                    @Override
                    protected void onPreExecute() {

                    }

                    @Override
                    protected Void doInBackground(Void... voids) {

                        MobileServiceTable<Join> joinsTable = getMobileServiceClient().getTable("joins", Join.class);

                        try {
                            for(Map.Entry<Integer, PassengerFace> entry : Globals.get_PassengerFaces().entrySet()) {

                                PassengerFace pf = entry.getValue();

                                Join _join = new Join();
                                _join.setWhenJoined(new Date());
                                String pictureURI = pf.getPictureUrl();
                                if (pictureURI != null && !pictureURI.isEmpty())
                                    _join.setPictureURL(pictureURI);

                                String faceId = pf.getFaceId().toString();
                                if (!faceId.isEmpty())
                                    _join.setFaceId(faceId);
                                _join.setRideCode(mCurrentRide.getRideCode());

                                // AndroidId is meaningless in this situation: it's only a picture of passenger
                                //_join.setDeviceId(android_id);

                                try {
                                    Location loc = getCurrentLocation(DriverRoleActivity.this);
                                    if (loc != null) {
                                        _join.setLat((float) loc.getLatitude());
                                        _join.setLon((float) loc.getLongitude());
                                    }
                                } catch (Exception e) {
                                    Log.e(LOG_TAG, e.getMessage());
                                }

                                String msg = String.format("Inserting join for user with FaceID %s", faceId);
                                Log.d(LOG_TAG, msg);

                                joinsTable.insert(_join).get();
                            }

                        } catch (Exception ex) { // ExecutionException | InterruptedException ex ) {
                            mEx = ex;
                            if (Crashlytics.getInstance() != null)
                                Crashlytics.logException(ex);

                            Log.e(LOG_TAG, ex.getMessage());
                        }

                        return null;
                    }
                }.execute();

                if (success) {
                    mCurrentRide.setApproved(Globals.RIDE_STATUS.APPROVED_BY_SELFY.ordinal());
                    updateCurrentRideTask.execute();
                } else {

                    mCurrentRide.setApproved(Globals.RIDE_STATUS.DENIED.ordinal());

                    new AsyncTask<Void, Void, Void>() {

                        Exception mEx;

                        @Override
                        protected Void doInBackground(Void... args) {

                            try {
                                String currentGeoFenceName = Globals.get_currentGeoFenceName();
                                mCurrentRide.setGFenceName(currentGeoFenceName);
                                mRidesTable.update(mCurrentRide).get();
                            } catch (InterruptedException | ExecutionException e) {
                                mEx = e;
                                Log.e(LOG_TAG, e.getMessage());
                            }

                            return null;

                        }

                        @Override
                        protected void onPostExecute(Void result) {

                            CustomEvent requestEvent = new CustomEvent(getString(R.string.no_fee_answer_name));
                            requestEvent.putCustomAttribute("User", getUser().getFullName());

                            requestEvent.putCustomAttribute(getString(R.string.answer_approved_attribute), 0);
                            Answers.getInstance().logCustom(requestEvent);
                        }
                    }.execute();


                    onAppealCamera();
                }
            }
            break;

            case Globals.APPEAL_UPLOAD_TASK_TAG: {
                finish();
            }
            break;
        }
    }

}
