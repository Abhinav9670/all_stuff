package org.styli.services.customer.service;

import org.styli.services.customer.pojo.nationalid.NationalIdValidationResponse;

/**
 * Service for extracting and validating National ID or Passport document data
 * from images using GCP Vertex AI.
 */
public interface NationalIdExtractionService {

    /**
     * Extracts document details from National ID or Passport image/PDF using Vertex AI,
     * uploads the file to storage, and returns validation response.
     *
     * @param customerId    customer identifier
     * @param fileBytes     raw bytes of the document (image or PDF depending on fileType)
     * @param storeId       store identifier
     * @param documentIdType "Passport" or "Oman National ID"
     * @param fileType      "image" to convert image to protected PDF, "pdf" to use bytes as PDF directly
     * @return NationalIdValidationResponse with status, message and extracted data (idNumber, expiryDate, fullName, imageData URL)
     */
    NationalIdValidationResponse extractAndValidateDocument(String customerId, byte[] fileBytes, String storeId, String documentIdType, String fileType);

    /**
     * Gets the stored National ID PDF for an address: loads address by addressId and customerId,
     * decrypts the imageData (GCP path) via {@link org.styli.services.customer.helper.NationalIdHelper#decryptNationalIdDetails(String)},
     * downloads the file from GCP and returns it as byte array.
     *
     * @param addressId  address entity id
     * @param customerId customer (parent) id
     * @return PDF bytes, or null if address not found, no imageData, or download failed
     */
    byte[] getStoredNationalIdPdfBytes(Integer addressId, Integer customerId);
}
