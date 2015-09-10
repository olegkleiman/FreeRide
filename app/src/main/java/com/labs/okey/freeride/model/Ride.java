package com.labs.okey.freeride.model;

import com.google.gson.annotations.Expose;
import com.labs.okey.freeride.utils.Globals;

import org.opencv.ml.Boost;

import java.io.Serializable;
import java.util.Date;

/**
 * Created by Oleg Kleiman on 13-Apr-15.
 */
public class Ride implements Serializable  {

    public Ride(){
        driverName = "";
        isPictureRequired = false;
    }

    @Expose
    @com.google.gson.annotations.SerializedName("id")
    public String Id;

    @Expose
    @com.google.gson.annotations.SerializedName("ridecode")
    private String rideCode;
    public String getRideCode() {
        return rideCode;
    }
    public void setRideCode(String value) { rideCode = value; }

    @Expose
    @com.google.gson.annotations.SerializedName("created")
    private Date created;
    public Date getCreated() { return created; }
    public void setCreated(Date value) { created = value; }

    @Expose
    @com.google.gson.annotations.SerializedName("driverid")
    private String driverid;
    public String getDriverId() { return driverid; }
    public void setDriverId(String value) { driverid = value;}

    @com.google.gson.annotations.SerializedName("drivername")
    private String driverName;
    public String getDriverName() { return driverName; }
    public void setDriverName(String value) { driverName = value; }

    @Expose
    @com.google.gson.annotations.SerializedName("carnumber")
    private String carNumber;
    public String getCarNumber() { return carNumber; }
    public void setCarNumber(String value) { carNumber = value; }

    @Expose
    @com.google.gson.annotations.SerializedName("approved")
    private Integer approved;
    public Integer getApproved() { return approved; }
    public void setApproved(Integer value) { approved = value; }

    @Expose
    @com.google.gson.annotations.SerializedName("picture_url")
    private String picture_url;
    public String getPictureURL() { return picture_url; }
    public void setPictureURL(String value) { picture_url = value; }

    @Expose
    @com.google.gson.annotations.SerializedName("ispicturerequired")
    private Boolean isPictureRequired;
    public Boolean isPictureRequired() {return isPictureRequired; }
}
