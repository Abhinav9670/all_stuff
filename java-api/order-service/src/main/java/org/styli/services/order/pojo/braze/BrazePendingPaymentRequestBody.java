package org.styli.services.order.pojo.braze;

import java.util.List;

import lombok.Data;

@Data
public class BrazePendingPaymentRequestBody {

	private List<BrazePendingPaymentEvent> events;

}
