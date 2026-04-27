package org.styli.services.customer.pojo.limiter;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;


/**
 * Created on 30-Jun-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
@AllArgsConstructor(staticName = "of")
public class TokenBucketObject {
    @JsonIgnore
    private String token;
    private Long updatedAt;
    private Integer count;

    public TokenBucketObject() {
        this.updatedAt = 0L;
        this.count = 0;
    }
}
