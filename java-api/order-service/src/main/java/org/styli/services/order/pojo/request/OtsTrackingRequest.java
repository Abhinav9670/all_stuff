package org.styli.services.order.pojo.request;

import java.util.List;

import lombok.Data;

@Data
public class OtsTrackingRequest {

	private Integer splitOrderId;

    private Integer parentOrderId;

	private String incrementId;

	private String sku;
}
