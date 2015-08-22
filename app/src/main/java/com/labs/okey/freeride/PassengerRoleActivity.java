package com.labs.okey.freeride;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.labs.okey.freeride.utils.Globals;

public class PassengerRoleActivity extends BaseActivity {

    TextView mTxtMonitorStatus;
    private static final String LOG_TAG = "FR.Passenger";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passenger_role);

        setupUI(getString(R.string.title_activity_passenger_role), "");


    }

    protected void setupUI(String title, String subTitle){
        super.setupUI(title, subTitle);

        RecyclerView driversRecycler = (RecyclerView)findViewById(R.id.recyclerViewDrivers);
        driversRecycler.setHasFixedSize(true);
        driversRecycler.setLayoutManager(new LinearLayoutManager(this));
        driversRecycler.setItemAnimator(new DefaultItemAnimator());

        mTxtMonitorStatus = (TextView)findViewById(R.id.status_monitor);
        Globals.setMonitorStatus(getString(R.string.geofence_outside));

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_passenger_role, menu);
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
        } else if ( id == R.id.action_camera_cv) {
            onCameraCV(null);
        }

        return super.onOptionsItemSelected(item);
    }

    public void onCameraCV(View view) {

        //String rideCode = ((TextView)findViewById(R.id.txtRideCode)).getText().toString();

        Intent intent = new Intent(this, CameraCVActivity.class);
        //intent.putExtra("rideCode", rideCode);
        startActivity(intent);
    }
}
