package com.labs.okey.freeride.jobs;

import android.annotation.TargetApi;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import com.labs.okey.freeride.model.GeoFence;
import com.labs.okey.freeride.utils.Globals;
import com.labs.okey.freeride.utils.wamsUtils;
import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.table.MobileServiceTable;
import com.microsoft.windowsazure.mobileservices.table.query.Query;
import com.microsoft.windowsazure.mobileservices.table.sync.MobileServiceSyncTable;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;

/**
 * Created by Oleg Kleiman on 22-Jun-15.
 *  Very clean introduction to Android's Job Scheduler by Chris Pierick
 *  http://toastdroid.com/2015/02/21/how-to-use-androids-job-scheduler/
 */

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class GeofencesDownloadService extends JobService {

    private static final String LOG_TAG = "FR.gfJob";
    private UpdateGeofencesAsyncTask updateTask = new UpdateGeofencesAsyncTask();

    @Override
    public boolean onStartJob(JobParameters jobParameters) {

        updateTask.execute(jobParameters);

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        // Note: return true to reschedule this job.


        return updateTask.stopJob(jobParameters);
    }

    private class UpdateGeofencesAsyncTask extends AsyncTask<JobParameters, Void, JobParameters[]> {

        private MobileServiceClient wamsClient;
        private MobileServiceTable<GeoFence> gFencesTbl;
        MobileServiceSyncTable<GeoFence> gFencesSyncTable;

        @Override
        protected void onPreExecute() {

            try{
                wamsClient = new MobileServiceClient(
                        Globals.WAMS_URL,
                        Globals.WAMS_API_KEY,
                        getApplicationContext());

                gFencesSyncTable = wamsClient.getSyncTable("geofences", GeoFence.class);
                gFencesTbl = wamsClient.getTable(GeoFence.class);
                gFencesSyncTable = wamsClient.getSyncTable("geofences", GeoFence.class);

                wamsUtils.sync(wamsClient, "geofences");

            } catch(MalformedURLException ex ) {
                Log.e(LOG_TAG, ex.getMessage() + " Cause: " + ex.getCause());
            }
        }

        @Override
        protected JobParameters[] doInBackground(JobParameters... params) {

            try {
                if (wamsClient != null) {
                    Query pullQuery = gFencesTbl.where();
                    gFencesSyncTable.purge(pullQuery);
                    gFencesSyncTable.pull(pullQuery).get();
                }
            } catch(InterruptedException | ExecutionException ex ) {
                Log.e(LOG_TAG, ex.getMessage() + " Cause: " + ex.getCause());
            }

            // Do updating and stopping logical here.
            return params;
        }

        public boolean stopJob(JobParameters params) {
            // Logic for stopping a job. return true if job should be rescheduled.
            return true;
        }

    }
}
