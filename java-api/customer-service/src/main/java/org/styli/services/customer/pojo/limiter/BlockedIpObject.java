package org.styli.services.customer.pojo.limiter;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created on 12-Jul-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
@AllArgsConstructor(staticName = "of")
public class BlockedIpObject {
    @JsonIgnore
    private String token;
    private String blockType;
    private Long createdAt;


    public BlockedIpObject() {
        this.createdAt = 0L;
    }
}
