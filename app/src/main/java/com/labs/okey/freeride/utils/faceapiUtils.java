package com.labs.okey.freeride.utils;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.labs.okey.freeride.R;
import com.labs.okey.freeride.model.PassengerFace;
import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.contract.VerifyResult;
import com.microsoft.projectoxford.face.rest.RESTException;

/**
 * Created by Oleg Kleiman on 8/24/15.
 */
public class faceapiUtils {

    private static final String LOG_TAG = "FR.FaceAPI";


    public static void Analyze(final Context ctx) {

        new AsyncTask<Void, Void, Void>() {

            int mDepth;

            @Override
            protected void onPreExecute() {
                mDepth = Globals.passengerFaces.size();
            }

            @Override
            protected Void doInBackground(Void... params) {

                if( mDepth < 2 )
                    return null;

                FaceServiceClient faceServiceClient = new FaceServiceClient(ctx.getString(R.string.oxford_subscription_key));

                try {

                    for (int i = 0; i < mDepth; i++) {
                        for (int j = i; j < mDepth; j++) {

                            if (i == j)
                                continue;

                            PassengerFace _pf1 = Globals.passengerFaces.get(i);
                            PassengerFace _pf2 = Globals.passengerFaces.get(j);

                            float matValue = Globals.verificationMat.get(i, j);
                            if (matValue == 0.0f) {

                                VerifyResult verifyResult = faceServiceClient.verify(_pf1.getFaceId(),
                                                                                     _pf2.getFaceId());


                                if( verifyResult.isIdentical ) {
                                    Log.e(LOG_TAG, "The pictures are identical");
                                }

                                float confidence = (float)verifyResult.confidence;
                                Globals.verificationMat.set(i, j, confidence);
                                Globals.verificationMat.set(j, i, confidence);
                            }
                        }

                    }
                } catch(RESTException e) {
                    e.printStackTrace();
                }

                return null;
            }
        }.execute();
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
