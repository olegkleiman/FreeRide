package com.labs.okey.freeride.model;

import java.io.Serializable;
import java.util.Date;

/**
 * Created by eli max on 09/09/2015.
 */
public class PassByDate implements Serializable {

    @com.google.gson.annotations.SerializedName("id")
    public String Id;

    @com.google.gson.annotations.SerializedName("dateride")
    private Date dateride;
    public Date getDateRide() {return dateride;}
    public void setDateRide(Date value) { dateride = value; }

    @com.google.gson.annotations.SerializedName("asdriver")
    private boolean asdriver;
    public boolean getAsDriver() {return asdriver;}
    public void setAsDriver(boolean value) { asdriver = value; }
}
