package org.styli.services.order.pojo.request;

import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
public class CitySearchAddressMapperRequestV2 {

	private String city_id;
	private Set<String> warehouse_ids;
}
