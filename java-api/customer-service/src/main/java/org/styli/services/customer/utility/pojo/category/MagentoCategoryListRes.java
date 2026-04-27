package org.styli.services.customer.utility.pojo.category;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

@Data
public class MagentoCategoryListRes implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5501542035836284817L;

	private Integer id;

	private Integer parent_id;

	private String name;

	private Boolean is_active;

	private Integer position;

	private Integer level;

	private Integer product_count;

	private List<MagentoSubCategoryRes> children_data;

}
