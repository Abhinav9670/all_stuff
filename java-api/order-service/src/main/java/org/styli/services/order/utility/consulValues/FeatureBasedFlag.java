package org.styli.services.order.utility.consulValues;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FeatureBasedFlag {

    private boolean cohortBasedCoupon;

}
