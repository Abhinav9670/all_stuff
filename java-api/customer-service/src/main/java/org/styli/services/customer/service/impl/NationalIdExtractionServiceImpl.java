package org.styli.services.customer.service.impl;

import java.io.IOException;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.styli.services.customer.component.GcpStorage;
import org.styli.services.customer.helper.NationalIdHelper;
import org.styli.services.customer.pojo.address.response.CustomerAddressEntity;
import org.styli.services.customer.pojo.nationalid.NationalIdValidationData;
import org.styli.services.customer.pojo.nationalid.NationalIdValidationResponse;
import org.styli.services.customer.repository.Customer.CustomerAddressEntityRepository;
import org.styli.services.customer.service.NationalIdExtractionService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.api.Blob;
import com.google.protobuf.ByteString;

@Service
public class NationalIdExtractionServiceImpl implements NationalIdExtractionService {

    private static final Log LOGGER = LogFactory.getLog(NationalIdExtractionServiceImpl.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DOCUMENT_TYPE_NATIONAL_ID = "Oman National ID";
    private static final Pattern DATE_SLASH = Pattern.compile("(\\d{1,2})/(\\d{1,2})/(\\d{4})");

    @Value("${gcp.project.id}")
    private String projectId;

    @Value("${vertex.ai.location:us-central1}")
    private String vertexLocation;

    @Value("${vertex.ai.model:gemini-1.5-flash-001}")
    private String vertexModel;

    @Autowired
    private GcpStorage gcpStorage;

    @Autowired
    private NationalIdHelper nationalIdHelper;

    @Autowired
    private CustomerAddressEntityRepository customerAddressEntityRepository;

    @Override
    public NationalIdValidationResponse extractAndValidateDocument(String customerId, byte[] fileBytes, String storeId, String documentIdType, String fileType) {
        NationalIdValidationResponse response = new NationalIdValidationResponse();
        if (fileBytes == null || fileBytes.length == 0) {
            response.setStatus(false);
            response.setMessage("Image is required");
            response.setData(null);
            return response;
        }
        if (documentIdType == null || documentIdType.isBlank()) {
            response.setStatus(false);
            response.setMessage("documentIdType is required (Passport or Oman National ID)");
            response.setData(null);
            return response;
        }
        byte[] pdfBytes;
        if ("pdf".equalsIgnoreCase(fileType != null ? fileType.trim() : "")) {
            pdfBytes = nationalIdHelper.encryptPdf(fileBytes);
        } else {
            pdfBytes = nationalIdHelper.convertImageToProtectedPdf(fileBytes);
        }
        String pdfFilepath = null;
        if (pdfBytes != null) {
            byte[] bytesToStore = nationalIdHelper.encryptPdfBytesForStorage(pdfBytes);
            pdfFilepath = nationalIdHelper.storePdfToGcp(bytesToStore != null ? bytesToStore : pdfBytes, true);
        }
        if (pdfFilepath == null) {
            pdfFilepath = "";
        }

            String idNumber = "";
            String expiryDate = "";
            String fullName = "";

            NationalIdValidationData data = new NationalIdValidationData();
            data.setCustomerId(customerId);
            data.setIdNumber(idNumber != null ? idNumber : "");
            data.setExpiryDate(expiryDate != null ? expiryDate : "");
            data.setFullName(fullName != null ? fullName : "");
            data.setFilePath(pdfFilepath);

            response.setStatus(true);
            response.setMessage("National ID validated successfully");
            response.setData(data);
            return response;
    }

    @Override
    public byte[] getStoredNationalIdPdfBytes(Integer addressId, Integer customerId) {
        if (addressId == null || customerId == null) {
            return null;
        }
        CustomerAddressEntity address = customerAddressEntityRepository.findByEntityIdAndParentId(addressId, customerId);
        if (address == null) {
            LOGGER.warn("Address not found for addressId=" + addressId + ", customerId=" + customerId);
            return null;
        }
        String encryptedImageData = address.getNationalIdImageData();
        if (encryptedImageData == null || encryptedImageData.isBlank()) {
            LOGGER.warn("No national ID image data for addressId=" + addressId);
            return null;
        }
        String gcpPath = nationalIdHelper.decryptNationalIdDetails(encryptedImageData);
        if (gcpPath == null || gcpPath.isBlank()) {
            return null;
        }
        byte[] storedBytes = gcpStorage.getNationalIdPdfBytes(gcpPath);
        if (storedBytes == null) {
            return null;
        }
        return nationalIdHelper.decryptPdfBytesFromStorage(storedBytes);
    }
}
