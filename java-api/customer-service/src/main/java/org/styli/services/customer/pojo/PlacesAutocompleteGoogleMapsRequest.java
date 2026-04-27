package org.styli.services.customer.pojo;

import lombok.Data;

@Data
public class PlacesAutocompleteGoogleMapsRequest {

	private String placeText;
	private Integer storeId;
}
