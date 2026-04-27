package org.styli.services.order.pojo.response;

import lombok.Data;

@Data
public class LockAndUnlockShukranResponse {
    public String statusCode;
    public Boolean status;
    public String statusMsg;
}
