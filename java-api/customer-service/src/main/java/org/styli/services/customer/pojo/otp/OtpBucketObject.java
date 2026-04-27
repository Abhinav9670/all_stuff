package org.styli.services.customer.pojo.otp;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created on 06-Jul-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
@AllArgsConstructor(staticName = "of")
public class OtpBucketObject {

    @JsonIgnore
    String mobileNo;
    String otp;
    Long originAt = 0L;
    Long createdAt = 0L;
    Long expiresAt = 0L;
    Integer createCount = 0;
    @JsonIgnore
    String customerId;
    private String email;
    Integer incorrectAttempts = 0;

    public OtpBucketObject() {
    }
    public Integer getIncorrectAttempts() {
        return incorrectAttempts;
    }

    public void setIncorrectAttempts(Integer incorrectAttempts) {
        this.incorrectAttempts = incorrectAttempts;
    }
}