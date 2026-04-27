package org.styli.services.order.pojo.request.Order;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import lombok.Data;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class CreateOrderRequestV2 {

    @NotNull
    public String quoteId;

    @NotNull
    @Min(1)
    public Integer storeId;

    public String ipAddress;

    public Integer source;

    public String merchantReference;

    public String appVersion;
    
    public Integer customerId;
    
    public boolean isProxy;
    
    public String paymentId;
    
    private String customerIp;
    
    private String xSource;

    private String otpvalue;
    
    private String orderIncrementId;
    
    private boolean retryPaymentReplica;
    
    private boolean payfortAuthorized;

    private Integer isApplePay=0;
}
