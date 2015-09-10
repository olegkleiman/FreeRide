package com.labs.okey.freeride;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;

import com.labs.okey.freeride.adapters.CarpoolingListAdapter;
import com.labs.okey.freeride.model.PassByDate;
import com.labs.okey.freeride.model.User;
import com.labs.okey.freeride.utils.Globals;
import com.labs.okey.freeride.utils.RoundedDrawable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PassengerDetailsActivity extends AppCompatActivity {

    private static final String LOG_TAG = "FR.Main";
    List<PassByDate> lstPassByDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passenger_details);

        User pass = (User) getIntent().getSerializableExtra("pass");

        TextView phonePassenger = (TextView) findViewById(R.id.phonePassenger);
        ImageView passengerAvatar = (ImageView) findViewById(R.id.passengerAvatarView);

        phonePassenger.setText(pass.getPhone());

        try {

            User user = User.load(this);

            //TODO passenger picture
            Drawable drawable =
                    (Globals.drawMan.userDrawable(this,
                            "1",
                            user.getPictureURL())).get();
            if (drawable != null) {
                drawable = RoundedDrawable.fromDrawable(drawable);
                ((RoundedDrawable) drawable)
                        .setCornerRadius(Globals.PICTURE_CORNER_RADIUS)
                        .setBorderColor(Color.WHITE)
                        .setBorderWidth(Globals.PICTURE_BORDER_WIDTH)
                        .setOval(true);

                passengerAvatar.setImageDrawable(drawable);

            }
        }catch (Exception e) {
            //TODO LOG_TAG is from main activity, need new log
                Log.e(LOG_TAG, e.getMessage());
        }


        RecyclerView recycler = (RecyclerView) findViewById(R.id.recyclerCarpooling);
        recycler.setHasFixedSize(true);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setItemAnimator(new DefaultItemAnimator());

        initListPassByDate();
        CarpoolingListAdapter adapter = new CarpoolingListAdapter(this, lstPassByDate);
        recycler.setAdapter(adapter);

    }

    //TODO after we have real data erase this method
    public void initListPassByDate(){
        lstPassByDate = new ArrayList<PassByDate>();


        Date date = new Date();

        PassByDate pass1 = new PassByDate();
        pass1.setAsDriver(true);
        pass1.setDateRide(date);
        lstPassByDate.add(pass1);

        PassByDate pass2 = new PassByDate();
        pass2.setAsDriver(true);
        pass2.setDateRide(date);
        lstPassByDate.add(pass2);

        PassByDate pass3 = new PassByDate();
        pass3.setAsDriver(true);
        pass3.setDateRide(date);
        lstPassByDate.add(pass3);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_passenger_details, menu);
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
}
