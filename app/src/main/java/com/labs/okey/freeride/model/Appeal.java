package com.labs.okey.freeride.model;

import java.io.Serializable;

/**
 * Created by eli max on 23/10/2015.
 */
public class Appeal implements Serializable {

    @com.google.gson.annotations.SerializedName("id")
    public String Id;

    @com.google.gson.annotations.SerializedName("rideid")
    private String rideId;
    public String getRideId() {
        return rideId;
    }
    public void setRideId(String value) { rideId = value; }

    @com.google.gson.annotations.SerializedName("emojiid")
    private String emojiId;
    public String getEmojiId() {
        return emojiId;
    }
    public void setEmojiId(String value) { emojiId = value; }

    @com.google.gson.annotations.SerializedName("pictureurl")
    private String pictureUrl;
    public String getPictureUrl() {
        return pictureUrl;
    }
    public void setPictureUrl(String value) { pictureUrl = value; }
}
