package com.labs.okey.freeride;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.labs.okey.freeride.model.GFCircle;
import com.labs.okey.freeride.model.GeoFence;
import com.labs.okey.freeride.utils.Globals;
import com.labs.okey.freeride.utils.wamsUtils;
import com.microsoft.windowsazure.mobileservices.MobileServiceList;
import com.microsoft.windowsazure.mobileservices.table.query.Query;
import com.microsoft.windowsazure.mobileservices.table.sync.MobileServiceSyncTable;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

public class GFActivity extends BaseActivity
        implements ResultCallback<Status>,
        GoogleApiClient.ConnectionCallbacks,
        android.location.LocationListener,
        OnMapReadyCallback {

    private static final String LOG_TAG = "FR.GFActivity";

    private LocationRequest                     mLocationRequest;
    private Location                            mCurrentLocation;
    private GoogleMap                           mGoogleMap;
    private MobileServiceSyncTable<GeoFence>    mGFencesSyncTable;
    private ArrayList<GFCircle>                 mGFCircles = new ArrayList<GFCircle>();
    private TextView                            mTextSwitcher;
    private Circle                              meCircle;
    private long                                mLastLocationUpdateTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gf);

        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(5 * 1000); // want to get loc every 5 sec
        mLocationRequest.setFastestInterval(3 * 1000); // get it to me sooner, if available
        //mLocationRequest.setSmallestDisplacement(0);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.gf_map);
        mapFragment.getMapAsync(this);

        mTextSwitcher = (TextView) findViewById(R.id.monitor_text);

        wamsInit(false);

        mCurrentLocation = _getCurrentLocation();
        if( mCurrentLocation == null ){
            new MaterialDialog.Builder(this)
                    .title(R.string.location_permission_lacked_title)
                    .content(R.string.location_permission_lacked)
                    .iconRes(R.drawable.ic_exclamation)
                    .positiveText(R.string.ok)
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {

                        }
                    })
                    .show();
        }
        else {
            startLocationUpdates();
        }

        initGeofences();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {

        mGoogleMap = googleMap;
//        mGoogleMap.getUiSettings().setZoomControlsEnabled(true);
//        mGoogleMap.setMyLocationEnabled(true);

    }

    @TargetApi(23)
    private Location _getCurrentLocation() {

        if (Build.VERSION.SDK_INT >= 23) {
            if (ContextCompat.checkSelfPermission(this, "android.permission.ACCESS_FINE_LOCATION")
                    != PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, "android.permission.ACCESS_COARSE_LOCATION")
                            != PackageManager.PERMISSION_GRANTED)
                return null;
        }

        Location location = LocationServices.FusedLocationApi.getLastLocation(getGoogleApiClient());
        if (location != null)
            return location;

        try {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            criteria.setAltitudeRequired(false);
            criteria.setBearingRequired(false);
            criteria.setCostAllowed(true);

            String provider = locationManager.getBestProvider(criteria, true);

            location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ex) {
            Log.e(LOG_TAG, ex.getMessage());
        }

        return location;
    }

    @Override
    public void onResume() {
        super.onResume();

//        if (getGoogleApiClient().isConnected() && !mRequestingLocationUpdates) {
            startLocationUpdates();
//        }
    }

    @Override
    protected void onPause() {

//        if (getGoogleApiClient().isConnected()) {
//
//            stopLocationUpdates();
//        }

        stopLocationUpdates();
        super.onPause();
    }

    private void startLocationUpdates() {

        if (Build.VERSION.SDK_INT >= 23) {
            if (ContextCompat.checkSelfPermission(this, "android.permission.ACCESS_FINE_LOCATION")
                    != PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, "android.permission.ACCESS_COARSE_LOCATION")
                            != PackageManager.PERMISSION_GRANTED)
                return;
        }

        // 1
//        if (getGoogleApiClient().isConnected()) {
//            LocationServices
//                    .FusedLocationApi
//                    .requestLocationUpdates(getGoogleApiClient(),
//                            mLocationRequest,
//                            this);
//        }

        // 2
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

