package com.labs.okey.freeride;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.CallSuper;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.labs.okey.freeride.adapters.PassengersAdapter;
import com.labs.okey.freeride.adapters.WiFiPeersAdapter2;
import com.labs.okey.freeride.model.GlobalSettings;
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
import com.labs.okey.freeride.utils.WAMSVersionTable;
import com.labs.okey.freeride.utils.WiFiUtil;
import com.microsoft.windowsazure.mobileservices.MobileServiceList;
import com.microsoft.windowsazure.mobileservices.table.MobileServiceTable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
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
        ResultCallback<Status> // for geofences' callback
{

    private static final String LOG_TAG = "FR.Driver";

    Ride mCurrentRide;

    PassengersAdapter mPassengersAdapter;
    private List<User> mPassengers = new ArrayList<>();
    private int mLastPassengersLength;

    WiFiUtil mWiFiUtil;

    TextView mTxtMonitorStatus;
    RecyclerView mPeersRecyclerView;

    String mCarNumber;

    ImageView mImageTransmit;

    private MobileServiceTable<Ride> ridesTable;
    private MobileServiceTable<GlobalSettings> settingsTable;

    CountDownTimer mDiscoveryTimer;
    ScheduledExecutorService mCheckPasengersTimer =
            Executors.newScheduledThreadPool(1);
    AsyncTask<Void, Void, Void> mAdvertiseTask;

    final int WIFI_CONNECT_REQUEST = 100;// request code for starting WiFi connection
    // handled  in onActivityResult

    private Handler handler = new Handler(this);

    public Handler getHandler() {
        return handler;
    }

    @Override
    @CallSuper
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_role);

        setupUI(getString(R.string.title_activity_driver_role), "");
        wamsInit(true);

        // Keep device awake when advertising fow Wi-Fi Direct
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        settingsTable = getMobileServiceClient().getTable("globalsettings", GlobalSettings.class);
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

        ridesTable = getMobileServiceClient().getTable("rides", Ride.class);
        mAdvertiseTask = new AsyncTask<Void, Void, Void>() {

            Exception mEx;
            Boolean mRequestSelfie;


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
                    mCurrentRide = ridesTable.insert(ride).get();

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

                if( mLastPassengersLength != passengers.size() ) {

                    mLastPassengersLength = passengers.size();

                    // Update UI on UI thread
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mPassengers.clear();
                            mPassengers.addAll(passengers);

                            mPassengersAdapter.notifyDataSetChanged();

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
        if (carsSet != null) {
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

        mWiFiUtil = new WiFiUtil(this);
        mWiFiUtil.deletePersistentGroups();

        new Thread() {
            @Override
            public void run() {

                try {

                    while (true) {

                        runOnUiThread(new Runnable() {

                            @Override
                            public void run() {

                                String message = Globals.isInGeofenceArea() ?
                                        Globals.getMonitorStatus() :
                                        getString(R.string.geofence_outside);

                                mTxtMonitorStatus.setText(message);

                            }
                        });

                        Thread.sleep(1000);
                    }
                } catch (InterruptedException ex) {
                    Log.e(LOG_TAG, ex.getMessage());
                }

            }
        }.start();
    }

    private void prepareLayoutForDriverPictures() {
//        TextView txtCaption = (TextView)findViewById(R.id.code_label_caption);
//        txtCaption.setText(R.string.discover_devices);
//
        findViewById(R.id.drive_internal_layout).setVisibility(View.GONE);
        findViewById(R.id.driver_status_layout).setVisibility(View.GONE);
        findViewById(R.id.status_strip).setVisibility(View.GONE);
//        findViewById(R.id.img_transmit).setVisibility(View.GONE);
//        findViewById(R.id.submit_ride_button).setVisibility(View.GONE);
//        findViewById(R.id.btnRefresh).setVisibility(View.GONE);
//
        findViewById(R.id.cabin_background_layout).setVisibility(View.VISIBLE);
//        findViewById(R.id.progress_refresh).setVisibility(View.VISIBLE);
//
    }


//    private void discoverPassengers() {
//
//        mWiFiUtil.startRegistrationAndDiscovery(this,
//                "", "", "",
//                getHandler(),
//                500);
//
//        mDiscoveryTimer = new CountDownTimer(Globals.DRIVER_DISCOVERY_PERIOD * 1000, 1000){
//
//            @Override
//            public void onTick(long millisUntilFinished) {
//                int rest = (int) (millisUntilFinished / 1000);
//                Log.i(LOG_TAG, String.format("Left %d sec. for discovering", rest));
//                Log.i(LOG_TAG, String.format("Discovered %d passengers", passengers.size()));
//
//                if( passengers.size() >= Globals.REQUIRED_PASSENGERS_NUMBER) {
//                    prepareLayoutForAdvertising();
//
//                    mAdvertiseTask.execute();
//                }
//
//            }
//
//            @Override
//            public void onFinish() {
//                mWiFiUtil.stopDiscovery();
//
//                mAdvertiseTask.execute();
//
//            }
//        }.start();
//    }

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

        mWiFiUtil.registerReceiver(this);
    }

    @Override
    @CallSuper
    public void onPause() {

        mWiFiUtil.unregisterReceiver();
        mWiFiUtil.stopDiscovery();

        Globals.clearMyPassengerIds();
        Globals.clearMyPassengers();

        super.onPause();
    }

    @Override
    @CallSuper
    protected void onStop() {
        mWiFiUtil.removeGroup();
        super.onStop();
    }

    public void onButtonPassengerCamera(View v) {
        Intent intent = new Intent(this,
                CameraCVActivity.class);

        try {
            Object tag = v.getTag();
            if (tag != null) {
                int requestCode = Integer.valueOf((String) tag);
                startActivityForResult(intent, requestCode);
            }
        } catch(Exception e) {
          Log.e(LOG_TAG, e.getMessage());
        }
    }

    public void onButtonSubmitRide(View v) {

        if( mPassengers.size() == 0 ) {
            prepareLayoutForDriverPictures();
            return;
        }

        if (mCurrentRide == null)
            return;

        mCurrentRide.setApproved(Globals.RIDE_STATUS.APPROVED.ordinal());

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... voids) {

                try {
                    ridesTable.update(mCurrentRide).get();
                } catch (InterruptedException | ExecutionException e) {
                    Log.e(LOG_TAG, e.getMessage());
                }
                return null;
            }
        }.execute();
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

    @Override
    protected void setupUI(String title, String subTitle) {
        super.setupUI(title, subTitle);

        mImageTransmit = (ImageView) findViewById(R.id.img_transmit);

        mPeersRecyclerView = (RecyclerView) findViewById(R.id.recyclerViewPeers);
        mPeersRecyclerView.setHasFixedSize(true);
        mPeersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mPeersRecyclerView.setItemAnimator(new DefaultItemAnimator());

        mPassengersAdapter = new PassengersAdapter(this,
                                            R.layout.peers_header,
                                            R.layout.row_passenger,
                                            mPassengers);
        mPeersRecyclerView.setAdapter(mPassengersAdapter);

        mTxtMonitorStatus = (TextView) findViewById(R.id.status_monitor);
        Globals.setMonitorStatus(getString(R.string.geofence_outside_title));

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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        View rootView = findViewById(R.id.cabin_background_layout);
        String tag = Integer.toString(requestCode);
        FloatingActionButton passengerPicture = (FloatingActionButton)rootView.findViewWithTag(tag);

        switch(requestCode ){
            case WIFI_CONNECT_REQUEST: {

                // if( resultCode == RESULT_OK ) {
                // How to distinguish between successful connection
                // and just pressing back from there?
                wamsInit(true);
            }
            break;

            case 1: { // picture from passenger 1

            }
            break;

            case 2: { // picture from passenger 2

            }
            break;

            case 3: { // picture from passenger 3

            }
            break;

            case 4: { // picture from passenger 4

            }
            break;

        }
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
        Thread handler = null;

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

    public void onDebug(View view) {
//        LinearLayout layout = (LinearLayout) findViewById(R.id.debugLayout);
//        int visibility = layout.getVisibility();
//        if (visibility == View.VISIBLE)
//            layout.setVisibility(View.GONE);
//        else
//            layout.setVisibility(View.VISIBLE);
    }

}
