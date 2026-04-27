package org.styli.services.customer.pojo.registration.response.Product;

import java.io.Serializable;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Categories implements Serializable {

	private static final long serialVersionUID = 1059027536621108431L;
	@JsonProperty("level0")
	private List<String> level0;

	@JsonProperty("level1")
	private List<String> level1;

	@JsonProperty("level2")
	private List<String> level2;

	@JsonProperty("level3")
	private List<String> level3;

}