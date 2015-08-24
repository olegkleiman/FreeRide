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
}
