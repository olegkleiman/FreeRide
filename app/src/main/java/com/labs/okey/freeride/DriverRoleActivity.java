package com.labs.okey.freeride;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.labs.okey.freeride.utils.Globals;

public class DriverRoleActivity extends BaseActivity {

    private static final String LOG_TAG = "FR.Driver";

    TextView mTxtStatus;
    TextView mTxtMonitorStatus;
    RecyclerView mPeersRecyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_role);

        setupUI(getString(R.string.title_activity_driver_role), "");
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

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void setupUI(String title, String subTitle){
        super.setupUI(title, subTitle);

        mTxtStatus = (TextView)findViewById(R.id.txtStatus);
        mPeersRecyclerView = (RecyclerView)findViewById(R.id.recyclerViewPeers);
        mPeersRecyclerView.setHasFixedSize(true);
        mPeersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mPeersRecyclerView.setItemAnimator(new DefaultItemAnimator());

//        mPeersAdapter = new WiFiPeersAdapter2(this, R.layout.peers_header, peers);
//        mPeersRecyclerView.setAdapter(mPeersAdapter);

        mTxtMonitorStatus = (TextView)findViewById(R.id.status_monitor);
        Globals.setMonitorStatus(getString(R.string.geofence_outside));

    }
}