//        Criteria criteria = new Criteria();
//        criteria.setAccuracy(Criteria.ACCURACY_FINE);
//        criteria.setAltitudeRequired(false);
//        criteria.setBearingRequired(false);
//        criteria.setCostAllowed(true);
//        //criteria.setPowerRequirement(Criteria.POWER_HIGH);
//        String bestProvider = locationManager.getBestProvider(criteria, true);

        if( locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) )
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                    0, 0, this);

        if( locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) )
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    Globals.LOCATION_UPDATE_MIN_FEQUENCY, 0, this);

        Globals.setInGeofenceArea(false);
    }

    protected void stopLocationUpdates() {

        if (Build.VERSION.SDK_INT >= 23) {
            if (ContextCompat.checkSelfPermission(this, "android.permission.ACCESS_FINE_LOCATION")
                    != PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, "android.permission.ACCESS_COARSE_LOCATION")
                            != PackageManager.PERMISSION_GRANTED)
                return;
        }

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.removeUpdates(this);
    }

    @Override
    public void onConnected(Bundle bundle) {
//
//        mCurrentLocation = _getCurrentLocation();

        if (mCurrentLocation != null) {
            CameraPosition cameraPosition = new CameraPosition.Builder().target(
                    new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude()))
                    .zoom(15)
                    .build();
            if( mGoogleMap != null)
                mGoogleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    protected void initGeofences() {
        if (!isWamsInitialized())
            return;

        if (mGFencesSyncTable == null)
            mGFencesSyncTable = getMobileServiceClient().getSyncTable("geofences", GeoFence.class);

        new AsyncTask<Object, Void, Void>() {

            MobileServiceList<GeoFence> gFences = null;

            @Override
            protected void onPostExecute(Void result){
                if( gFences == null )
                    return;

                for (GeoFence _gFence : gFences) {
                    if( _gFence.isActive() ) {
                        // About the issues of converting float to double
                        // see here: http://programmingjungle.blogspot.co.il/2013/03/float-to-double-conversion-in-java.html
                        double lat = new BigDecimal(String.valueOf(_gFence.getLat())).doubleValue();
                        double lon = new BigDecimal(String.valueOf(_gFence.getLon())).doubleValue();
                        int radius = Math.round(_gFence.getRadius());
                        LatLng latLng = new LatLng(lat, lon);

                        GFCircle gfCircle = new GFCircle(lat, lon,
                                                         radius,
                                                        _gFence.getLabel());
                        mGFCircles.add(gfCircle);

                        CircleOptions circleOpt = new CircleOptions()
                                .center(latLng)
                                .radius(radius)
                                .strokeColor(Color.CYAN)
                                .fillColor(Color.TRANSPARENT);
                        mGoogleMap.addCircle(circleOpt);
                    }
                }

                String msg = getGFenceForLocation(mCurrentLocation);
                mTextSwitcher.setText(msg);

            }

            @Override
            protected Void doInBackground(Object... params) {

                try {

                    wamsUtils.sync(getMobileServiceClient(), "geofences");

                    Query pullQuery = getMobileServiceClient().getTable(GeoFence.class).where();
                    gFences = mGFencesSyncTable.read(pullQuery).get();

                } catch (Exception ex) {
                    Log.e(LOG_TAG, ex.getMessage() + " Cause: " + ex.getCause());
                }

                return null;
            }
        }.execute();

    }

    private String getGFenceForLocation(Location location) {

        String strStatus = getString(R.string.geofence_outside_title_debug);
        if( mCurrentLocation == null )
            return strStatus;

        Boolean bInsideGeoFences = false;

        long start = System.currentTimeMillis();
        for(GFCircle circle : mGFCircles) {
            float[] res = new float[3];
            Location.distanceBetween(mCurrentLocation.getLatitude(),
                    mCurrentLocation.getLongitude(),
                    circle.getX(),
                    circle.getY(),
                    res);
            if (res[0] < circle.getRadius()) {

                bInsideGeoFences = true;
                strStatus = circle.getTag();

                break;
            }
        }
        long elapsed = System.currentTimeMillis() - start;

        if (location.hasAccuracy()) {
            strStatus += String.format(" %s %f %f (%.1f) [%d]",
                    location.getProvider(),
                    location.getLatitude(),
                    location.getLongitude(),
                    location.getAccuracy(),
                    elapsed);
        } else {
            strStatus += String.format(" %s %f %f [%d]",
                    location.getProvider(),
                    location.getLatitude(),
                    location.getLongitude(),
                    elapsed);
        }

        String lastUpdateTime = DateFormat.getTimeInstance().format(new Date());
        strStatus += " " + lastUpdateTime;

        Globals.setInGeofenceArea(bInsideGeoFences);

        return strStatus;
    }

    //
    // Implementation of LocationListener
    //
    @Override
    public void onLocationChanged(Location location) {

        if( !isAccurate(location) ) {
            Log.i(LOG_TAG, "Location skipped");
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

        String msg = getGFenceForLocation(location);

        String msgRepeat = "(R) " + mTextSwitcher.getText().toString();

        if( Globals.isInGeofenceArea() ) {
            mLastLocationUpdateTime = System.currentTimeMillis();
        } else {
            long elapsed = System.currentTimeMillis() - mLastLocationUpdateTime;
            if( mLastLocationUpdateTime != 0 // for the first-time
                    && elapsed < Globals.GF_OUT_TOLERANCE ) {
                Globals.setInGeofenceArea(true);
                msg = msgRepeat;
            }
        }

        mTextSwitcher.setText(msg);

    }

    protected boolean isAccurate(Location loc){

        return loc.hasAccuracy() && loc.getAccuracy() < Globals.MIN_ACCURACY;

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

    public void onResult(Status status) {

    }


}
