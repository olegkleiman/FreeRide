package com.labs.okey.freeride;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.labs.okey.freeride.adapters.PassengerListAdapter;
import com.labs.okey.freeride.model.Appeal;
import com.labs.okey.freeride.model.Ride;
import com.labs.okey.freeride.model.User;
import com.labs.okey.freeride.utils.Globals;
import com.labs.okey.freeride.utils.IRecyclerClickListener;
import com.labs.okey.freeride.utils.RoundedDrawable;
import com.microsoft.windowsazure.mobileservices.MobileServiceList;
import com.microsoft.windowsazure.mobileservices.table.MobileServiceTable;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

public class RideDetailsActivity extends BaseActivity
        implements IRecyclerClickListener {

    private static final String LOG_TAG = "FR.RideDetails";

    ImageView DriverImage;
    ImageView picture;
    TextView carNumber;
    TextView created;
    TextView nameDriver;
    RecyclerView recyclerViewPass;
    TextView titleDetails;
    ProgressBar progressBar;
    RelativeLayout rawLayout;


    List<User> lstPassenger;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ride_details);

        //setupUI("Ride details", "");
        final Ride ride = (Ride) getIntent().getSerializableExtra("ride");

        DriverImage = (ImageView) findViewById(R.id.imageDriver);
        picture = (ImageView) findViewById(R.id.picture);
        carNumber = (TextView) findViewById(R.id.txtCarNumber);
        nameDriver = (TextView) findViewById(R.id.txtNameDriver);
        created = (TextView) findViewById(R.id.txtCreated);
        recyclerViewPass = (RecyclerView)findViewById(R.id.recyclerPassengers);
        titleDetails = (TextView)findViewById(R.id.titleDetails);
        progressBar = (ProgressBar) findViewById(R.id.progressBarAppealPicture);
        // rawLayout = (RelativeLayout) findViewById(R.id.myRideDetail);



        if( ride.getCreated() != null ) {
            DateFormat df = DateFormat.getDateTimeInstance();
            created.setText(df.format(ride.getCreated()));
        }

        carNumber.setText(ride.getCarNumber());
        nameDriver.setText(ride.getDriverName());
        progressBar.setVisibility(View.GONE);

        //if user are driver
        if(ride.getDriverId().equals(Globals.userID)) {


            try {
                User user = User.load(this);

                Drawable drawable =
                        (Globals.drawMan.userDrawable(this,
                                "1",
                                user.getPictureURL())).get();


                drawable = RoundedDrawable.fromDrawable(drawable);
                ((RoundedDrawable) drawable)
                        .setCornerRadius(Globals.PICTURE_CORNER_RADIUS)
                        .setBorderColor(Color.WHITE)
                        .setBorderWidth(Globals.PICTURE_BORDER_WIDTH)
                        .setOval(true);

                DriverImage.setImageDrawable(drawable);

            } catch (Exception e) {

                Log.e(LOG_TAG, e.getMessage());
            }


            int status = 0;
            if(ride.getApproved() != null){
                status = ride.getApproved();
            }

        switch (status) {

            case 0:  //WAIT

                picture.setVisibility(View.GONE);
                recyclerViewPass.setVisibility(View.GONE);
                titleDetails.setVisibility(View.GONE);
                findViewById(R.id.passengers_or_Image_card).setVisibility(View.GONE);
                findViewById(R.id.lineView).setVisibility(View.GONE);



                break;
            case 1:  //APPROVED

                picture.setVisibility(View.GONE);
                titleDetails.setText(R.string.list_of_passengers);

                RecyclerView recycler = (RecyclerView) findViewById(R.id.recyclerPassengers);
                recycler.setHasFixedSize(true);
                recycler.setLayoutManager(new LinearLayoutManager(this));
                recycler.setItemAnimator(new DefaultItemAnimator());


                PassengerListAdapter adapter = new PassengerListAdapter(this, lstPassenger);

                //TODO unComment later
//            adapter.setOnClickListener(new IRecyclerClickListener() {
//                @Override
//                public void clicked(View v, int position) {
//
//
//                    User choosePass = lstPassenger.get(position);
//                    Intent intent = new Intent(getApplicationContext(), PassengerDetailsActivity.class);
//
//                    intent.putExtra("pass", choosePass);
//                    startActivity(intent);
//                }
//            });

                recycler.setAdapter(adapter);

                break;


            case 2:  //APPROVED_BY_SELFY

                findViewById(R.id.recyclerPassengers).setVisibility(View.GONE);
                ((TextView)findViewById(R.id.textViewListPass)).setText(R.string.ride_photo);
                //TODO:  need implementation
                //all picture?

                break;

            case 3:  //DENIED

                picture.setVisibility(View.GONE);
                recyclerViewPass.setVisibility(View.GONE);
                findViewById(R.id.lineView).setVisibility(View.GONE);
                titleDetails.setText(R.string.ride_denied);

                break;

            case 4:  //APPEAL

                recyclerViewPass.setVisibility(View.GONE);
                titleDetails.setText(R.string.appeal_image);


                new AsyncTask<Object, Void, Void>() {
                    Drawable drawable = null;


                    @Override
                    protected void onPostExecute(Void res) {
                        picture.setImageDrawable(drawable);

                        if (progressBar.getVisibility() == View.VISIBLE) {
                            progressBar.setVisibility(View.GONE);
                        }

                    }

                    @Override
                    protected void onPreExecute() {
                        super.onPreExecute();

                        if (progressBar.getVisibility() == View.GONE) {
                            progressBar.setVisibility(View.VISIBLE);
                        }


                    }

                    @Override
                    protected Void doInBackground(Object... objects) {

                        try {

                            wamsInit(false);

                            MobileServiceTable<Appeal> appealsTable = getMobileServiceClient()
                                    .getTable("appeal", Appeal.class);

                            final MobileServiceList<Appeal> mslAppeals =
                                    appealsTable.where().field("rideid").eq(ride.Id).execute().get();

                            List<Appeal> appeals = mslAppeals;
                            Appeal appeal = appeals.get(0);

                            drawable = fetch(appeal.getPictureUrl());

                        } catch (Exception ex) {
                            Log.e(LOG_TAG, ex.getMessage() + " Cause: " + ex.getCause());
                        }
                        return null;
                    }
                }.execute();


                break;
        }

        }
        // user are passenger
        else
        {
            int status;
            if(ride.getApproved() != null){
                status = ride.getApproved();
            }
            else{
                status = 0;
            }
            switch (status)
            {


                case 0:  //WAIT
                case 2:  //APPROVED_BY_SELFY
                case 3:  //DENIED
                case 4:  //APPEAL

                    picture.setVisibility(View.GONE);
                    recyclerViewPass.setVisibility(View.GONE);
                    findViewById(R.id.textViewListPass).setVisibility(View.GONE);
                    findViewById(R.id.passengers_or_Image_card).setVisibility(View.GONE);
                    findViewById(R.id.lineView).setVisibility(View.GONE);

                    break;
                case 1:  //APPROVED

                    picture.setVisibility(View.GONE);

                    RecyclerView recycler = (RecyclerView) findViewById(R.id.recyclerPassengers);
                    recycler.setHasFixedSize(true);
                    recycler.setLayoutManager(new LinearLayoutManager(this));
                    recycler.setItemAnimator(new DefaultItemAnimator());


                    PassengerListAdapter adapter = new PassengerListAdapter(this, lstPassenger);

                    //TODO unComment later
//            adapter.setOnClickListener(new IRecyclerClickListener() {
//                @Override
//                public void clicked(View v, int position) {
//
//                    // TODO:
//                    User choosePass = lstPassenger.get(position);
//                    Intent intent = new Intent(getApplicationContext(), PassengerDetailsActivity.class);
//
//                    intent.putExtra("pass", choosePass);
//                    startActivity(intent);
//                }
//            });

                    recycler.setAdapter(adapter);
                    break;
            }
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


    private Drawable fetch(String urlString){

        InputStream is = null;
        HttpURLConnection urlConnection = null;
        Drawable drawable = null;

        try {

            URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();

            boolean redirect = false;

            urlConnection.connect();
            int responseCode = urlConnection.getResponseCode();

            urlConnection.setInstanceFollowRedirects(true);
            HttpURLConnection.setFollowRedirects(true);

            if( responseCode ==  HttpURLConnection.HTTP_MOVED_TEMP
                    || responseCode == HttpURLConnection.HTTP_MOVED_PERM
                    || responseCode == HttpURLConnection.HTTP_SEE_OTHER) {

                redirect = true;
            }

            if( redirect ) {
                String loc = urlConnection.getHeaderField("Location");
                urlConnection = (HttpURLConnection)new URL(loc).openConnection();
            }

            is = urlConnection.getInputStream();

            drawable = Drawable.createFromStream(is, "src");

            is.close();


        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if( urlConnection != null )
                urlConnection.disconnect();
        }

        return drawable;
    }

    @Override
    public void clicked(View view, int position) {

    }

}
