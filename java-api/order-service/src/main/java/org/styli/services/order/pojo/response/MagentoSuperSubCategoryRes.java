package org.styli.services.order.pojo.response;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

@Data
public class MagentoSuperSubCategoryRes implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5672511087494469556L;

	private Integer id;

	private Integer parent_id;

	private String name;

	private Boolean is_active;

	private Integer position;

	private Integer level;

	private Integer product_count;

	private Boolean include_in_menu;

	private String magento_path_url;

	private String meta_title;

	private String meta_keywords;

	private String meta_description;

	private String thumbnail;

	private List<MagentoSuperSubTypeCategoryRes> children_data;

}
