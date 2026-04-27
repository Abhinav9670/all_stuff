package org.styli.services.order.pojo.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class LockUnlockHttpRequestBody {
    @JsonProperty("ProfileId")
    public String profileId;
    @JsonProperty("CartId")
    public String cartId;
    @JsonProperty("Points")
    public String points;
    @JsonProperty("Action")
    public String action;
}
