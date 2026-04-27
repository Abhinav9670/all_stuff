package org.styli.services.order.pojo.config;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

@Data
public class FeatureFlag implements Serializable {
	
	private static final long serialVersionUID = 3422937313904494229L;
	
	private List<Integer> zatca;

}
