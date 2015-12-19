package com.labs.okey.freeride;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;

import com.labs.okey.freeride.model.Appeal;
import com.labs.okey.freeride.model.User;
import com.labs.okey.freeride.utils.Globals;
import com.labs.okey.freeride.utils.RoundedDrawable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;


public class AppealDetailsActivity extends BaseActivity {

    private static final String LOG_TAG = "FR.AppealDetails";
    Appeal appeal = null;

    ImageView DriverImage;
    ImageView AppealImage;
    TextView carNumber;
    TextView created;
    TextView nameDriver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_appeal_details);

        //setupUI("Ride details", "");
        appeal = (Appeal) getIntent().getSerializableExtra("appeal");

        DriverImage = (ImageView) findViewById(R.id.imageDriver);
        AppealImage = (ImageView) findViewById(R.id.appeal_image);
        carNumber = (TextView) findViewById(R.id.txtCarNumber);
        nameDriver = (TextView) findViewById(R.id.txtNameDriver);
        created = (TextView) findViewById(R.id.txtCreated);

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
                nameDriver.setText(user.getFirstName() + " " + user.getLastName());
            }

        } catch (Exception e) {

            Log.e(LOG_TAG, e.getMessage());
        }

        DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm");
        //carNumber.setText(ride.getCarNumber());

        created.setText(df.format(appeal.getCreatedAt()));


        new AsyncTask<Object, Void, Void>() {
            Drawable drawable = null;

            // Runs on UI thread
            @Override
            protected void onPostExecute(Void res) {
                AppealImage.setImageDrawable(drawable);

            }

            @Override
            protected void onPreExecute() {
                super.onPreExecute();


            }

            @Override
            protected Void doInBackground(Object... objects) {


                    InputStream is = fetch(appeal.getPictureUrl());
                    drawable = Drawable.createFromStream(is, "src");


                return null;
            }
        }.execute();






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


    private InputStream fetch(String urlString){

        InputStream in = null;
        HttpURLConnection urlConnection = null;

        try {

            URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url
                        .openConnection();
            in = urlConnection.getInputStream();

            InputStreamReader isw = new InputStreamReader(in);

            int data = isw.read();
            while (data != -1) {
                char current = (char) data;
                data = isw.read();
                System.out.print(current);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if( urlConnection != null )
                urlConnection.disconnect();
        }

        return in;
      }
}
