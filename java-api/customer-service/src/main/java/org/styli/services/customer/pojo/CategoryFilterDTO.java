package org.styli.services.customer.pojo;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.styli.services.customer.pojo.registration.response.Product.KeyValuePair;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CategoryFilterDTO implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 164504941562174956L;

	private Integer totalCount;

	private Map<String, List<KeyValuePair>> filters;

	private List<Integer> totalProductIds;

}
