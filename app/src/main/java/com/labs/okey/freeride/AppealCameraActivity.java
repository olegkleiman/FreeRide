package com.labs.okey.freeride;

import android.app.DialogFragment;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class AppealCameraActivity extends AppCompatActivity
                implements AppealDialog.NoticeDialogListener{

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    //String mCurrentPhotoPath;
    Uri uriPhoto;
    int emojiIndicator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_appeal_camera);

        //TODO update from server
        //emojiIndicator = Globals.EMOJI_INDICATOR;

        showAppealDialog();

    }

    public void showAppealDialog() {
        // Create an instance of the dialog fragment and show it
        DialogFragment dialog = new AppealDialog();

        ImageView emoji = (ImageView) dialog.getDialog().findViewById(R.id.appeal_emoji);
        String uri = "@drawable/emoji_" + Integer.toString(emojiIndicator);
        int imageResource = getResources().getIdentifier(uri, null, null);
        emoji.setImageResource(imageResource);
        dialog.show(getFragmentManager(), "AppealDialog");
    }



    public void camera(){

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {

            File photoFile = null;

            try {
                photoFile = createImageFile();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (photoFile != null) {

                intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));

                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {

            ImageView imageViewAppeal = (ImageView) findViewById(R.id.imageViewAppeal);
            imageViewAppeal.setImageURI(uriPhoto);

//            Bundle extras = data.getExtras();
//            Bitmap imageBitmap = (Bitmap) extras.get("data");
//            imageViewAppeal.setImageBitmap(imageBitmap);
        }
    }

    private File createImageFile() throws IOException {

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String photoFileName = "AppealJPEG_" + timeStamp + "_";


        File storageDir = getExternalFilesDir(null);

        File photoFile = File.createTempFile(
                photoFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        //mCurrentPhotoPath = "file:" + photoFile.getAbsolutePath();
        uriPhoto = Uri.fromFile(photoFile);
        return photoFile;
    }




    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_appeal_camera, menu);
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

    //todo
    public void onClickGoTutorial(View view) {


    }

    @Override
    public void onDialogPositiveClick(DialogFragment dialog) {
        camera();
    }

    @Override
    public void onDialogNegativeClick(DialogFragment dialog) {

    }
}
