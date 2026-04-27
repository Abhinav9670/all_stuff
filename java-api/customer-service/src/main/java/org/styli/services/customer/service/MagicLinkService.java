package org.styli.services.customer.service;

import org.styli.services.customer.pojo.MagicLinkRequest;
import org.styli.services.customer.pojo.MagicLinkResponse;
import org.styli.services.customer.pojo.MagiclinkValidationRequest;

import java.util.Map;

/**
 * @author Biswabhusan Pradhan
 * @project customer-service
 */
public interface MagicLinkService {

    MagicLinkResponse createAndSendMagicLink(MagicLinkRequest request, String magicLinkBaseUrl, String mailSubject, String mailContent);

    MagicLinkResponse validateMagicLink(MagiclinkValidationRequest magicLinkRequestBody, Map<String, String> requestHeader);
}
