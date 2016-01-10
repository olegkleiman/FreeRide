package com.labs.okey.freeride.model;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.annotations.Expose;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Oleg Kleiman on 13-Apr-15.
 */
public class Ride implements Serializable, Parcelable {

    public Ride(){
        driverName = "";
        isPictureRequired = false;
        approved = 0;
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

    @Expose
    @com.google.gson.annotations.SerializedName("ispicturerequired_bydriver")
    private Boolean isPictureRequiredByDriver;
    public Boolean isPictureRequiredByDriver() {return isPictureRequiredByDriver; }
    public void setPictureRequiredByDriver(Boolean value) {
        isPictureRequiredByDriver = value;
    }

    @Expose
    @com.google.gson.annotations.SerializedName("gfencename")
    private String gfencename;
    public String getGFenceName() {
        return gfencename;
    }
    public void setGFenceName(String value){
        gfencename = value;
    }

    //
    // Implementation of Parcelable
    //

    private Ride(Parcel in) {

        Id = in.readString();

        SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        String _dt = in.readString();
        try {
            setCreated(formatter.parse(_dt));
        } catch(ParseException ex) {
        }

        setDriverId(in.readString());
        setDriverName( in.readString() );
        setCarNumber( in.readString() );
        setRideCode( in.readString() );
        setApproved(in.readInt());
        isPictureRequired  = in.readByte() != 0;
        setGFenceName( in.readString() );
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {

        parcel.writeString(Id);

        SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        String created = formatter.format(getCreated());
        parcel.writeString(created);

        parcel.writeString(getDriverId());
        parcel.writeString(getDriverName());
        parcel.writeString(getCarNumber());
        parcel.writeString(getRideCode());
        parcel.writeInt(getApproved());
        parcel.writeByte((byte) (isPictureRequired() ? 1: 0) );
        parcel.writeString(getGFenceName());
    }

    public static final Creator<Ride> CREATOR = new Creator<Ride>() {
        public Ride createFromParcel(Parcel in) {
            return new Ride(in);
        }

        public Ride[] newArray(int size) {
            return new Ride[size];
        }
    };
}
