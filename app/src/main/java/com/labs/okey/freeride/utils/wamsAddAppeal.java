package com.labs.okey.freeride.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.os.AsyncTask;
import android.util.Log;
import android.view.Display;

import com.afollestad.materialdialogs.MaterialDialog;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.labs.okey.freeride.R;
import com.labs.okey.freeride.model.Appeal;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.table.MobileServiceTable;

import net.steamcrafted.loadtoast.LoadToast;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;


/**
 * Created by eli max on 23/10/2015.
 */

public class wamsAddAppeal extends AsyncTask<File, Void, Void> {

    private static final String LOG_TAG = "FR.wamsAppeal";

    URI                                 publishedUri;
    Exception                           error;
    String                              mRideID;
    String                              mDriverID;
    String                              mDriverName;
    int                                 mEmojiID;
    Context                             mContext;
    String                              mContainerName;
    IUploader                           mUploader;
    Appeal                              mCurrentAppeal;

    private MobileServiceClient         wamsClient;
    public MobileServiceClient getMobileServiceClient() { return wamsClient; }

    LoadToast                           lt;

    public static final String storageConnectionString =
            "DefaultEndpointsProtocol=http;" +
            "DefaultEndpointsProtocol=http;" +
                    "AccountName=fastride;" +
                    "AccountKey=tuyeJ4EmEuaoeGsvptgyXD0Evvsu1cTiYPAF2cwaDzcGkONdAOZ/3VEY1RHAmGXmXwwkrPN1yQmRVdchXQVgIQ==";

    public wamsAddAppeal(Context ctx, String driverName,
                         String containerName,
                         String rideID,
                         String driverID,
                         int emojiID){

        mContainerName = containerName;
        mRideID = rideID;
        mDriverID = driverID;
        mDriverName = driverName;
        mEmojiID = emojiID;

        mContext = ctx;
        if( ctx instanceof IUploader )
            mUploader = (IUploader)ctx;
    }

    @Override
    protected void onPreExecute() {
        lt = new LoadToast(mContext);
        lt.setText(mContext.getString(R.string.processing));

        Display display = ((Activity)mContext).getWindow().getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        lt.setTranslationY(size.y / 2);
        lt.show();
    }


    @Override
    protected void onPostExecute(Void result) {

        if( error == null ) {

            lt.success();

            CustomEvent requestEvent = new CustomEvent(mContext.getString(R.string.appeal_answer_name));
            requestEvent.putCustomAttribute("User", mDriverName);

            Answers.getInstance().logCustom(requestEvent);

            new MaterialDialog.Builder(mContext)
                    .title(mContext.getString(R.string.appeal_send_title))
                    .content(mContext.getString(R.string.appeal_send_success))
                    .iconRes(R.drawable.ic_info)
                    .positiveText(R.string.ok)
                    .callback(new MaterialDialog.ButtonCallback() {
                                  @Override
                                  public void onPositive(MaterialDialog dialog) {
                                      mUploader.finished(Globals.APPEAL_UPLOAD_TASK_TAG, true);
                                  }
                              }
                    )
                    .show();
        }
        else {
            lt.error();

            new MaterialDialog.Builder(mContext)
                    .title(mContext.getString(R.string.appeal_send_title))
                    .content(error.getMessage())
                    .iconRes(R.drawable.ic_exclamation)
                    .show();
        }

    }

    @Override
    protected Void doInBackground(File... params) {

        File photoFile = params[0];

        try {
            CloudStorageAccount storageAccount = CloudStorageAccount.parse(storageConnectionString);
            CloudBlobClient blobClient = storageAccount.createCloudBlobClient();
            CloudBlobContainer container = blobClient.getContainerReference(mContainerName );

            String fileName = photoFile.getName();

            CloudBlockBlob blob = container.getBlockBlobReference(fileName);
            blob.upload(new FileInputStream(photoFile), photoFile.length());

            publishedUri = blob.getQualifiedUri();

            Appeal appeal = new Appeal();
            appeal.setRideId(mRideID);
            appeal.setPictureUrl(publishedUri.toString());
            appeal.setEmojiId(Integer.toString(mEmojiID));
            appeal.setDriverId(mDriverID);

            wamsClient = wamsUtils.init(mContext);

            MobileServiceTable<Appeal>  wamsAppealTable = getMobileServiceClient().getTable("appeal", Appeal.class);
            mCurrentAppeal = wamsAppealTable.insert(appeal).get();

        } catch (Exception e) {
            error = e;
            Log.e(LOG_TAG, e.getMessage());
        }

        return null;
    }

}
