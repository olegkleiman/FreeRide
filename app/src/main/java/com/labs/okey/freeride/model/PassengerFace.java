package com.labs.okey.freeride.model;

import java.util.UUID;

/**
 * Created by Oleg Kleiman on 8/24/15.
 */
public class PassengerFace {

    private String faceId;
    public void setFaceId(String value) {
        faceId = value;
    }
    public UUID getFaceId() {
       return  UUID.fromString(faceId);
    }

    String pictureUrl;
    public void setPictureUrl(String value) { pictureUrl = value; }
    public String getPictureUrl() { return pictureUrl; }

    public PassengerFace(String _faceId){
        this.faceId = _faceId;
    }
}
