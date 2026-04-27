package org.styli.services.customer.pojo.nationalid;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request model for National ID / Passport document validation API.
 * Accepts JSON body with base64-encoded file content.
 * Fields: customerId, storeId, documentIdType, fileType, fileContent (base64 string).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentValidateRequest {

    private String customerId;
    private String storeId;
    private String documentIdType;
    /** "image" or "pdf"; optional – PDF is auto-detected from content if omitted. */
    private String fileType;
    /** File bytes as Base64-encoded string. */
    private String fileContent;

    private String guestId;
}
