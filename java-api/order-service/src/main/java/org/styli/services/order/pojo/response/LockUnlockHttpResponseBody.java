package org.styli.services.order.pojo.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LockUnlockHttpResponseBody {
    public String ProfileId;
    public String CartId;
    public String Points;
    public String Message;
}
