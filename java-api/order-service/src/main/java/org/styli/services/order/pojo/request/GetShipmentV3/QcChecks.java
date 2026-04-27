package org.styli.services.order.pojo.request.GetShipmentV3;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QcChecks implements Serializable{

	private static final long serialVersionUID = 1L;
	
	@JsonProperty("qc_check_code")
	private String qcCheckCode;
	
	private String value;
	
	private String desc;

}
