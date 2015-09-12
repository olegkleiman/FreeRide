package com.labs.okey.freeride.model;

/**
 * Created by Oleg on 11-Sep-15.
 */
public class AdvertisedRide {

    private String mUserId;
    private String mUserName;
    private String mRideCode;

    public AdvertisedRide(String userId,
                          String userName,
                          String rideCode){
        mUserId = userId;
        mUserName = userName;
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

    public String getUserName() { return mUserName; }
    public void setUseName(String value) { mUserName = value; }
}
