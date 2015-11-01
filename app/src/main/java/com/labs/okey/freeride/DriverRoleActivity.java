package com.labs.okey.freeride;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
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
import android.support.annotation.UiThread;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
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
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.labs.okey.freeride.adapters.PassengersAdapter;
import com.labs.okey.freeride.model.Join;
import com.labs.okey.freeride.model.PassengerFace;
import com.labs.okey.freeride.model.Ride;
import com.labs.okey.freeride.model.User;
import com.labs.okey.freeride.model.WifiP2pDeviceUser;
import com.labs.okey.freeride.utils.ClientSocketHandler;
import com.labs.okey.freeride.utils.Globals;
import com.labs.okey.freeride.utils.GroupOwnerSocketHandler;
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
import com.microsoft.windowsazure.mobileservices.table.MobileServiceTable;

import net.steamcrafted.loadtoast.LoadToast;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DriverRoleActivity extends BaseActivityWithGeofences
        implements ITrace,
        IMessageTarget,
        Handler.Callback,
        IRefreshable,
        IRecyclerClickListener,
        WiFiUtil.IPeersChangedListener,
        WifiP2pManager.PeerListListener,
        WifiP2pManager.ConnectionInfoListener,
        GoogleApiClient.ConnectionCallbacks,
        WAMSVersionTable.IVersionMismatchListener,
        IUploader,
        ResultCallback<Status> // for geofences callback
{

    private static final String LOG_TAG = "FR.Driver";

    PassengersAdapter                           mPassengersAdapter;
    SwipeableRecyclerViewTouchListener          mSwipeTouchListener;
    private ArrayList<User>                     mPassengers = new ArrayList<>();
    private int                                 mLastPassengersLength;

    private ArrayList<Integer>                  mCapturedPassengersIDs = new ArrayList<>();

    WiFiUtil                                    mWiFiUtil;

    TextSwitcher                                mTextSwitcher;
    RecyclerView                                mPeersRecyclerView;
    ImageView                                   mImageTransmit;

    String                                      mCarNumber;
    Uri                                         mUriPhotoAppeal;
    Ride                                        mCurrentRide;
    int                                         mEmojiID;

    private MobileServiceTable<Ride>            mRidesTable;

    ScheduledExecutorService                    mCheckPasengersTimer = Executors.newScheduledThreadPool(1);
    ScheduledExecutorService                    mCheckGeoFencesTimer = Executors.newScheduledThreadPool(1);
    AsyncTask<Void, Void, Void>                 mAdvertiseTask;

    // codes handled in onActivityResult()
    final int WIFI_CONNECT_REQUEST  = 100;// request code for starting WiFi connection
    final int REQUEST_IMAGE_CAPTURE = 1000;

    private Handler handler = new Handler(this);

    public Handler getHandler() {
        return handler;
    }

    MaterialDialog                              mOfflineDialog;
    private Boolean                             mCabinPictureButtonShown = false;
    private Boolean                             mCabinShown = false;



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

        // Keep device awake when advertising for Wi-Fi Direct
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        forceLTR();

        setupUI(getString(R.string.title_activity_driver_role), "");

        boolean bInitializedBeforeRotation = false;
        if( savedInstanceState != null ) {

            wamsInit();

            if( savedInstanceState.containsKey(Globals.PARCELABLE_KEY_RIDE_CODE) ) {
                bInitializedBeforeRotation = true;

                String rideCode = savedInstanceState.getString(Globals.PARCELABLE_KEY_RIDE_CODE);

                TextView txtRideCode = (TextView)findViewById(R.id.txtRideCode);
                txtRideCode.setVisibility(View.VISIBLE);
                txtRideCode.setText(rideCode);

                TextView txtRideCodeCaption = (TextView)findViewById(R.id.code_label_caption);
                txtRideCodeCaption.setText(R.string.ride_code_label);

                ImageView imageTransmit = (ImageView)findViewById(R.id.img_transmit);
                imageTransmit.setVisibility(View.VISIBLE);
                AnimationDrawable animationDrawable = (AnimationDrawable) imageTransmit.getDrawable();
                animationDrawable.start();
            }

            if( savedInstanceState.containsKey(Globals.PARCELABLE_KEY_CURRENT_RIDE) ) {
                bInitializedBeforeRotation = true;
                mCurrentRide = savedInstanceState.getParcelable(Globals.PARCELABLE_KEY_CURRENT_RIDE);
            }

            if( savedInstanceState.containsKey(Globals.PARCELABLE_KEY_PASSENGERS) ) {
                bInitializedBeforeRotation = true;
                ArrayList<User> passengers = savedInstanceState.getParcelableArrayList(Globals.PARCELABLE_KEY_PASSENGERS);
                if( passengers != null ) {

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

            if( savedInstanceState.containsKey(Globals.PARCELABLE_KEY_CABIN_PICTURES_BUTTON_SHOWN) ) {
                bInitializedBeforeRotation = true;

                mCabinPictureButtonShown = savedInstanceState.getBoolean(Globals.PARCELABLE_KEY_CABIN_PICTURES_BUTTON_SHOWN);
                if(mCabinPictureButtonShown)
                    findViewById(R.id.submit_ride_button).setVisibility(View.VISIBLE);
                else
                    findViewById(R.id.submit_ride_button).setVisibility(View.GONE);
            }

            if( savedInstanceState.containsKey(Globals.PARCELABLE_KEY_CAPTURED_PASSENGERS_IDS) ) {
                bInitializedBeforeRotation = true;

                mCapturedPassengersIDs =
                        savedInstanceState.getIntegerArrayList(Globals.PARCELABLE_KEY_CAPTURED_PASSENGERS_IDS);

                if( mCapturedPassengersIDs != null ) {
                    for (int i = 0; i < mCapturedPassengersIDs.size(); i++) {

                        int nCaptured = mCapturedPassengersIDs.get(i);

                        int fabID = getResources().getIdentifier("passenger" + Integer.toString(i + 1),
                                "id", this.getPackageName());

                        ImageView fab = (ImageView) findViewById(fabID);
                        if (fab != null) {
                            if (nCaptured == 1)
                                fab.setImageResource(R.drawable.ic_action_done);
                            else
                                fab.setImageResource(R.drawable.ic_action_camera);
                        }
                    }
                }
            }

            if( savedInstanceState.containsKey(Globals.PARCELABLE_KEY_PASSENGERS_FACE_IDS) ) {
                bInitializedBeforeRotation = true;

                ConcurrentHashMap<Integer, PassengerFace> hash_map =
                        (ConcurrentHashMap)savedInstanceState.getSerializable(Globals.PARCELABLE_KEY_PASSENGERS_FACE_IDS);

                Globals.set_PassengerFaces(hash_map);

                // See the explanation of covariance in Java here
                // http://stackoverflow.com/questions/6951306/cannot-cast-from-arraylistparcelable-to-arraylistclsprite
//                ArrayList<PassengerFace> _temp = savedInstanceState.getParcelableArrayList(Globals.PARCELABLE_KEY_PASSENGERS_FACE_IDS);
//                Globals.set_PassengerFaces(_temp);
            }

            if( savedInstanceState.containsKey(Globals.PARCELABLE_KEY_APPEAL_PHOTO_URI)) {
                bInitializedBeforeRotation = true;

                String str = savedInstanceState.getString(Globals.PARCELABLE_KEY_APPEAL_PHOTO_URI);
                mUriPhotoAppeal = Uri.parse(str);
            }

            if(  savedInstanceState.containsKey(Globals.PARCELABLE_KEY_EMOJIID) ) {
                bInitializedBeforeRotation = true;

                mEmojiID = savedInstanceState.getInt(Globals.PARCELABLE_KEY_EMOJIID);
            }

            if( savedInstanceState.containsKey(Globals.PARCELABLE_KEY_DRIVER_CABIN_SHOWN) ) {
                mCabinShown = savedInstanceState.getBoolean(Globals.PARCELABLE_KEY_DRIVER_CABIN_SHOWN);

                if( mCabinShown )
                    showCabinView();
            }

            if( !bInitializedBeforeRotation )
                setupNetwork();

        } else {
            if( isConnectedToNetwork() ) {
                wamsInit();
                setupNetwork();
            }
        }

        if( mWiFiUtil == null ) {
            mWiFiUtil = new WiFiUtil(this);
            mWiFiUtil.deletePersistentGroups();
        }

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
            outState.putParcelableArrayList(Globals.PARCELABLE_KEY_PASSENGERS, mPassengers);

            outState.putParcelable(Globals.PARCELABLE_KEY_CURRENT_RIDE, mCurrentRide);

            outState.putBoolean(Globals.PARCELABLE_KEY_CABIN_PICTURES_BUTTON_SHOWN, mCabinPictureButtonShown);

            outState.putIntegerArrayList(Globals.PARCELABLE_KEY_CAPTURED_PASSENGERS_IDS, mCapturedPassengersIDs);

            outState.putSerializable(Globals.PARCELABLE_KEY_PASSENGERS_FACE_IDS, Globals.get_PassengerFaces());

            if( mUriPhotoAppeal != null)
                outState.putString(Globals.PARCELABLE_KEY_APPEAL_PHOTO_URI, mUriPhotoAppeal.toString());

            outState.putInt(Globals.PARCELABLE_KEY_EMOJIID, mEmojiID);

            outState.putBoolean(Globals.PARCELABLE_KEY_DRIVER_CABIN_SHOWN, mCabinShown);
        }

        super.onSaveInstanceState(outState);
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

    private void startAdvertise(String userID,
                                String userName,
                                String rideCode) {

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            BLEUtil bleUtil = new BLEUtil(this);
//            Boolean bleRes = bleUtil.startAdvertise();
//        }

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

        if( !isConnectedToNetwork() ) {
            TextView txtRideCodeLabel =  (TextView)findViewById(R.id.code_label_caption);
            txtRideCodeLabel.setText("");
            mOfflineDialog.show();
        }
        else {

            if( mOfflineDialog.isShowing() ) {
                mOfflineDialog.dismiss();

                setupNetwork();
            }
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

        super.onDestroy();
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

    public void onSubmitRidePics(View view){
        // Process the Faces with Oxford FaceAPI
        // Will be continued on finish() from IPictureURLUpdater
        new faceapiUtils(this).execute();
    }

    AsyncTask<Void, Void, Void> updateOnDeniedCurrentRideTask = new AsyncTask<Void, Void, Void>() {

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
    };

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

    public void onSubmitRide(View v) {

        if( mPassengers.size() < Globals.REQUIRED_PASSENGERS_NUMBER ) {
            showCabinView();
            return;
        }

        if (mCurrentRide == null)
            return;

        boolean bRequestApprovalBySefies = false;

        for(User passenger: Globals.getMyPassengers() ){
            if( passenger.wasSelfPictured() ) {
                bRequestApprovalBySefies = true;
                break;
            }

        }

        if( bRequestApprovalBySefies )
            onSubmitRidePics(v);
        else {

            mCurrentRide.setApproved(Globals.RIDE_STATUS.APPROVED.ordinal());
            updateCurrentRideTask.execute();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.i(LOG_TAG, "onConfigurationChanged");
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

    @Override
    public void onConnected(Bundle bundle) {
        super.onConnected(bundle);
    }

    private void wamsInit() {

        wamsInit(true);

        mRidesTable = getMobileServiceClient().getTable("rides", Ride.class);
    }

    private void setupNetwork() {

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


        mAdvertiseTask = new AsyncTask<Void, Void, Void>() {

            Exception mEx;
            Boolean mRequestSelfie;

            @Override
            protected void onPreExecute() {
                TextView txtRideCodeCaption = (TextView)findViewById(R.id.code_label_caption);
                txtRideCodeCaption.setText(R.string.generating_ride_code);
            }

            @Override
            protected void onPostExecute(Void result) {

                if (mEx == null) {
                    TextView txtRideCodeCaption = (TextView)findViewById(R.id.code_label_caption);
                    txtRideCodeCaption.setText(R.string.ride_code_label);
                    TextView txtRideCode = (TextView) findViewById(R.id.txtRideCode);
                    txtRideCode.setVisibility(View.VISIBLE);

                    String rideCode = mCurrentRide.getRideCode();
                    txtRideCode.setText(rideCode);

                    findViewById(R.id.img_transmit).setVisibility(View.VISIBLE);

                    if (!mCurrentRide.isPictureRequired()) {
                        mImageTransmit.setVisibility(View.VISIBLE);
                        AnimationDrawable animationDrawable = (AnimationDrawable) mImageTransmit.getDrawable();
                        animationDrawable.start();

                        startAdvertise(getUser().getRegistrationId(),
                                getUser().getFullName(),
                                rideCode);

                        getHandler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.submit_ride_button);
                                fab.setVisibility(View.VISIBLE);

                                mTextSwitcher.setText(getString(R.string.instruction_not_enought_passengers));

                                mCabinPictureButtonShown = true;

                            }
                        }, Globals.CABIN_PICTURES_BUTTON_SHOW_INTERVAL);
                    }
                } else {
                    Toast.makeText(DriverRoleActivity.this,
                            mEx.getMessage(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            protected Void doInBackground(Void... voids) {

                try {

                    Ride ride = new Ride();
                    ride.setCreated(new Date());
                    ride.setCarNumber(mCarNumber);
                    ride.setPictureRequiredByDriver(mRequestSelfie);
                    if( mRidesTable != null ) {
                        mCurrentRide = mRidesTable.insert(ride).get();
                    } else {
                        mEx = new Exception("No network");
                    }

                } catch (ExecutionException | InterruptedException ex) {
                    mEx = ex;
                    Log.e(LOG_TAG, ex.getMessage());
                }

                return null;
            }
        }.execute();

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

        mCheckGeoFencesTimer.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                String message = Globals.isInGeofenceArea() ?
                        Globals.getMonitorStatus() :
                        getString(R.string.geofence_outside);

                String currentGeoStatus = Globals.getMonitorStatus();
                if( !currentGeoStatus.equals(message) )
                    mTextSwitcher.setText(message);
            }
        },
        0, // Start immediately
        1, TimeUnit.SECONDS );

    }

    @UiThread
    private void showSubmitPicsButton() {

        FloatingActionButton fab = (FloatingActionButton)findViewById(R.id.submit_ride_button_pics);
        Context ctx =  getApplicationContext();
        if( fab != null) {
            fab.setVisibility(View.VISIBLE);
            fab.setImageDrawable(ContextCompat.getDrawable(ctx, R.drawable.ic_action_done));

            mTextSwitcher.setText(getString(R.string.instruction_can_submit_no_fee));
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
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 )
            getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
    }

    @Override
    protected void setupUI(String title, String subTitle) {
        super.setupUI(title, subTitle);

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
                    .neutralText(R.string.help)
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            AppealCamera();
                        }

                        @Override
                        public void onNegative(MaterialDialog dialog) {
                            finish();
                        }

                        @Override
                        public void onNeutral(MaterialDialog dialog) {
                            Intent intent = new Intent(getApplicationContext(),
                                                        TutorialActivity.class);
                            intent.putExtra(getString(R.string.tutorial_id), Globals.TUTORIAL_Appeal);
                            startActivity(intent);
                        }
                    });

            View customDialog = getLayoutInflater().inflate(R.layout.dialog_appeal, null);
            builder.customView(customDialog, false);

            if( mEmojiID == 0 ){
                mEmojiID =  new Random().nextInt(Globals.NUM_OF_EMOJIS);
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        View rootView = findViewById(R.id.cabin_background_layout);
        String tag = Integer.toString(requestCode);

        if( requestCode == REQUEST_IMAGE_CAPTURE
                && resultCode == RESULT_OK) {

            try {
                MaterialDialog.Builder builder = new MaterialDialog.Builder(this);

                builder.title(R.string.appeal_answer)
                        .iconRes(R.drawable.ic_info)
                        .positiveText(R.string.appeal_send)
                        .negativeText(R.string.appeal_cancel)
                        .neutralText(R.string.appeal_another_picture);

                View customDialog = getLayoutInflater().inflate(R.layout.dialog_appeal_answer, null);
                builder.customView(customDialog, false);

                ImageView imageViewAppeal =  (ImageView)customDialog.findViewById(R.id.imageViewAppeal);
                imageViewAppeal.setImageURI(mUriPhotoAppeal);

                builder.callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        sendAppeal();
                    }


                    @Override
                    public void onNeutral(MaterialDialog dialog) {
                        onAppealCamera();
                    }
                });

                builder.show();

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
                Bitmap bmp = extras.getParcelable(getString(R.string.detection_face_bitmap));
                if( bmp != null) {
                    Drawable drawable = new BitmapDrawable(this.getResources(), bmp);

                    drawable = RoundedDrawable.fromDrawable(drawable);
                    ((RoundedDrawable) drawable)
                            .setCornerRadius(Globals.PICTURE_CORNER_RADIUS)
                            .setBorderColor(Color.WHITE)
                            .setBorderWidth(0)
                            .setOval(true);

                    passengerPicture.setImageDrawable(drawable);
                }

                UUID _faceId  = (UUID)extras.getSerializable(getString(R.string.detection_face_id));
                URI faceURI = (URI)extras.getSerializable(getString(R.string.detection_face_uri));
                addPassengerFace(requestCode - 1, _faceId, faceURI.toString());

            }

            mCapturedPassengersIDs.set(requestCode -1, 1);
        }
    }

    public  void sendAppeal(){
        new wamsAddAppeal(DriverRoleActivity.this,
                            getUser().getFullName(),
                            "appeals",
                            mCurrentRide.Id,
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
                                    Location loc = getCurrentLocation();
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
                    updateOnDeniedCurrentRideTask.execute();

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
