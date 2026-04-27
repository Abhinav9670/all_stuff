package org.styli.services.order.component;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.styli.services.order.config.GcsShippingLabelConfig;
import org.styli.services.order.utility.Constants;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Component
public class GcpStorage {

	private static final Log LOGGER = LogFactory.getLog(GcpStorage.class);

	@Value("${gcp.project.id}")
	private String gcpProjectId;

	@Autowired
	private GcsShippingLabelConfig gcsConfig;

	private Storage storage;

	@PostConstruct
	public void init() {
		try {
			this.storage = StorageOptions.newBuilder()
					.setCredentials(GoogleCredentials.getApplicationDefault())
					.setProjectId(gcpProjectId)
					.build()
					.getService();
			LOGGER.info("GCP Storage initialized successfully for project: " + gcpProjectId);
		} catch (Exception e) {
			LOGGER.warn("Failed to initialize Google Cloud Storage client. GCP features will be disabled.", e);
			this.storage = null;
		}
	}

	/**
	 * Check if an object exists in GCS bucket
	 * 
	 * @param bucketName Target bucket name
	 * @param objectPath Path within bucket
	 * @return true if object exists, false otherwise
	 */
	public boolean objectExists(String bucketName, String objectPath) {
		if (storage == null) {
			LOGGER.warn("GCP Storage is not initialized, cannot check object existence");
			return false;
		}

		try {
			BlobId blobId = BlobId.of(bucketName, objectPath);
			Blob blob = storage.get(blobId);
			boolean exists = blob != null && blob.exists();
			LOGGER.debug("Object existence check for gs://" + bucketName + "/" + objectPath + ": " + exists);
			return exists;
		} catch (Exception e) {
			LOGGER.error("Error checking object existence in GCS: " + e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Check if an object exists using gs:// path format
	 * 
	 * @param gcsObjectPath Full GCS path (gs://bucket/path)
	 * @return true if object exists, false otherwise
	 */
	public boolean objectExistsFromPath(String gcsObjectPath) {
		String bucketName = extractBucketFromGcsPath(gcsObjectPath);
		String objectPath = extractObjectFromGcsPath(gcsObjectPath);
		
		if (bucketName == null || objectPath == null) {
			LOGGER.warn("Invalid GCS path format: " + gcsObjectPath);
			return false;
		}
		
		return objectExists(bucketName, objectPath);
	}

	/**
	 * Upload file content to GCS bucket
	 * 
	 * @param bucketName Target bucket name
	 * @param objectPath Path within bucket (e.g., "shipping-labels/2024/order-123.pdf")
	 * @param content File content as byte array
	 * @param contentType MIME type (e.g., "application/pdf")
	 * @return GCS object path (gs://bucket/path)
	 * @throws IOException if upload fails
	 */
	public String uploadFile(String bucketName, String objectPath, byte[] content, String contentType) 
			throws IOException {
		
		if (storage == null) {
			throw new IOException("GCP Storage is not initialized");
		}

		try {
			BlobId blobId = BlobId.of(bucketName, objectPath);
			BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
					.setContentType(contentType)
					.build();

			// Upload file with no public access
			storage.create(blobInfo, content, Storage.BlobTargetOption.doesNotExist());
			
			String gcsPath = String.format("gs://%s/%s", bucketName, objectPath);
			LOGGER.info("File uploaded successfully to: " + gcsPath + " (size: " + content.length + " bytes)");
			
			return gcsPath;
			
		} catch (Exception e) {
			LOGGER.error("Error uploading file to GCS: " + e.getMessage(), e);
			throw new IOException("Failed to upload file to GCS", e);
		}
	}

	/**
	 * Upload or reuse existing file in GCS bucket
	 * Checks if file exists first, if so returns existing path without upload
	 * 
	 * @param bucketName Target bucket name
	 * @param objectPath Path within bucket
	 * @param content File content as byte array (only used if upload needed)
	 * @param contentType MIME type
	 * @return GCS object path (gs://bucket/path)
	 * @throws IOException if upload fails
	 */
	public String uploadOrReuseFile(String bucketName, String objectPath, byte[] content, String contentType) 
			throws IOException {
		
		if (storage == null) {
			throw new IOException("GCP Storage is not initialized");
		}

		String gcsPath = String.format("gs://%s/%s", bucketName, objectPath);
		
		// Check if object already exists
		if (objectExists(bucketName, objectPath)) {
			LOGGER.info("Object already exists in GCS, reusing: " + gcsPath);
			return gcsPath;
		}

		// Object doesn't exist, upload it
		LOGGER.info("Object doesn't exist, uploading to: " + gcsPath);
		return uploadFile(bucketName, objectPath, content, contentType);
	}

	/**
	 * Generate signed URL from GCS object path (gs://bucket/path format)
	 * 
	 * @param gcsObjectPath Full GCS path (gs://bucket/path)
	 * @param expiryMinutes Expiry duration in minutes
	 * @return Signed URL as string
	 * @throws SignedUrlGenerationException if generation fails
	 */
	public String generateSignedUrlFromPath(String gcsObjectPath, int expiryMinutes) 
			throws SignedUrlGenerationException {
		
		try {
			if (storage == null) {
				throw new SignedUrlGenerationException("GCP Storage is not initialized", null);
			}

			String bucketName = extractBucketFromGcsPath(gcsObjectPath);
			String objectName = extractObjectFromGcsPath(gcsObjectPath);

			if (bucketName == null || objectName == null) {
				throw new IllegalArgumentException("Invalid GCS path: " + gcsObjectPath);
			}

			BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, objectName).build();
			URL signedUrl = storage.signUrl(blobInfo, expiryMinutes, TimeUnit.MINUTES);

			LOGGER.info("Generated signed URL for: " + gcsObjectPath + " (expires in " + expiryMinutes + " minutes)");
			return signedUrl.toString();
			
		} catch (Exception e) {
			LOGGER.error("Error generating signed URL from path: " + gcsObjectPath, e);
			throw new SignedUrlGenerationException("Failed to generate signed URL", e);
		}
	}

	/**
	 * Legacy method - Generate signed URL from storage.googleapis.com URL
	 * This method is kept for backward compatibility with existing code
	 */
	public String generateSignedUrl(String objectUrl) throws SignedUrlGenerationException {
		try {
			if (storage == null) {
				throw new SignedUrlGenerationException("GCP Storage is not initialized", null);
			}
			String bucketName = extractBucketNameFromUrl(objectUrl);
			String objectName = extractObjectNameFromUrl(objectUrl);

			if (bucketName == null || objectName == null) {
				throw new IllegalArgumentException("Failed to parse bucket or object name from URL: " + objectUrl);
			}

			BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, objectName).build();
			URL signedUrl = storage.signUrl(
					blobInfo,
					Constants.orderCredentials != null ? Constants.orderCredentials.getSignedUrlExpiry() : 15,
					TimeUnit.MINUTES
			);

			return signedUrl.toString();
		} catch (Exception e) {
			LOGGER.error("Error generating signed URL", e);
			throw new SignedUrlGenerationException("Failed to generate signed URL", e);
		}
	}
	private String[] getPathParts(String objectUrl) {
		try {
			URL url = new URL(objectUrl);
			return Arrays.stream(url.getPath().split("/"))
					.filter(part -> !part.isEmpty())
					.toArray(String[]::new);
		} catch (MalformedURLException e) {
			LOGGER.error("Invalid object URL: " + objectUrl, e);
			return new String[0];
		}
	}

	private String extractBucketNameFromUrl(String objectUrl) {
		String[] parts = getPathParts(objectUrl);
		return parts.length > 0 ? parts[0] : null;
	}

	private String extractObjectNameFromUrl(String objectUrl) {
		String[] parts = getPathParts(objectUrl);
		if (parts.length <= 1) return null;
		return String.join("/", Arrays.copyOfRange(parts, 1, parts.length));
	}

	/**
	 * Extract bucket name from gs://bucket/path format
	 */
	private String extractBucketFromGcsPath(String gcsPath) {
		if (gcsPath == null || !gcsPath.startsWith("gs://")) {
			return null;
		}
		String pathWithoutPrefix = gcsPath.substring(5); // Remove "gs://"
		int slashIndex = pathWithoutPrefix.indexOf('/');
		return slashIndex > 0 ? pathWithoutPrefix.substring(0, slashIndex) : pathWithoutPrefix;
	}

	/**
	 * Extract object path from gs://bucket/path format
	 */
	private String extractObjectFromGcsPath(String gcsPath) {
		if (gcsPath == null || !gcsPath.startsWith("gs://")) {
			return null;
		}
		String pathWithoutPrefix = gcsPath.substring(5); // Remove "gs://"
		int slashIndex = pathWithoutPrefix.indexOf('/');
		return slashIndex > 0 ? pathWithoutPrefix.substring(slashIndex + 1) : null;
	}
}
