package org.styli.services.order.model.redis;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Created on 19-May-2022
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

    public OtpBucketObject() {
        // empty constructor
    }
}
