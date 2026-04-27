package org.styli.services.customer.pojo.response;

import lombok.Data;

import java.util.List;

@Data
public class InfluencerResponse {

    private String code;
    private String message;
    private String errorCode;
    private List<Influencer> body;

}


