package org.styli.services.customer.pojo;

import java.util.List;

import org.styli.services.customer.pojo.registration.response.ErrorType;

import lombok.Data;

@Data
public class PlacesAutocompleteGoogleMapsResponse {

	private List<GoogleMapsPlaceDetails> placesList;
	private Boolean status;
    private String statusCode;
    private String statusMsg;
    private ErrorType error;
}
