package org.styli.services.order.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.service.ConfigService;
import org.styli.services.order.utility.Constants;


/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Component
public class ConfigServiceImpl implements ConfigService {

	private static final Log LOGGER = LogFactory.getLog(ConfigServiceImpl.class);

	@Autowired
	StaticComponents staticComponents;

	@Value("${auth.external.header.bearer.token}")
	private String externalAuthBearerToken;

	@Value("${auth.internal.header.bearer.token}")
	private String internalAuthBearerToken;

	@Override
	public List<Integer> getWebsiteStoresByStoreId(Integer storeId) {
		List<Stores> stores = Constants.getStoresList();
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(storeId)).findAny()
				.orElse(null);

		if (store == null || store.getStoreCode() == null || store.getStoreCurrency() == null) {
			return new ArrayList<>();
		} else {
			return stores.stream().filter(e -> e.getWebsiteId() == store.getWebsiteId())
					.map(e -> Integer.valueOf(e.getStoreId())).collect(Collectors.toList());
		}
	}

	@Override
	public Double getWebsiteRefundByStoreId(Integer storeId) {

		List<Stores> stores = Constants.getStoresList();
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(storeId)).findAny()
				.orElse(null);
		double refundDeduction=0.0;

		if(store != null && store.isSecondRefund()){
			refundDeduction=store.getRefundDeduction();
		};
		return refundDeduction;
	}

	@Override
	public boolean checkAuthorization(String intenalAuthorizationToken, String externalAuthorizationToken) {
		boolean statusFlag = false;
		if (StringUtils.isNotEmpty(intenalAuthorizationToken) && StringUtils.isNotBlank(internalAuthBearerToken)) {
			String intenalToken = internalAuthBearerToken;
			if (intenalToken.contains(",")) {
				List<String> authTokenList = Arrays.asList(intenalToken.split(","));
				if (CollectionUtils.isNotEmpty(authTokenList) && authTokenList.contains(intenalAuthorizationToken)) {
					statusFlag = true;
					return statusFlag;
				}
			}
		} else if (StringUtils.isNotEmpty(externalAuthorizationToken)
				&& StringUtils.isNotBlank(externalAuthBearerToken)) {
			String externalToken = externalAuthBearerToken;
			if (externalToken.contains(",")) {
				List<String> authTokenList = Arrays.asList(externalToken.split(","));
				if (CollectionUtils.isNotEmpty(authTokenList) && authTokenList.contains(externalAuthorizationToken)) {
					statusFlag = true;
					return statusFlag;
				}
			}
		}
		return statusFlag;
	}

	@Override
	public boolean checkAuthorizationInternal(String authorizationToken) {
		boolean statusFlag = false;
		if (StringUtils.isNotEmpty(authorizationToken) && StringUtils.isNotBlank(internalAuthBearerToken)) {
			LOGGER.info("internalAuthBearerToken: " + internalAuthBearerToken);
			String intenalToken = internalAuthBearerToken;
			if (intenalToken.contains(",")) {
				List<String> authTokenList = Arrays.asList(intenalToken.split(","));
				if (CollectionUtils.isNotEmpty(authTokenList) && authTokenList.contains(authorizationToken)) {
					statusFlag = true;
					return statusFlag;
				}
			}
		}
		return statusFlag;
	}

	@Override
	public boolean checkAuthorizationExternal(String authorizationToken) {
		boolean statusFlag = false;
		if (StringUtils.isNotEmpty(authorizationToken) && StringUtils.isNotBlank(externalAuthBearerToken)) {
			String externalToken = externalAuthBearerToken;
			if (externalToken.contains(",")) {
				List<String> authTokenList = Arrays.asList(externalToken.split(","));
				if (CollectionUtils.isNotEmpty(authTokenList) && authTokenList.contains(authorizationToken)) {
					statusFlag = true;
					return statusFlag;
				}
			}
		}
		return statusFlag;
	}

	@Override
	public String getFirstInternalAuthBearerToken() {
		if (StringUtils.isBlank(internalAuthBearerToken)) {
			return null;
		}
		// Return first token if comma-separated, otherwise return the token itself
		if (internalAuthBearerToken.contains(",")) {
			List<String> authTokenList = Arrays.asList(internalAuthBearerToken.split(","));
			if (CollectionUtils.isNotEmpty(authTokenList)) {
				return authTokenList.get(0).trim();
			}
		}
		return internalAuthBearerToken.trim();
	}

}