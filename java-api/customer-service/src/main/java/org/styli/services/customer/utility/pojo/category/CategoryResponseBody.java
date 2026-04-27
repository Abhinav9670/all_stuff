package org.styli.services.customer.utility.pojo.category;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

@Data
public class CategoryResponseBody implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5501592135836284817L;

	private List<Category> categories;

}
