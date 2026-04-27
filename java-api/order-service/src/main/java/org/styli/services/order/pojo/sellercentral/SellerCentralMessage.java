package org.styli.services.order.pojo.sellercentral;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SellerCentralMessage {

    private String type;
    private SellerCentralOrder payload;
}