package org.styli.services.customer.pojo.address.response;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * National ID or Passport details attached to an address.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NationalId implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Type of document: e.g. "passport", "oman_national_id" */
    private String type;

    /** CDN or GCS path/URL of the document file */
    private String filePath;

    /** Document number (civil number or passport number) */
    private String number;

    /** Expiration date e.g. "31-12-2025" */
    private String expirationDate;
}
