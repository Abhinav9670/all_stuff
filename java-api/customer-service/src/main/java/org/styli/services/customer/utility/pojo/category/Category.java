package org.styli.services.customer.utility.pojo.category;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

@Data
public class Category implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5501942135836284817L;

	private Integer id;

	private String categoryKey;

	private Integer parentId;

	private String name;

	private Boolean isActive;

	private Integer position;

	private Integer level;

	private Integer productCount;

	private String imageUrl;

	private String magentoPathUrl;

	private String metaTitle;

	private String metaKeywords;

	private String metaDescription;

	private List<FirstSubCategory> subCategories;

}
