package org.styli.services.customer.pojo;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

@Data
public class ImageDetails implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 164504941562174856L;

	private String image;

	private String smallImage;

	private String thumbnail;

	private String image2;

	private String smallImage2;

	private String thumbnail2;

	private List<String> mediaGallery;

}
