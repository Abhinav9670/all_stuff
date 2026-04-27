package org.styli.services.customer.pojo.otp;

import org.styli.services.customer.pojo.GetLocationGoogleMaps;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Created on 06-Jul-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
@AllArgsConstructor(staticName = "bf")
public class CityBucketObject {


    GetLocationGoogleMaps response;
    @JsonIgnore
    String areaName;
    Long originAt = 0L;
    Long createdAt = 0L;
    Long expiresAt = 0L;
    Integer createCount = 0;


    public CityBucketObject() {
    	// empty constructor
    }
}