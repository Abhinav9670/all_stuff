package org.styli.services.customer.pojo.epsilon;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor(staticName = "of")
public class EpsilonBucketObject {

    @JsonIgnore
    String mobileNo;
    Long createdAt = 0L;

    public EpsilonBucketObject() {
    }
}