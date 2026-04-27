package org.styli.services.customer.service.impl;

import java.util.Arrays;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.styli.services.customer.exception.CustomerException;
import org.styli.services.customer.model.Store;
import org.styli.services.customer.repository.StoreRepository;

@Component
public class CustomRestTemplate {
    private static final Log LOGGER = LogFactory.getLog(CustomRestTemplate.class);

    @Autowired
    @Qualifier("withoutEureka")
    private RestTemplate withoutEurekarestTemplate;

    @Autowired
    StoreRepository storeRepository;

    /**
     * @param requestHeader
     * @param email
     * @return
     * @throws CustomerException
     */
    public String resetPassword(String email, Integer storeId, String magentoBaseUrl)
            throws CustomerException {

        HttpHeaders headers = new HttpHeaders();

        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));

        HttpEntity entity = new HttpEntity(headers);
        String responseString = null;
        try {

            String url = magentoBaseUrl + "/rest/V1/customers/password";

            if (null != storeId) {

                Store store = storeRepository.findByStoreId(storeId);

                if (null != store) {

                    String storeCode = store.getCode();
                    url = magentoBaseUrl + "/rest/" + storeCode + "/V1/customers/password";
                }
            }

            String sbUrl = new StringBuilder(url).append("?email=").append(email).append("&template=email_reset")
                    .toString();

            LOGGER.info("Magento Password Forget URL:" + sbUrl);

            ResponseEntity<String> response = withoutEurekarestTemplate.exchange(sbUrl, HttpMethod.PUT, entity,
                    String.class);

            if (response.getStatusCode() == HttpStatus.OK) {

                responseString = response.getBody();

            }

        } catch (HttpStatusCodeException e) {

            LOGGER.info("Exception::" + e.getResponseBodyAsString());
            if (e.getResponseBodyAsString().length() > 11) {

                throw new CustomerException("400",
                        StringUtils.substringBetween(e.getResponseBodyAsString(), "message\":\"", "\"}"));

            } else {

                throw new CustomerException("400", e.getResponseBodyAsString());
            }
        }

        return responseString;
    }

}
