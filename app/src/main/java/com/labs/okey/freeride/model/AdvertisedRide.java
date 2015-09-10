package com.labs.okey.freeride.model;

/**
 * Created by Oleg on 11-Sep-15.
 */
public class AdvertisedRide {

    private String mUserId;
    private String mRideCode;

    public AdvertisedRide(String userId,
                          String rideCode){
        mUserId = userId;
        mRideCode = rideCode;
    }

    public String getUserId() {
        return mUserId;
    }
    public void setUserId(String value) {
        mUserId = value;
    }

    public String getRideCode() {
        return mRideCode;
    }
    public void setRideCode(String value){
        mRideCode = value;
    }
}
