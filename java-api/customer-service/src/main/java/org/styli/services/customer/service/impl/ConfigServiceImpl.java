package org.styli.services.customer.service.impl;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.styli.services.customer.service.ConfigService;
import java.util.stream.Collectors;


import java.util.Arrays;
import java.util.List;


/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Component
public class ConfigServiceImpl implements ConfigService {
	
	private static final Log LOGGER = LogFactory.getLog(ConfigService.class);

    
    @Value("${auth.internal.header.bearer.token}")
    private String internalAuthBearerToken;

	@Value("${auth.external.header.bearer.token}")
	private String externalAuthBearerToken;


	
	
	/**
	 *
	 */
	@Override
	public boolean checkAuthorization(String intenalAuthorizationToken, String externalAuthorizationToken) {
		
		boolean statusFlag = false;
		LOGGER.info("externalAuthorizationToken:"+externalAuthorizationToken);
		LOGGER.info("intenalAuthorizationToken:"+intenalAuthorizationToken);
		if(StringUtils.isNotEmpty(intenalAuthorizationToken) && StringUtils.isNotBlank(internalAuthBearerToken)) {
			
			String intenalToken = internalAuthBearerToken;
			
			if(intenalToken.contains(",")) {
				
				List<String> authTokenList = Arrays.asList(intenalToken.split(","));
				
				if(CollectionUtils.isNotEmpty(authTokenList) && authTokenList.contains(intenalAuthorizationToken)) {
					
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
		
		LOGGER.info("checkAuthorizationInternal: "+authorizationToken);
		if(StringUtils.isNotEmpty(authorizationToken) && StringUtils.isNotBlank(internalAuthBearerToken)) {
			
			LOGGER.info("internalAuthBearerToken: "+internalAuthBearerToken);
			String intenalToken = internalAuthBearerToken;
			
			if(intenalToken.contains(",")) {
				
				List<String> authTokenList = Arrays.asList(intenalToken.split(","));
				
				LOGGER.info("intenalToken: "+intenalToken);
				
				if(CollectionUtils.isNotEmpty(authTokenList) && authTokenList.contains(authorizationToken)) {
					
					statusFlag = true;
					
					LOGGER.info("statusFlag: "+statusFlag);
					
					return statusFlag;
				}
			}
		}
		return statusFlag;
	}

	@Override
	public boolean checkAuthorizationExternal(String authorizationToken) {

		LOGGER.info("Authorization header value: [{}]" +authorizationToken);
		LOGGER.info("Expected token(s): [{}]" +externalAuthBearerToken);

		if (StringUtils.isBlank(authorizationToken) || StringUtils.isBlank(externalAuthBearerToken)) {
			LOGGER.info("Either authorization token or secret token is blank");
			return false;
		}

		// Split expected tokens
		List<String> expectedTokens = Arrays.stream(externalAuthBearerToken.split(","))
				.map(String::trim)
				.toList();

		// Split incoming tokens
		List<String> incomingTokens = Arrays.stream(authorizationToken.split(","))
				.map(String::trim)
				.toList();

		// Check if any incoming token matches any expected token
		for (String incoming : incomingTokens) {
			for (String expected : expectedTokens) {
				LOGGER.info("Comparing: input=[{}] vs expected=[{}]" +incoming);
				if (incoming.equals(expected)) {
					LOGGER.info("Token match successful.");
					return true;
				}
			}
		}

		LOGGER.info("No matching token found. Authorization failed.");
		return false;
	}

	@Override
	public String getFirstInternalAuthBearerToken() {
		String result = "";
		try{
			if(StringUtils.isNotBlank(internalAuthBearerToken)) {
				final String[] chunks = internalAuthBearerToken.split(",");
				if(chunks.length > 0 && StringUtils.isNotBlank(chunks[0])) {
					result = chunks[0];
				}
			}
		} catch (Exception e) {
			LOGGER.info("getFirstInternalAuthBearerToken error: " + e.getMessage());
		}
		return result;
	}

	@Override
	public String getFirstExternalAuthBearerToken() {
		String result = "";
		try{
			if(StringUtils.isNotBlank(externalAuthBearerToken)) {
				final String[] chunks = externalAuthBearerToken.split(",");
				if(chunks.length > 0 && StringUtils.isNotBlank(chunks[0])) {
					result = chunks[0];
				}
			}
		} catch (Exception e) {
			LOGGER.info("getFirstExternalAuthBearerToken error: " + e.getMessage());
		}
		return result;
	}


}
