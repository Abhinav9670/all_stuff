package org.styli.services.order.pojo.request;

import lombok.Data;
import lombok.NonNull;

@Data
public class LockAndUnlockShukranRequest {
    public Boolean isLock = false;
    @NonNull
    public String profileId;
    @NonNull
    public String points;
    @NonNull
    public String cartId;
}
