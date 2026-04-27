package org.styli.services.order.pojo;

import lombok.Data;

/**
 * Created on 19-May-2022
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@Data
public class PaymentRestriction {
    Boolean codEnabled;
    Integer otpFlag;
    Boolean otpValidation;
    String otpValidationType;
}
