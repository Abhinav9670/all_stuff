package org.styli.services.customer.helper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.styli.services.customer.pojo.registration.request.GetQuoteRequest;
import org.styli.services.customer.pojo.registration.request.UpdateQuoteRequest;
import org.styli.services.customer.utility.Constants;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;

@Component
public class ExternalQuoteHelper {

    private static final Log LOGGER = LogFactory.getLog(ExternalQuoteHelper.class);

    private static final String USER_AGENT = "user-agent";
    private static final String TOKEN = "Token";
    private static final String X_HEADER_TOKEN = "x-header-token";
    private static final String X_SOURCE = "x-source";
    private static final String X_CLIENT_VERSION = "x-client-version";
    @Autowired
    @Qualifier("restTemplateBuilder")
    private RestTemplate restTemplate;
    @Value("${quote.service.base.url}")
    String quoteServiceBaseUrl;
    private static final String V6_GET_QUOTE_SUB_URL = "/rest/quote/auth/v6/get";
    private static final String V6_UPDATE_QUOTE_SUB_URL = "/rest/quote/auth/v6/update";

    public ResponseEntity<String> fetchQuote(JSONObject request, String tokenHeader,
                                       String xHeaderToken, String xSource, String xClientVersion) {
        GetQuoteRequest payload = buildGetQuoteRequest(request);
        HttpHeaders headers = buildQuoteHeaders(tokenHeader, xHeaderToken, xSource, xClientVersion);

        URI uri = buildQuoteUri(V6_GET_QUOTE_SUB_URL);
        if (uri == null) {
            LOGGER.error("Failed to build URI for fetchQuote request.");
            return ResponseEntity.badRequest().build();
        }

        RequestEntity<GetQuoteRequest> requestEntity = new RequestEntity<>(payload, headers, HttpMethod.POST, uri);
        return restTemplate.exchange(requestEntity, String.class);
    }

    public ResponseEntity<String> updateQuote(JSONObject request, String tokenHeader,
                                             String xHeaderToken, String xSource, String xClientVersion) {
        UpdateQuoteRequest payload = buildUpdateQuoteRequest(request);
        HttpHeaders headers = buildQuoteHeaders(tokenHeader, xHeaderToken, xSource, xClientVersion);

        URI uri = buildQuoteUri(V6_UPDATE_QUOTE_SUB_URL);
        if (uri == null) {
            LOGGER.error("Failed to build URI for Update Quote request.");
            return ResponseEntity.badRequest().build();
        }

        RequestEntity<UpdateQuoteRequest> requestEntity = new RequestEntity<>(payload, headers, HttpMethod.POST, uri);
        return restTemplate.exchange(requestEntity, String.class);
    }

    private GetQuoteRequest buildGetQuoteRequest(JSONObject request) {
        return GetQuoteRequest.builder()
                .bagView(1)
                .customerId(request.optInt("customerId", 0))
                .storeId(request.optInt("storeId", 0))
                .build();
    }
    private UpdateQuoteRequest buildUpdateQuoteRequest(JSONObject request) {
        return UpdateQuoteRequest.builder()
                .customerId(request.optInt("customerId", 0))
                .storeId(request.optInt("storeId", 0))
                .phoneNumber(request.optString("phoneNumber", ""))
                .build();
    }
    private HttpHeaders buildQuoteHeaders(String tokenHeader,String xHeaderToken,String xSource,String xClientVersion) {
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        requestHeaders.add(USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);
        requestHeaders.set(TOKEN, tokenHeader);
        requestHeaders.set(X_HEADER_TOKEN, xHeaderToken);
        requestHeaders.set(X_SOURCE, xSource);
        requestHeaders.set(X_CLIENT_VERSION, xClientVersion);
        return requestHeaders;
    }


    private URI buildQuoteUri(String subUrl) {
        try {
            return new URI(quoteServiceBaseUrl + subUrl);
        } catch (URISyntaxException e) {
            LOGGER.error("Invalid URI syntax: " + quoteServiceBaseUrl + subUrl, e);
            return null;
        }
    }
}
