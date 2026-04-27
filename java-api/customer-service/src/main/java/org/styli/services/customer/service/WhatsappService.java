package org.styli.services.customer.service;

import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestHeader;
import org.styli.services.customer.pojo.GenericApiResponse;
import org.styli.services.customer.pojo.whatsapp.WhatsappSignupBucketObject;
import org.styli.services.customer.pojo.whatsapp.WhatsappSignupRequest;
import org.styli.services.customer.pojo.whatsapp.WhatsappSignupResponse;

import java.util.Map;

/**
 * Created on 05-Oct-2022
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */


@Service
public interface WhatsappService {


    public GenericApiResponse<WhatsappSignupResponse> createWhatsappSignupLink(
            WhatsappSignupRequest requestBody, @RequestHeader Map<String, Object> requestHeader);

    /**
     * Return the payload object if {@code token} is validated successfully or else returns null.
     *
     * @param token   the token generated from API:
     *                <blockquote><pre>
     *                    /whatsapp/signup
     *                </pre></blockquote>
     * @return        the specified {@code JwtPayload} in the given token.
     */
    public WhatsappSignupBucketObject getValidPayloadFromToken(String token);

    /**
     * Removes the {@code token} from cache and returns the success flag.
     *
     * @param token   the token generated from API:
     *                <blockquote><pre>
     *                    /whatsapp/signup
     *                </pre></blockquote>
     * @return        returns success flag for remove token operation.
     */
    public boolean clearToken(String token);
}
