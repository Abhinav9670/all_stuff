package org.styli.services.order.helper;

import java.util.Iterator;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.styli.services.order.model.sales.SalesOrderAddress;
import org.styli.services.order.pojo.AddressDetails;
import org.styli.services.order.utility.Constants;

@Component
public class NavikHelper {

	private static final String NAME_EN = "name_en";

	private static final Log LOGGER = LogFactory.getLog(NavikHelper.class);

	private static final String NAME_AR = "name_ar";

	public AddressDetails getArabicAddressDetails(SalesOrderAddress address) throws JSONException {

		AddressDetails addressDetails = new AddressDetails();
		String mapper = Constants.getAddressMapper(address.getCountryId().toLowerCase());

		LOGGER.info("mapper string before area check:" + mapper);
		boolean areaCorrect = false;
		if (mapper != null && StringUtils.isNotEmpty(mapper)) {

			JSONObject jsonObj = new JSONObject(mapper);
			JSONObject province = jsonObj.getJSONObject("provinces");
			Iterator<?> provinceIterator = province.keys();
			while (provinceIterator.hasNext()) {
				Object provinceKey = provinceIterator.next();
				JSONObject provinceValue = province.getJSONObject(provinceKey.toString());
				if (provinceKey.toString().toLowerCase().trim().equals(address.getCountryId().toLowerCase())) {
					JSONObject provinceNext = provinceValue;
					Iterator<?> provinceNextIterator = provinceNext.keys();

					areaCorrect = setProvince(address, areaCorrect, provinceNext, provinceNextIterator, addressDetails);

				}

			}
		}

		return addressDetails;
	}

	private boolean setProvince(SalesOrderAddress customerAddRequest, boolean areaCorrect, JSONObject provinceNext,
			Iterator<?> provinceNextIterator, AddressDetails addressDetails) throws JSONException {
		while (provinceNextIterator.hasNext()) {
			Object provinceNextKey = provinceNextIterator.next();
			JSONObject provinceNextValue = provinceNext.getJSONObject(provinceNextKey.toString());
			if (null != customerAddRequest.getRegion()
					&& provinceNextValue.get("name").toString().trim().equals(customerAddRequest.getRegion().trim())
					|| provinceNextValue.get(NAME_AR).toString().trim().equals(customerAddRequest.getRegion().trim())) {
				addressDetails.setProvienceName(provinceNextValue.get("name").toString().trim());
				JSONObject city = provinceNextValue.getJSONObject("cities");
				Iterator<?> iterator = city.keys();
				while (iterator.hasNext()) {
					Object cityKey = iterator.next();
					JSONObject cityValue = city.getJSONObject(cityKey.toString());
					areaCorrect = setCityCorrect(customerAddRequest, areaCorrect, cityValue, addressDetails);
				}
			}
		}
		return areaCorrect;
	}

	private boolean setCityCorrect(SalesOrderAddress customerAddRequest, boolean areaCorrect, JSONObject cityValue,
			AddressDetails addressDetails) throws JSONException {
		if (null != customerAddRequest.getCity()
				&& cityValue.get(NAME_EN).toString().trim().equals(customerAddRequest.getCity().trim())
				|| cityValue.get(NAME_AR).toString().trim().equals(customerAddRequest.getCity().trim())) {
			addressDetails.setCityName(cityValue.get(NAME_EN).toString().trim());
			areaCorrect = setAreaForCustomerAddress(customerAddRequest, areaCorrect, cityValue, addressDetails);
		}
		return areaCorrect;
	}

	private boolean setAreaForCustomerAddress(SalesOrderAddress customerAddRequest, boolean areaCorrect,
			JSONObject cityValue, AddressDetails addressDetails) throws JSONException {
		JSONObject area = cityValue.getJSONObject("area");
		Iterator<?> areaIterator = area.keys();
		while (areaIterator.hasNext()) {
			Object areaKey = areaIterator.next();
			JSONObject areaValue = area.getJSONObject(areaKey.toString());
			if (null != customerAddRequest.getArea()
					&& areaValue.get(NAME_EN).toString().trim().equals(customerAddRequest.getArea().trim())
					|| areaValue.get(NAME_AR).toString().trim().equals(customerAddRequest.getArea().trim())) {
				addressDetails.setAreaName(areaValue.get(NAME_EN).toString().trim());
				areaCorrect = true;
				break;
			}
		}
		return areaCorrect;
	}
}
