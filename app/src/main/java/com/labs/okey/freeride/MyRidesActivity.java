package com.labs.okey.freeride;

import android.content.SharedPreferences;
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
import com.microsoft.windowsazure.mobileservices.table.query.Query;
import com.microsoft.windowsazure.mobileservices.table.sync.MobileServiceSyncTable;

import java.util.ArrayList;
import java.util.List;

public class MyRidesActivity extends BaseActivity
        implements ActionBar.TabListener {

    private static final String             LOG_TAG = "FR.MyRides";

    private MyRideTabAdapter                mTabAdapter;
    private List<Ride>                      mRides;
    private MobileServiceSyncTable<Ride>    mRidesSyncTable;

    SlidingTabLayout                        slidingTabLayout;
    String[]                                titles;
    ViewPager                               mViewPager;

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

        mTabAdapter= new MyRideTabAdapter(getSupportFragmentManager(),
                                          titles, mRides);
        mViewPager.setAdapter(mTabAdapter);

        slidingTabLayout.setViewPager(mViewPager);
        slidingTabLayout.setCustomTabColorizer(new SlidingTabLayout.TabColorizer() {
            @Override
            public int getIndicatorColor(int position) {
                return R.color.ColorAccent;
            }
        });

        mRides = new ArrayList<Ride>();

        updateHistory();
    }

    public void updateHistory(){

        final ProgressBar myRidesProgressRefresh =  (ProgressBar)findViewById(R.id.myrides_progress_refresh);

        new AsyncTask<Object, Void, Void>() {

            // Runs on UI thread
            @Override
            protected void onPostExecute(Void res) {

                SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.putBoolean(Globals.UPDATE_MYRIDES_REQUIRED, Globals.myRides_update_required);
                editor.apply();

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

                    Query pullQueryRides = getMobileServiceClient().getTable(Ride.class)
                            .where().field("driverid").eq(Globals.userID);
                    wamsUtils.sync(getMobileServiceClient(), "rides");

                    if( Globals.myRides_update_required ) {

                        mRidesSyncTable.pull(pullQueryRides).get();
                        Globals.myRides_update_required = false;

                    }

                    mRides = mRidesSyncTable.read(pullQueryRides).get();

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
        Globals.myRides_update_required = true;
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


