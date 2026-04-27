package org.styli.services.customer.pojo.otp;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.stream.Stream;

/**
 * Created on 08-Jul-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
public enum OtpValidationType {

    NORMAL, LOGIN, REGISTRATION;


    @JsonCreator
    public static OtpValidationType decode(final Object code) {
        if(code instanceof String) {
            String codeString = ((String) code).trim();
            return Stream.of(values())
                    .filter(targetEnum -> targetEnum.name().equals(codeString))
                    .findFirst()
                    .orElse(NORMAL);
        }
        return NORMAL;
    }
}
