package org.styli.services.order.pojo.request.Order;

import java.util.List;
import javax.validation.constraints.NotNull;

public class DummyReturnShipmentRequest {
    
    @NotNull
    private List<String> returnIncrementIds;

    public List<String> getReturnIncrementIds() {
        return returnIncrementIds;
    }
    
    public void setReturnIncrementIds(List<String> returnIncrementIds) {
        this.returnIncrementIds = returnIncrementIds;
    }
}
