package org.styli.services.order.pojo;

import lombok.Data;

@Data
public class AttributeValue {

	private String attributeType;
	
	private Integer plpVisiable;
	
	private Integer pdpVisiable;
	
	private Integer attributeFor;
	
	private Integer isUserDefind;
}
