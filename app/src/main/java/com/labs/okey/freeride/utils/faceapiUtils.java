package com.labs.okey.freeride.utils;

import android.content.Context;
import android.os.AsyncTask;

import com.labs.okey.freeride.R;
import com.labs.okey.freeride.model.PassengerFace;
import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.contract.VerifyResult;
import com.microsoft.projectoxford.face.rest.RESTException;

/**
 * Created by Oleg Kleiman on 8/24/15.
 */
public class faceapiUtils {


    public static void Analyze(final Context ctx) {

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {

                if( Globals.passengerFaces.size() < 2 )
                    return null;

                // Get an instance of face service client to detect faces in image.
                FaceServiceClient faceServiceClient = new FaceServiceClient(ctx.getString(R.string.oxford_subscription_key));
                PassengerFace pf1 = Globals.passengerFaces.get(0);
                PassengerFace pf2 = Globals.passengerFaces.get(1);

                try {
                    VerifyResult verifyResult = faceServiceClient.verify(pf1.getFaceId(), pf2.getFaceId());
                } catch (RESTException e) {
                    e.printStackTrace();
                }

                return null;
            }
        }.execute();
    }
}
