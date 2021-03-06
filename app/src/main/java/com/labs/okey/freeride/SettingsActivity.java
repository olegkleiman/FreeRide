package com.labs.okey.freeride;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.labs.okey.freeride.adapters.CarsAdapter;
import com.labs.okey.freeride.model.GeoFence;
import com.labs.okey.freeride.model.RegisteredCar;
import com.labs.okey.freeride.model.User;
import com.labs.okey.freeride.utils.Globals;
import com.labs.okey.freeride.utils.RoundedDrawable;
import com.labs.okey.freeride.utils.WAMSVersionTable;
import com.labs.okey.freeride.utils.wamsUtils;
import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.MobileServiceList;
import com.microsoft.windowsazure.mobileservices.table.MobileServiceTable;
import com.microsoft.windowsazure.mobileservices.table.query.Query;
import com.microsoft.windowsazure.mobileservices.table.sync.MobileServiceSyncTable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class SettingsActivity extends BaseActivity
        implements WAMSVersionTable.IVersionMismatchListener{

    List<RegisteredCar> mCars;
    CarsAdapter mCarsAdapter;
    User mUser;

    private EditText mCarInput;
    private EditText mCarNick;

    private static final String LOG_TAG = "FR.Settings";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        setupUI(getString(R.string.title_activity_settings), "");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.menu_settings, menu);

        try {
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            sharedPrefs.getBoolean(Globals.PREF_DEBUG_WITHOUT_GEOFENCES, Globals.DEBUG_WITHOUT_GEOFENCES);
            MenuItem menuItem = menu.findItem(R.id.action_debug_without_geofences);
            menuItem.setChecked(Globals.DEBUG_WITHOUT_GEOFENCES);
        } catch(Exception ex) {
            Log.e(LOG_TAG, ex.getMessage());
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();
        if (id == R.id.action_refresh_settings) {
            onRefreshGeofences();
            return true;
        } else if( id == R.id.action_refresh_classifiers) {
            onRefreshClassifiers();
        } else if( id == R.id.action_debug_without_geofences) {
            Globals.DEBUG_WITHOUT_GEOFENCES = !Globals.DEBUG_WITHOUT_GEOFENCES;
            item.setChecked(Globals.DEBUG_WITHOUT_GEOFENCES);

            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            SharedPreferences.Editor editor = sharedPrefs.edit();

            editor.putBoolean(Globals.PREF_DEBUG_WITHOUT_GEOFENCES, Globals.DEBUG_WITHOUT_GEOFENCES);
            editor.apply();
        } else if( id == R.id.action_debug_update_version ) {
            WAMSVersionTable versionTable = new WAMSVersionTable(this, this);
            versionTable.execute();
        }

        return super.onOptionsItemSelected(item);
    }

    //
    // Implementation of IVersionMismatchListener
    //
    public void mismatch(int major, int minor, final String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        //intent.setDataAndType(Uri.parse(url), "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

    }

    public void match() {

    }

    public void connectionFailure(Exception ex) {

        if( ex != null ) {

            View v = findViewById(R.id.drawer_layout);
            Snackbar.make(v, ex.getMessage(), Snackbar.LENGTH_LONG);
        }

    }

    private void displayUser() {
        mUser = getUser();

        ImageView userPicture = (ImageView)findViewById(R.id.imageProfileView);

        Drawable drawable = null;
        try {

            TextView txtView = (TextView)findViewById(R.id.textUserName);
            txtView.setText(String.format("%s %s", mUser.getFirstName(), mUser.getLastName()));

            txtView = (TextView)findViewById(R.id.textUserEmail);
            txtView.setText(mUser.getEmail());

            txtView = (TextView)findViewById(R.id.textUserPhone);
            txtView.setText(mUser.getPhone());

            drawable = (Globals.drawMan.userDrawable(this,
                    "1",
                    mUser.getPictureURL())).get();
            drawable = RoundedDrawable.fromDrawable(drawable);
            ((RoundedDrawable) drawable)
                    .setCornerRadius(Globals.PICTURE_CORNER_RADIUS)
                    .setBorderColor(Color.LTGRAY)
                    .setBorderWidth(Globals.PICTURE_BORDER_WIDTH)
                    .setOval(true);

            userPicture.setImageDrawable(drawable);
        } catch(InterruptedException | ExecutionException ex) {
            Log.e(LOG_TAG, ex.getMessage());
        }

    }

    @Override
    protected void setupUI(String title, String subTitle) {
        super.setupUI(title, subTitle);

        displayUser();

        try{

            mCars = new ArrayList<>();
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            Set<String> carsSet = sharedPrefs.getStringSet(Globals.CARS_PREF, new HashSet<String>());
            if( carsSet != null ) {
                Iterator<String> iterator = carsSet.iterator();
                while (iterator.hasNext()) {
                    String strCar = iterator.next();

                    String[] tokens = strCar.split("~");
                    RegisteredCar car = new RegisteredCar();
                    car.setCarNumber(tokens[0]);
                    if( tokens.length > 1 )
                        car.setCarNick(tokens[1]);
                    mCars.add(car);
                }
            }

            ListView listView = (ListView)findViewById(R.id.carsListView);
            mCarsAdapter = new CarsAdapter(this, R.layout.car_item_row, mCars);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent,
                                        final View view,
                                        int position, long id) {

                    final RegisteredCar currentCar =  mCarsAdapter.getItem(position);

                    MaterialDialog dialog = new MaterialDialog.Builder(SettingsActivity.this)
                            .title(R.string.edit_car_dialog_caption)
                            .customView(R.layout.dialog_add_car, true)
                            .positiveText(R.string.edit_car_button_save)
                            .negativeText(R.string.cancel)
                            .neutralText(R.string.edit_car_button_delete)
                            .autoDismiss(false)
                            .cancelable(true)
                            .callback(new MaterialDialog.ButtonCallback() {
                                @Override
                                public void onPositive(MaterialDialog dialog) {

                                    String strCarNumber = mCarInput.getText().toString();
                                    if( strCarNumber.length() < 7 ) {
                                        mCarInput.setError(getString(R.string.car_number_validation_error));
                                        return;
                                    }

                                    mCars.remove(currentCar);

                                    String carNick = mCarNick.getText().toString();

                                    RegisteredCar registeredCar = new RegisteredCar();
                                    registeredCar.setCarNumber(strCarNumber);
                                    registeredCar.setCarNick(carNick);

                                    mCars.add(registeredCar);

                                    // Adapter's items will be updated since underlaying list changes
                                    mCarsAdapter.notifyDataSetChanged();

                                    saveCars();


                                    dialog.dismiss();
                                }

                                @Override
                                public void onNegative(MaterialDialog dialog) {
                                    dialog.dismiss();
                                }


                                @Override
                                public void onNeutral(MaterialDialog dialog) {
                                    String carNumber = mCarInput.getText().toString();

                                    RegisteredCar carToRemove = null;
                                    for(RegisteredCar car : mCars) {
                                        if( car.getCarNumber().equals(carNumber) ) {
                                            carToRemove = car;
                                        }
                                    }

                                    if( carToRemove!= null ) {

                                        mCarsAdapter.remove(carToRemove);
                                        mCarsAdapter.notifyDataSetChanged();

                                        saveCars();
                                    }
                                    dialog.dismiss();
                                }
                            })
                            .build();
                    mCarInput = (EditText) dialog.getCustomView().findViewById(R.id.txtCarNumber);
                    mCarInput.setText(currentCar.getCarNumber());
                    mCarNick = (EditText) dialog.getCustomView().findViewById(R.id.txtCarNick);
                    mCarNick.setText(currentCar.getCarNick());

                    dialog.show();

                }
            });
            listView.setAdapter(mCarsAdapter);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void onRefreshClassifiers() {
        new AsyncTask<Void, Void, Void>() {

            Exception mEx;
            MaterialDialog progress;

            @Override
            protected void onPreExecute() {
                progress = new MaterialDialog.Builder(SettingsActivity.this)
                        .title(getString(R.string.download_classifiers_desc))
                        .content(R.string.please_wait)
                        .progress(true, 0)
                        .show();
            }

            @Override
            protected void onPostExecute(Void result){
                progress.dismiss();

                String msg = "Classifiers updated";

                if( mEx != null ) {
                    msg = mEx.getMessage() + " Cause: " + mEx.getCause();
                }

                Toast.makeText(SettingsActivity.this, msg,
                        Toast.LENGTH_LONG).show();
            }

            @Override
            protected Void doInBackground(Void... voids) {

                try {
                    URL url = new URL(Globals.CASCADE_URL);
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setRequestMethod("GET");
                    urlConnection.connect();

                    String cascadeName = Uri.parse(Globals.CASCADE_URL).getLastPathSegment();

                    //set the path where we want to save the file
                    File file = new File(getFilesDir(), cascadeName);
                    FileOutputStream fileOutput = new FileOutputStream(file);

                    InputStream inputStream = urlConnection.getInputStream();

                    byte[] buffer = new byte[1024];
                    int bufferLength = 0;

                    while ( (bufferLength = inputStream.read(buffer)) > 0 ) {
                        fileOutput.write(buffer, 0, bufferLength);
                    }
                    fileOutput.close();

                    Globals.setCascadePath(file.getAbsolutePath());

                } catch (IOException ex) {
                    Log.e(LOG_TAG, ex.getMessage() + " Cause: " + ex.getCause());
                    mEx = ex;
                }

                return null;
            }
        }.execute();
    }

    void onRefreshGeofences() {

        new AsyncTask<Void, Void, Void>() {

            Exception mEx;
            MaterialDialog progress;

            @Override
            protected void onPreExecute() {
                progress = new MaterialDialog.Builder(SettingsActivity.this)
                        .title(getString(R.string.download_geofences_desc))
                        .content(R.string.please_wait)
                        .progress(true, 0)
                        .show();
            }

            @Override
            protected void onPostExecute(Void result){
                progress.dismiss();

                String msg = "Geofences updated";

                if( mEx != null ) {
                    msg = mEx.getMessage() + " Cause: " + mEx.getCause();
                }

                Toast.makeText(SettingsActivity.this, msg,
                        Toast.LENGTH_LONG).show();
            }

            @Override
            protected Void doInBackground(Void... voids) {

                try {
                    MobileServiceClient wamsClient =
                            new MobileServiceClient(
                                    Globals.WAMS_URL,
                                    Globals.WAMS_API_KEY,
                                    getApplicationContext());

                    MobileServiceSyncTable<GeoFence> gFencesSyncTable = wamsClient.getSyncTable("geofences",
                            GeoFence.class);
                    MobileServiceTable<GeoFence> gFencesTbl = wamsClient.getTable(GeoFence.class);

                    wamsUtils.sync(wamsClient, "geofences");

                    Query pullQuery = gFencesTbl.where().field("isactive").ne(false);
                    gFencesSyncTable.purge(pullQuery);
                    gFencesSyncTable.pull(pullQuery).get();

                    // TEST
                    MobileServiceList<GeoFence> gFences
                            = gFencesSyncTable.read(pullQuery).get();
                    for (GeoFence _gFence : gFences) {
                        double lat = _gFence.getLat();
                        double lon = _gFence.getLon();
                        String label = _gFence.getLabel();
                        String[] tokens = label.split(":");
                        if( tokens.length > 1 )
                            Log.i(LOG_TAG, "GFence: " + tokens[0] + " " + tokens[1]);
                        Log.i(LOG_TAG, "GFence: " + lat + " " + lon);
                    }

                } catch(MalformedURLException | InterruptedException | ExecutionException ex ) {
                    Log.e(LOG_TAG, ex.getMessage() + " Cause: " + ex.getCause());
                    mEx = ex;
                }

                return null;
            }
        }.execute();


    }

    public void onAddCar(View view){

        MaterialDialog dialog = new MaterialDialog.Builder(this)
                .title(R.string.add_car_dialog_caption)
                .customView(R.layout.dialog_add_car, true)
                .positiveText(R.string.add_car_button_add)
                .negativeText(R.string.cancel)
                .autoDismiss(true)
                .cancelable(true)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {

                        String carNumber = mCarInput.getText().toString();
                        String carNick = mCarNick.getText().toString();

                        RegisteredCar car = new RegisteredCar();
                        car.setCarNumber(carNumber);
                        car.setCarNick(carNick);

                        mCarsAdapter.add(car);
                        mCarsAdapter.notifyDataSetChanged();

                        saveCars();
                    }
                })
                .build();

        final View positiveAction = dialog.getActionButton(DialogAction.POSITIVE);
        mCarNick = (EditText) dialog.getCustomView().findViewById(R.id.txtCarNick);
        mCarInput = (EditText) dialog.getCustomView().findViewById(R.id.txtCarNumber);
        mCarInput.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged (CharSequence s,int start, int count, int after){

            }

            @Override
            public void onTextChanged (CharSequence s,int start, int before, int count){
                positiveAction.setEnabled(s.toString().trim().length() > 0);
            }

            @Override
            public void afterTextChanged (Editable s){

            }
        });

        dialog.show();
        positiveAction.setEnabled(false); // disabled by default
    }

    private void saveCars() {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = sharedPrefs.edit();
        Set<String> carsSet = new HashSet<String>();
        for (RegisteredCar car : mCars) {

            String _s = car.getCarNumber() + "~" + car.getCarNick();
            carsSet.add(_s);

        }
        editor.putStringSet(Globals.CARS_PREF, carsSet);
        editor.apply();
    }

    public void onPhoneClick(View v) {
        MaterialDialog dialog = new MaterialDialog.Builder(this)
                .title(R.string.edit_phone_dialog_caption)
                .input(mUser.getPhone(), mUser.getPhone(), new MaterialDialog.InputCallback() {
                    @Override
                    public void onInput(MaterialDialog dialog, CharSequence input) {

                    }
                })
                .inputMaxLength(10)
                .inputType(InputType.TYPE_CLASS_PHONE)
                .positiveText(R.string.edit_car_button_save)
                .negativeText(R.string.cancel)
                .callback((new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        String phoneNumber = dialog.getInputEditText().getText().toString();
                        mUser.setPhone(phoneNumber);
                        mUser.save(SettingsActivity.this);

                        // Actually, this is a refresh
                        displayUser();
                    }

                }

                ))
                .build();

        dialog.show();
    }
}
