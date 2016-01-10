package com.labs.okey.freeride;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;

import com.labs.okey.freeride.adapters.MyRideTabAdapter;
import com.labs.okey.freeride.model.Ride;
import com.labs.okey.freeride.utils.Globals;
import com.labs.okey.freeride.utils.wamsUtils;
import com.labs.okey.freeride.views.SlidingTabLayout;
import com.microsoft.windowsazure.mobileservices.MobileServiceList;
import com.microsoft.windowsazure.mobileservices.table.MobileServiceTable;
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

        if (Globals.userID == null ||
                Globals.userID.isEmpty()) {
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            Globals.userID = sharedPrefs.getString(Globals.USERIDPREF, "");
        }

        wamsInit(false);
        mRidesSyncTable = getMobileServiceClient().getSyncTable("rides", Ride.class);


        setupUI(getString(R.string.subtitle_activity_my_rides), "");

        titles = getResources().getStringArray(R.array.my_rides_titles);

        mViewPager = (ViewPager) findViewById(R.id.viewpager);
        slidingTabLayout = (SlidingTabLayout) findViewById(R.id.sliding_tabs);


        //TODO the array is empty, please implement with cache table
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
        updateHistory();
    }


    public void updateHistory(){

        // final ProgressBar gen_progress_refresh = (ProgressBar)findViewById(R.id.gen_fragment_progress_refresh);
        // final ProgressBar rej_progress_refresh = (ProgressBar)findViewById(R.id.rej_fragment_progress_refresh);

        final ProgressBar myRidesProgressRefresh =  (ProgressBar)findViewById(R.id.myrides_progress_refresh);


        new AsyncTask<Object, Void, Void>() {



            // Runs on UI thread
            @Override
            protected void onPostExecute(Void res) {

                if (myRidesProgressRefresh.getVisibility() == View.VISIBLE) {
                    myRidesProgressRefresh.setVisibility(View.GONE);
                }
                mTabAdapter.updateRides(mRides);
            }

            @Override
            protected void onPreExecute() {
                super.onPreExecute();

                if (myRidesProgressRefresh.getVisibility() == View.GONE) {
                    myRidesProgressRefresh.setVisibility(View.VISIBLE);
                }
            }

            @Override
            protected Void doInBackground(Object... objects) {

                try {

                    wamsUtils.sync(getMobileServiceClient(), "rides");

                    Query pullQueryRides = getMobileServiceClient().getTable(Ride.class)
                            .where().field("driverid").eq(Globals.userID);
                    mRidesSyncTable.pull(pullQueryRides).get();

                    final MobileServiceList<Ride> ridesList = mRidesSyncTable.read(pullQueryRides).get();

                    mRides = ridesList;

                    Globals.f_update_required = false;

                } catch (Exception ex) {
                    Log.e(LOG_TAG, ex.getMessage() + " Cause: " + ex.getCause());
                }

                return null;
            }
        }.execute();

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_my_rides, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();
        if (id == R.id.action_refresh_history) {
            onRefresh();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void onRefresh() {
        updateHistory();
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

}


