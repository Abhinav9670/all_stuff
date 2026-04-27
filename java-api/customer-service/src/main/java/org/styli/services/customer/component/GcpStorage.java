package org.styli.services.customer.component;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.styli.services.customer.utility.Constants;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.CopyWriter;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.storage.Storage.BlobListOption;

/**
 * GCP Storage activity
 * @author chandanbehera
 *
 */
@Component
public class GcpStorage {

	private static final Log LOGGER = LogFactory.getLog(GcpStorage.class);

	@Value("${gcp.project.id}")
	private String gcpProjectId;

	@Value("${gcp.bucket.name}")
	private String gcpBucketName;

	@Value("${gcp.bucket.url}")
	private String gcpBucketPath;

	@Value("${env}")
	private String env;

	public void cacheAdrsmprJson(String country) {
		try {
			LOGGER.info("Processing adrsmpr josn from bucket. Country : " + country);
			LOGGER.info(" Project ID : " + gcpProjectId + " bucket name :" + gcpBucketName);
			StorageOptions storage = StorageOptions.newBuilder().setCredentials(GoogleCredentials.getApplicationDefault())
					.setProjectId(gcpProjectId)
					.build();
			String fullPath = env + "/address_" + country + ".json";
			Blob blob = ((Storage) storage).get(gcpBucketName, fullPath);
			if (Objects.isNull(blob)) {
				LOGGER.error("Error in loading address mapper json. No content.");
				return;
			}
			String content = new String(blob.getContent());
			LOGGER.info("India Address JSON loaded from bucket : Length : " + content.length());
			Constants.setAddressMapper(country, String.valueOf(content));
		} catch (IOException e) {
			LOGGER.error("Error in loading address mapper json. Error : " + e);
		}

	}

	private static final String NATIONAL_ID_DOC_PREFIX = "NATIONAL_ID_DOC/";
	private static final String NATIONAL_ID_DOC_TEMP_PREFIX = "NATIONAL_ID_DOC/temp/";

	/**
	 * Uploads password-protected PDF bytes to the NATIONAL_ID_DOC GCS bucket and returns the object path.
	 * Object is created with private ACL so it is not publicly readable via URL.
	 *
	 * @param pdfBytes PDF file content (e.g. AES-256 protected)
	 * @return object path e.g. "gcpBucketPath/NATIONAL_ID_DOC/uuid.pdf" or null on failure
	 */
	public String uploadNationalId(byte[] pdfBytes) {
		return uploadNationalId(pdfBytes, false);
	}

	/**
	 * Uploads PDF bytes to NATIONAL_ID_DOC (or NATIONAL_ID_DOC/temp for temporary storage).
	 *
	 * @param pdfBytes    PDF file content (e.g. AES-256 protected)
	 * @param tempStorage if true, store under NATIONAL_ID_DOC/temp/ for later move on address save
	 * @return object path e.g. "gcpBucketPath/NATIONAL_ID_DOC/temp/uuid.pdf" or "gcpBucketPath/NATIONAL_ID_DOC/uuid.pdf", or null on failure
	 */
	public String uploadNationalId(byte[] pdfBytes, boolean tempStorage) {
		if (pdfBytes == null || pdfBytes.length == 0) {
			return null;
		}
		try {
			Storage storage = getStorage();
			String objectName = tempStorage
					? NATIONAL_ID_DOC_TEMP_PREFIX + UUID.randomUUID().toString() + ".pdf"
					: NATIONAL_ID_DOC_PREFIX + UUID.randomUUID().toString() + ".pdf";
			BlobId blobId = BlobId.of(gcpBucketName, objectName);
			BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("application/pdf").build();
			storage.create(blobInfo, pdfBytes, Storage.BlobTargetOption.predefinedAcl(Storage.PredefinedAcl.PRIVATE));
			LOGGER.info("Uploaded national ID PDF to gs://" + gcpBucketName + "/" + objectName);
			return gcpBucketPath + "/" + objectName;
		} catch (IOException e) {
			LOGGER.error("Error uploading national ID PDF to GCS: " + e);
			return null;
		}
	}

	/**
	 * Moves a National ID PDF from temp (NATIONAL_ID_DOC/temp/xxx.pdf) to permanent (NATIONAL_ID_DOC/xxx.pdf),
	 * then deletes the temp object. Use when user saves/updates address with nationalId.filePath set to the temp path.
	 *
	 * @param tempObjectPath path returned from upload (e.g. "gcpBucketPath/NATIONAL_ID_DOC/temp/uuid.pdf" or "NATIONAL_ID_DOC/temp/uuid.pdf")
	 * @return permanent path e.g. "gcpBucketPath/NATIONAL_ID_DOC/uuid.pdf", or null if not a temp path or on failure
	 */
	public String moveNationalIdFromTempToPermanent(String tempObjectPath) {
		if (tempObjectPath == null || tempObjectPath.trim().isEmpty()) {
			return null;
		}
		String objectName = toObjectName(tempObjectPath);
		if (objectName == null || !objectName.startsWith(NATIONAL_ID_DOC_TEMP_PREFIX)) {
			LOGGER.warn("moveNationalIdFromTempToPermanent: path is not a temp path, skipping move: " + tempObjectPath);
			return null;
		}
		try {
			Storage storage = getStorage();
			Blob sourceBlob = storage.get(gcpBucketName, objectName);
			if (sourceBlob == null && gcpBucketPath != null && !gcpBucketPath.equals(gcpBucketName)) {
				sourceBlob = storage.get(gcpBucketPath, objectName);
			}
			if (sourceBlob == null) {
				LOGGER.warn("moveNationalIdFromTempToPermanent: temp blob not found: " + objectName);
				return null;
			}
			String fileName = objectName.substring(NATIONAL_ID_DOC_TEMP_PREFIX.length());
			String destObjectName = NATIONAL_ID_DOC_PREFIX + fileName;
			String bucketName = sourceBlob.getBucket();
			BlobId destId = BlobId.of(bucketName, destObjectName);
			CopyWriter copyWriter = sourceBlob.copyTo(destId);
			copyWriter.getResult();
			sourceBlob.delete();
			LOGGER.info("Moved national ID PDF from temp to permanent: " + objectName + " -> " + destObjectName);
			return gcpBucketPath + "/" + destObjectName;
		} catch (Exception e) {
			LOGGER.error("Error moving national ID PDF from temp to permanent: " + tempObjectPath + " - " + e);
			return null;
		}
	}

