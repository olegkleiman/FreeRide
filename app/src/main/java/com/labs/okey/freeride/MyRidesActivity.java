package com.labs.okey.freeride;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.labs.okey.freeride.adapters.MyRideTabAdapter;
import com.labs.okey.freeride.model.Ride;
import com.labs.okey.freeride.utils.Globals;
import com.labs.okey.freeride.utils.wamsUtils;
import com.labs.okey.freeride.views.SlidingTabLayout;
import com.microsoft.windowsazure.mobileservices.MobileServiceList;
import com.microsoft.windowsazure.mobileservices.table.query.Query;
import com.microsoft.windowsazure.mobileservices.table.sync.MobileServiceSyncTable;

import java.util.ArrayList;
import java.util.List;

public class MyRidesActivity extends BaseActivity
        implements ActionBar.TabListener {

    private static final String LOG_TAG = "FR.MyRides";

    MyRideTabAdapter mTabAdapter;
    private String titles[];
    List<Ride> mRides;
    ViewPager mViewPager;
    SlidingTabLayout slidingTabLayout;

    private MobileServiceSyncTable<Ride> mRidesSyncTable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_rides);

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        Globals.userID = sharedPrefs.getString(Globals.USERIDPREF, "");

        wamsInit(false);
        mRidesSyncTable = getMobileServiceClient().getSyncTable("rides", Ride.class);

        new AsyncTask<Object, Void, Void>() {

            // Runs on UI thread
            @Override
            protected void onPostExecute(Void res) {
                mTabAdapter.updateRides(mRides);
            }

            @Override
            protected Void doInBackground(Object... objects) {

                try {

                    wamsUtils.sync(getMobileServiceClient(), "rides");
                    String userID = Globals.userID;

                    Query pullQuery = getMobileServiceClient().getTable(Ride.class)
                            .where().field("driverid").eq(userID);
                    mRidesSyncTable.pull(pullQuery).get();

                    final MobileServiceList<Ride> ridesList = mRidesSyncTable.read(pullQuery).get();
                    mRides = ridesList;

//                   mTabAdapter.notifyDataSetChanged();

//                    runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            //mRidesAdapter.clear();
//
//                            for(Ride _ride : ridesList) {
//                                Log.d(LOG_TAG, _ride.getRideCode());
//                                //mRidesAdapter.add(_ride);
//                            }
//                        }
//                    });


                } catch (Exception ex) {
                    Log.e(LOG_TAG, ex.getMessage() + " Cause: " + ex.getCause());
                }

                return null;
            }
        }.execute();

        setupUI(getString(R.string.subtitle_activity_my_rides), "");

        titles = getResources().getStringArray(R.array.my_rides_titles);

        mViewPager = (ViewPager) findViewById(R.id.viewpager);
        slidingTabLayout = (SlidingTabLayout) findViewById(R.id.sliding_tabs);

        // Read the rides from local cache
//        new AsyncTask<Object, Void, Void>() {
//
//            @Override
//            protected void onPostExecute(Void result){
//                mViewPager.setAdapter(new MyRideTabAdapter(getSupportFragmentManager(),
//                        titles, mRides));
//            }
//
//            @Override
//            protected Void doInBackground(Object... objects) {
//
//                try {
//                    SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
//                    String userID = sharedPrefs.getString(Globals.USERIDPREF, "");
//
//                    Query pullQuery = getMobileServiceClient().getTable(Ride.class)
//                            .where().field("driverid").eq(userID);
//                    final MobileServiceList<Ride> ridesList = mRidesSyncTable.read(pullQuery).get();
//                    mRides = ridesList;
//
//                } catch(InterruptedException | ExecutionException ex) {
//                    Log.e(LOG_TAG, ex.getMessage());
//                }
//
//                return null;
//            }
//        }.execute();



        mRides = new ArrayList<Ride>();
        mTabAdapter= new MyRideTabAdapter(getSupportFragmentManager(),
                titles, mRides);
        mViewPager.setAdapter(mTabAdapter);

        slidingTabLayout.setViewPager(mViewPager);
        slidingTabLayout.setCustomTabColorizer(new SlidingTabLayout.TabColorizer() {
            @Override
            public int getIndicatorColor(int position) {
                return Color.WHITE;
            }
        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_my_rides, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, android.support.v4.app.FragmentTransaction fragmentTransaction) {

    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, android.support.v4.app.FragmentTransaction fragmentTransaction) {

    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, android.support.v4.app.FragmentTransaction fragmentTransaction) {

    }

//    @Override
//    public void clicked(View   view, int position) {
//        Toast.makeText(this, "Yaaaa", Toast.LENGTH_LONG).show();
//
//
//        Intent intent = new Intent(this, DriverRoleActivity.class);
//        //intent.putExtra("aaa",);
//        startActivity(intent);
//
//    }
}
