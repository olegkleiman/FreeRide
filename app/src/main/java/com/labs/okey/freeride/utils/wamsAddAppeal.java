package com.labs.okey.freeride.utils;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.util.Log;

import com.afollestad.materialdialogs.MaterialDialog;
import com.labs.okey.freeride.R;
import com.labs.okey.freeride.model.Appeal;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.table.MobileServiceTable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;


/**
 * Created by eli max on 23/10/2015.
 */

public class wamsAddAppeal extends AsyncTask<File, Void, Void> {

    private static final String LOG_TAG = "FR.wamsAppeal";

    URI publishedUri;
    Exception error;
    String mRideID;
    Context mContext;
    String  mContainerName;

    Appeal mCurrentAppeal;
    private MobileServiceTable<Appeal> AppealTable;

    private MobileServiceClient wamsClient;
    public MobileServiceClient getMobileServiceClient() { return wamsClient; }


    ProgressDialog mProgressDialog;

    public static final String storageConnectionString =
            "DefaultEndpointsProtocol=http;" +
            "DefaultEndpointsProtocol=http;" +
                    "AccountName=fastride;" +
                    "AccountKey=tuyeJ4EmEuaoeGsvptgyXD0Evvsu1cTiYPAF2cwaDzcGkONdAOZ/3VEY1RHAmGXmXwwkrPN1yQmRVdchXQVgIQ==";

    public wamsAddAppeal(Context ctx, String containerName, String rideID){

        mContainerName = containerName;
        mRideID = rideID;

        mContext = ctx;
//        if( ctx instanceof IPictureURLUpdater )
//            mUrlUpdater = (IPictureURLUpdater)ctx;
    }

    @Override
    protected void onPreExecute() {
//        mProgressDialog = ProgressDialog.show(mContext,
//                mContext.getString(R.string.detection_store),
//                mContext.getString(R.string.detection_wait));
    }


    @Override
    protected void onPostExecute(Void result) {

        new MaterialDialog.Builder(mContext)
                .title(mContext.getString(R.string.appeal_send_title))
                .content(mContext.getString(R.string.appeal_send_success))
                .iconRes(R.drawable.ic_info)
                .positiveText(R.string.ok)
                .show();
//        mProgressDialog.dismiss();

//        if( mUrlUpdater != null )
//            mUrlUpdater.update(publishedUri.toString());
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
            appeal.setEmojiId(Integer.toString(Globals.EMOJI_INDICATOR));

            wamsClient = wamsUtils.init(mContext);
            startAutoUpdate();

            AppealTable = getMobileServiceClient().getTable("appeal", Appeal.class);
            mCurrentAppeal = AppealTable.insert(appeal).get();




        } catch (URISyntaxException | InvalidKeyException
                | IOException | StorageException e) {
            error = e;
            Log.e(LOG_TAG, e.getMessage());
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return null;
    }

    private WAMSVersionTable wamsVersionTable;

    private void startAutoUpdate() {
        try {

            WAMSVersionTable.IVersionMismatchListener listener = null;
            if (this instanceof WAMSVersionTable.IVersionMismatchListener) {
                listener = (WAMSVersionTable.IVersionMismatchListener) this;
            }
            wamsVersionTable = new WAMSVersionTable(mContext, listener);
            PackageInfo info = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
            String packageVersionName = info.versionName;
            if (!packageVersionName.isEmpty()) {

                StringTokenizer tokens = new StringTokenizer(packageVersionName, ".");
                if (tokens.countTokens() > 0) {
                    int majorPackageVersion = Integer.parseInt(tokens.nextToken());
                    int minorPackageVersion = Integer.parseInt(tokens.nextToken());
                    wamsVersionTable.compare(majorPackageVersion, minorPackageVersion);
                }
            }

        } catch (PackageManager.NameNotFoundException ex) {

            Log.e(LOG_TAG, ex.getMessage());
        }
    }
}