	/**
	 * Deletes all objects under NATIONAL_ID_DOC/temp/ (orphaned files never associated with an address).
	 * Intended to be run by a daily scheduler.
	 *
	 * @return number of objects deleted, or -1 on error
	 */
	public int deleteAllNationalIdTempFiles() {
		try {
			Storage storage = getStorage();
			Iterable<Blob> blobs = storage.list(gcpBucketName, BlobListOption.prefix(NATIONAL_ID_DOC_TEMP_PREFIX)).iterateAll();
			int count = 0;
			for (Blob blob : blobs) {
				blob.delete();
				count++;
			}
			if (count > 0) {
				LOGGER.info("Deleted " + count + " orphaned national ID temp file(s) from " + NATIONAL_ID_DOC_TEMP_PREFIX);
			}
			return count;
		} catch (Exception e) {
			LOGGER.error("Error deleting national ID temp files: " + e);
			return -1;
		}
	}

	private Storage getStorage() throws IOException {
		return StorageOptions.newBuilder()
				.setCredentials(GoogleCredentials.getApplicationDefault())
				.setProjectId(gcpProjectId)
				.build()
				.getService();
	}

	/** Normalizes path to bucket object name (e.g. NATIONAL_ID_DOC/temp/uuid.pdf). */
	private String toObjectName(String objectPath) {
		if (objectPath == null) return null;
		String trimmed = objectPath.trim();
		int idx = trimmed.indexOf(NATIONAL_ID_DOC_PREFIX);
		return idx >= 0 ? trimmed.substring(idx) : null;
	}

	/**
	 * Downloads the National ID PDF from GCS by object path and returns its bytes.
	 * Upload returns "gcpBucketPath/NATIONAL_ID_DOC/uuid.pdf" but the blob is stored under name "NATIONAL_ID_DOC/uuid.pdf";
	 * this method strips the bucket-path prefix when present so the correct blob name is used.
	 *
	 * @param objectPath object path (e.g. "gcpBucketPath/NATIONAL_ID_DOC/uuid.pdf" or "NATIONAL_ID_DOC/uuid.pdf")
	 * @return PDF file bytes, or null if not found or on error
	 */
	public byte[] getNationalIdPdfBytes(String objectPath) {
		if (objectPath == null || objectPath.trim().isEmpty()) {
			return null;
		}
		try {
			Storage storage = StorageOptions.newBuilder()
					.setCredentials(GoogleCredentials.getApplicationDefault())
					.setProjectId(gcpProjectId)
					.build()
					.getService();
			String objectName = objectPath.trim();
			// Strip any prefix so we use the actual blob name in the bucket (e.g. "NATIONAL_ID_DOC/uuid.pdf" or "NATIONAL_ID_DOC/temp/uuid.pdf").
			int idx = objectName.indexOf(NATIONAL_ID_DOC_PREFIX);
			if (idx >= 0) {
				objectName = objectName.substring(idx);
			}
			// Try primary bucket (gcpBucketName)
			Blob blob = storage.get(gcpBucketName, objectName);
			if (blob == null && gcpBucketPath != null && !gcpBucketPath.isEmpty()
					&& !gcpBucketPath.equals(gcpBucketName)) {
				// Fallback: actual GCS bucket may be named after gcpBucketPath (e.g. dev-bucket.stylifashion.com)
				LOGGER.info("National ID PDF not in bucket " + gcpBucketName + ", trying bucket " + gcpBucketPath);
				blob = storage.get(gcpBucketPath, objectName);
			}
			if (blob == null) {
				LOGGER.warn("National ID PDF not found in GCS. Tried bucket=" + gcpBucketName
						+ (gcpBucketPath != null && !gcpBucketPath.equals(gcpBucketName) ? " and bucket=" + gcpBucketPath : "")
						+ ", objectName=" + objectName + ". Verify in GCP Console that the object exists.");
				return null;
			}
			return blob.getContent();
		} catch (IOException e) {
			LOGGER.error("Error downloading National ID PDF from GCS: " + objectPath + " - " + e);
			return null;
		} catch (StorageException e) {
			LOGGER.error("GCS StorageException downloading National ID PDF: " + objectPath + " - " + e.getMessage(), e);
			return null;
		} catch (RuntimeException e) {
			LOGGER.error("Unexpected error downloading National ID PDF from GCS: " + objectPath + " - " + e.getMessage(), e);
			return null;
		}
	}

}
