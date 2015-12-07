package com.labs.okey.freeride.model;

import com.google.gson.annotations.Expose;

import java.util.Date;

/**
 * Created by Oleg Kleiman on 14-Apr-15.
 */
public class Join {

    @com.google.gson.annotations.SerializedName("id")
    public String Id;

    @Expose
    @com.google.gson.annotations.SerializedName("lat")
    private float lat;
    public float getLat() { return lat; }
    public void setLat(float value) { lat = value; }

    @Expose
    @com.google.gson.annotations.SerializedName("lon")
    private float lon;
    public float getLon() { return lon; }
    public void setLon(float value) { lon = value; }

    @com.google.gson.annotations.SerializedName("deviceid")
    private String deviceId;
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String value){
        deviceId = value;
    }

    @com.google.gson.annotations.SerializedName("when_joined")
    private Date whenJoined;
    public Date getWhenJoined() {
        return whenJoined;
    }
    public void setWhenJoined(Date value) {
        whenJoined = value;
    }

    @com.google.gson.annotations.SerializedName("ridecode")
    private String rideCode;
    public String getRideCode() {
        return rideCode;
    }
    public void setRideCode(String value) {
        rideCode = value;
    }


    @com.google.gson.annotations.SerializedName("picture_url")
    private String picture_url;
    public String getPictureURL() { return picture_url; }
    public void setPictureURL(String value) { picture_url = value; }

    @com.google.gson.annotations.SerializedName("faceid")
    private String faceId;
    public String getFaceId() { return faceId; }
    public void setFaceId(String value) { faceId = value; }

    @Expose
    @com.google.gson.annotations.SerializedName("gfencename")
    private String gfencename;
    public String getGFenceName() {
        return gfencename;
    }
    public void setGFenceName(String value){
        gfencename = value;
    }
}

