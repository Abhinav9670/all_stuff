package org.styli.services.order.pojo.tabby;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tabby Orde Items
 * 
 * @author Chandan Behera
 *
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TabbyItems implements Serializable {

	private static final long serialVersionUID = 1L;

	private String title;

	private String description;

	private Integer quantity;

	@JsonProperty("unit_price")
	private String unitPrice;


}
