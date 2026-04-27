package org.styli.services.customer.pojo;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Biswabhusan Pradhan
 * @project customer-service
 */
@Data
public class MagicLinkRequest {
    private boolean loginStatus;
    private String type;
    private String email;
    private String store;
    private String langCode;
    private String requestSource;
    private String redirectUrl;
    private LinkedHashMap<String, String> additionalParams;

    public String getEmail() {
        return email != null ? email.toLowerCase() : null;
    }

    public String getLangCode() {
        return langCode != null ? langCode : "en";
    }
}
