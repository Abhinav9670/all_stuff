package org.styli.services.customer.pojo.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Created on 13-Oct-2022
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
@AllArgsConstructor(staticName = "of")
public class WhatsappSignupBucketObject {

    String mobileNo;
    String firstName;
    String lastName;
    String code;
    Long originAt = 0L;
    Long updatedAt = 0L;
    Long expiresAt = 0L;
    Integer requestCount = 0;

    public WhatsappSignupBucketObject() {
        // empty constructor
    }
}
