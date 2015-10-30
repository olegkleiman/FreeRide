package com.labs.okey.freeride.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.os.AsyncTask;
import android.util.Log;
import android.view.Display;

import com.crashlytics.android.Crashlytics;
import com.labs.okey.freeride.R;
import com.labs.okey.freeride.model.PassengerFace;
import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.contract.VerifyResult;
import com.microsoft.projectoxford.face.rest.RESTException;

import net.steamcrafted.loadtoast.LoadToast;

/**
 * Created by Oleg Kleiman on 8/24/15.
 */
public class faceapiUtils extends AsyncTask<Void, Void, Void> {

    private static final String LOG_TAG = "FR.FaceAPI";

    Context             mContext;
    IPicturesVerifier   mUrlUpdater;
    int                 mDepth;
    LoadToast           lt;
    Boolean             mComparisonResult = true;

    public faceapiUtils(Context ctx) {

        mContext = ctx;

        if(ctx instanceof IPicturesVerifier)
            mUrlUpdater=(IPicturesVerifier)ctx;
    }

    @Override
    protected void onPreExecute() {

        lt = new LoadToast(mContext);
        lt.setText(mContext.getString(R.string.processing));
        if( mContext instanceof Activity) {
            Display display = ((Activity)mContext).getWindow().getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            lt.setTranslationY(size.y / 2);
        }
        lt.show();

        mDepth = Globals.get_PassengerFaces().size();
    }

    @Override
    protected void onPostExecute(Void result){
        lt.success();

        if( mUrlUpdater != null )
            mUrlUpdater.finished(mComparisonResult);
    }

    @Override
    protected Void doInBackground(Void... params) {

        if( mDepth < 2 )
            return null;

        FaceServiceClient faceServiceClient = new FaceServiceClient(mContext.getString(R.string.oxford_subscription_key));

        try {

            for (int i = 0; i < mDepth; i++) {
                for (int j = i; j < mDepth; j++) {

                    if (i == j)
                        continue;

                    PassengerFace _pf1 = Globals.get_PassengerFace(i);
                    PassengerFace _pf2 = Globals.get_PassengerFace(j);

                    float matValue = Globals.verificationMat.get(i, j);
                    if (matValue == 0.0f) {

                        VerifyResult verifyResult = faceServiceClient.verify(_pf1.getFaceId(),
                                                                             _pf2.getFaceId());


                        if( verifyResult.isIdentical ) {
                            mComparisonResult = false;

                            String msg = String.format("The faces %s and %s are identical",
                                                       _pf1.getFaceId(),
                                                       _pf2.getFaceId() );
                            Log.i(LOG_TAG, msg);
                        }

                        float confidence = (float)verifyResult.confidence;
                        Globals.verificationMat.set(i, j, confidence);
                        Globals.verificationMat.set(j, i, confidence);
                    }
                }

            }
        } catch(RESTException e) {
            if(Crashlytics.getInstance() != null )
                Crashlytics.logException(e);

            Log.e(LOG_TAG, e.getMessage());
        }

        return null;
    }


    public static void dumpVerificationMatrix(int depth) {
        for(int i = 0; i < depth; i++) {

            StringBuilder sb = new StringBuilder();

            for(int j = 0; j < depth; j++) {
                float val = Globals.verificationMat.get(i, j);
                sb.append(val);
                sb.append(" ");
            }

            Log.i(LOG_TAG, sb.toString());
        }
    }
}
