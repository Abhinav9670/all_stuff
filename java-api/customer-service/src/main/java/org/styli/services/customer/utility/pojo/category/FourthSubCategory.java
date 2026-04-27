package org.styli.services.customer.utility.pojo.category;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

@Data
public class FourthSubCategory implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5501042135836284817L;

	private Integer id;

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

	private List<FourthSubCategory> subCategories;

}
