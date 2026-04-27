package org.styli.services.order.pojo.eas;

import lombok.Data;

@Data
public class EarnResponse {
	 private Integer code;
	 private EarnMessage message;
	 private Integer coins;
}
