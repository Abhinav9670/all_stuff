package org.styli.services.order.pojo.request.GetShipmentV3;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class GetShipmentV3Request {

	@JsonProperty("pickup_info")
    private PickupInfo pickupInfo;
	
	@JsonProperty("drop_info")
    private DropInfo dropInfo;
	
	@JsonProperty("return_info")
    private ReturnInfo returnInfo;
    
	@JsonProperty("tax_info")
    private Object taxInfo;
	
    private Additional additional;
    
    @JsonProperty("shipment_details")
    private ShipmentDetails shipmentDetails;
}
