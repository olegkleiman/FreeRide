package com.labs.okey.freeride;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.labs.okey.freeride.adapters.PassengerListAdapter;
import com.labs.okey.freeride.model.Ride;
import com.labs.okey.freeride.model.User;
import com.labs.okey.freeride.utils.Globals;
import com.labs.okey.freeride.utils.IRecyclerClickListener;
import com.labs.okey.freeride.utils.RoundedDrawable;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class RideDetailsActivity extends BaseActivity
        implements IRecyclerClickListener {

    private static final String LOG_TAG = "FR.RideDetails";

    ImageView DriverImage;
    ImageView SelfieImage;
    TextView carNumber;
    TextView created;
    TextView nameDriver;
    RecyclerView recyclerViewPass;
    RelativeLayout rawLayout;

    Boolean boolPassengersList;

    List<User> lstPassenger;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ride_details);

        //setupUI("Ride details", "");
        Ride ride = (Ride) getIntent().getSerializableExtra("ride");

        DriverImage = (ImageView) findViewById(R.id.imageDriver);
        SelfieImage = (ImageView) findViewById(R.id.selfi);
        carNumber = (TextView) findViewById(R.id.txtCarNumber);
        nameDriver = (TextView) findViewById(R.id.txtNameDriver);
        created = (TextView) findViewById(R.id.txtCreated);
        // rawLayout = (RelativeLayout) findViewById(R.id.myRideDetail);

        try {
            User user = User.load(this);

            Drawable drawable =
                    (Globals.drawMan.userDrawable(this,
                            "1",
                            user.getPictureURL())).get();

            if( drawable != null ) {
                drawable = RoundedDrawable.fromDrawable(drawable);
                ((RoundedDrawable) drawable)
                        .setCornerRadius(Globals.PICTURE_CORNER_RADIUS)
                        .setBorderColor(Color.WHITE)
                        .setBorderWidth(Globals.PICTURE_BORDER_WIDTH)
                        .setOval(true);

                DriverImage.setImageDrawable(drawable);
            }
            else {
                DriverImage.setImageResource(R.drawable.driver50);
            }
        } catch (Exception e) {

            Log.e(LOG_TAG, e.getMessage());
        }

        DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm");

        carNumber.setText(ride.getCarNumber());
        nameDriver.setText(ride.getDriverName());
        created.setText(df.format(ride.getCreated()));
        carNumber.setText(ride.getCarNumber());

        //TODO:  for test
        boolPassengersList = true;
        //------

        if(boolPassengersList == true) {

            SelfieImage.setVisibility(View.GONE);

            RecyclerView recycler = (RecyclerView) findViewById(R.id.recyclerPassengers);
            recycler.setHasFixedSize(true);
            recycler.setLayoutManager(new LinearLayoutManager(this));
            recycler.setItemAnimator(new DefaultItemAnimator());

            initPassenger();

            PassengerListAdapter adapter = new PassengerListAdapter(this, lstPassenger);

            adapter.setOnClickListener(new IRecyclerClickListener() {
                @Override
                public void clicked(View v, int position) {

                    // TODO:
                    User choosePass = lstPassenger.get(position);
                    Intent intent = new Intent(getApplicationContext(), PassengerDetailsActivity.class);

                    intent.putExtra("pass", choosePass);
                    startActivity(intent);
                }
            });

            recycler.setAdapter(adapter);
        }
        else {
            findViewById(R.id.recyclerPassengers).setVisibility(View.GONE);
            ((TextView)findViewById(R.id.textViewListPass)).setText(R.string.ride_photo);
            //TODO:  need implementation
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_ride_details, menu);
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

    private void getPassenger  (){
        lstPassenger = new ArrayList<User>();

//        User pass1 = new User();
//        pass1.setFirstName("aaaa");
//        pass1.setLastName("AAA");
//        //pass1.setPictureURL(R.drawable.passenger64);
//        lstPassenger.add(pass1);
//
//        User pass2 = new User();
//        pass2.setFirstName("bbb");
//        pass2.setLastName("BBB");
//        //pass2.setPictureURL(R.drawable.passenger64);
//        lstPassenger.add(pass2);
//
//        User pass3 = new User();
//        pass3.setFirstName("ccc");
//        pass3.setLastName("CCC");
//        //pass2.setPictureURL(R.drawable.passenger64);
//        lstPassenger.add(pass3);
    }

    //TODO after we have real data erase this method
    private void initPassenger  (){
//        lstPassenger = new ArrayList<User>();
//
//        User pass1 = new User();
//        pass1.setFirstName("aaaa");
//        pass1.setLastName("AAA");
//        //pass1.setPictureURL(R.drawable.passenger64);
//        lstPassenger.add(pass1);
//
//        User pass2 = new User();
//        pass2.setFirstName("bbb");
//        pass2.setLastName("BBB");
//        //pass2.setPictureURL(R.drawable.passenger64);
//        lstPassenger.add(pass2);
//
//        User pass3 = new User();
//        pass3.setFirstName("ccc");
//        pass3.setLastName("CCC");
//        //pass2.setPictureURL(R.drawable.passenger64);
//        lstPassenger.add(pass3);
    }

    @Override
    public void clicked(View view, int position) {

    }

}
