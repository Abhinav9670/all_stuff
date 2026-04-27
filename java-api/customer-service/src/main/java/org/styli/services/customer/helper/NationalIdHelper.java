package org.styli.services.customer.helper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.styli.services.customer.component.GcpStorage;

/**
 * Helper for National ID document processing: image-to-PDF conversion (AES-256 encrypted, no password),
 * and storing the PDF to GCP storage.
 */
@Component
public class NationalIdHelper {

    private static final Log LOGGER = LogFactory.getLog(NationalIdHelper.class);

    @Value("${national.id.encryption.secret}")
    private String nationalIdEncryptionSecret;

    @Value("${national.id.default.encryption.secret}")
    private String DEFAULT_ENCRYPTION_SECRET;

    @Value("${national.id.cipher:AES/ECB/PKCS5Padding}")
    private String NATIONAL_ID_CIPHER;

    private static final String STORAGE_CIPHER = "AES/CBC/PKCS5Padding";
    private static final int AES_BLOCK_SIZE = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    private GcpStorage gcpStorage;

    /**
     * Encrypts the National ID number using AES-256 before storing in DB.
     *
     * @param nationalIdNumber plain National ID number (e.g. from request)
     * @return Base64-encoded encrypted string, or null if input is null/empty or encryption fails
     */
    public String encryptNationalIdDetails(String nationalIdNumber) {
        if (nationalIdNumber == null || nationalIdNumber.trim().isEmpty()) {
            return null;
        }
        try {
            SecretKeySpec key = getNationalIdEncryptionKey();
            if (key == null) {
                return null;
            }
            Cipher cipher = Cipher.getInstance(NATIONAL_ID_CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(nationalIdNumber.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            LOGGER.error("Failed to encrypt National ID number", e);
            return null;
        }
    }

    /**
     * Decrypts the National ID number that was encrypted by {@link #decryptNationalIdDetails(String)}.
     * If decryption fails (e.g. value was stored plain earlier), returns the input as-is for backward compatibility.
     *
     * @param encryptedNationalIdData Base64-encoded encrypted string from DB
     * @return decrypted National ID number, or null if input is null/empty
     */
    public String decryptNationalIdDetails(String encryptedNationalIdData) {
        if (encryptedNationalIdData == null || encryptedNationalIdData.trim().isEmpty()) {
            return null;
        }
        try {
            SecretKeySpec key = getNationalIdEncryptionKey();
            if (key == null) {
                return encryptedNationalIdData;
            }
            Cipher cipher = Cipher.getInstance(NATIONAL_ID_CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decoded = Base64.getDecoder().decode(encryptedNationalIdData.trim());
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.debug("National ID number decryption failed (may be plain text): " + e.getMessage());
            return encryptedNationalIdData;
        }
    }

    private SecretKeySpec getNationalIdEncryptionKey() {
        try {
            byte[] keyBytes = nationalIdEncryptionSecret != null
                    ? nationalIdEncryptionSecret.getBytes(StandardCharsets.UTF_8)
                    : DEFAULT_ENCRYPTION_SECRET.getBytes(StandardCharsets.UTF_8);
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            keyBytes = sha256.digest(keyBytes);
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            LOGGER.error("Failed to create National ID encryption key", e);
            return null;
        }
    }

    /**
     * Converts image bytes to a single-page PDF and encrypts it with AES-256 (no password required to open).
     *
     * @param imageBytes raw image bytes (e.g. JPEG/PNG)
     * @return encrypted PDF as byte array, or null on failure
     */
    public byte[] convertImageToProtectedPdf(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return null;
        }
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDImageXObject pdImage = PDImageXObject.createFromByteArray(document, imageBytes, "national-id-image");
            float imgWidth = pdImage.getWidth();
            float imgHeight = pdImage.getHeight();
            PDRectangle mediaBox = page.getMediaBox();
            float pageWidth = mediaBox.getWidth();
            float pageHeight = mediaBox.getHeight();

            // Scale image to fit within page while preserving aspect ratio
            float scale = Math.min(pageWidth / imgWidth, pageHeight / imgHeight);
            float drawWidth = imgWidth * scale;
            float drawHeight = imgHeight * scale;
            float x = (pageWidth - drawWidth) / 2;
            float y = (pageHeight - drawHeight) / 2;

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.drawImage(pdImage, x, y, drawWidth, drawHeight);
            }

            // Encrypt with AES-256 only; empty passwords so no password is required to open
            AccessPermission ap = new AccessPermission();
            ap.setCanPrint(true);
            ap.setCanModify(true);
            ap.setCanExtractContent(true);
            StandardProtectionPolicy spp = new StandardProtectionPolicy("", "", ap);
            spp.setEncryptionKeyLength(256);
            document.protect(spp);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            LOGGER.error("Failed to convert image to encrypted PDF", e);
            return null;
        }
    }

    /**
     * Encrypts an existing PDF with the same AES-256 protection policy used for converted image PDFs
     * (no password required to open). Use this for direct PDF uploads so they are stored with the same
     * encryption as image-converted PDFs.
     *
     * @param pdfBytes raw PDF bytes (unencrypted or already encrypted; if already encrypted, may require password to load)
     * @return encrypted PDF as byte array, or null on failure
     */
    public byte[] encryptPdf(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return null;
        }
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            AccessPermission ap = new AccessPermission();
            ap.setCanPrint(true);
            ap.setCanModify(true);
            ap.setCanExtractContent(true);
            StandardProtectionPolicy spp = new StandardProtectionPolicy("", "", ap);
            spp.setEncryptionKeyLength(256);
            document.protect(spp);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            LOGGER.error("Failed to encrypt PDF", e);
            return null;
        }
    }

    /**
     * Encrypts PDF bytes for storage in GCP. The stored blob is not a valid PDF; only this application
     * can decrypt it with {@link #decryptPdfBytesFromStorage(byte[])}. Anyone with bucket access cannot open the file.
     * Uses AES-256/CBC with a random IV (prepended to ciphertext).
     *
     * @param pdfBytes PDF bytes (plain or already PDF-protected)
     * @return IV (16 bytes) + ciphertext, or null on failure
     */
    public byte[] encryptPdfBytesForStorage(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return null;
        }
        try {
            SecretKeySpec key = getNationalIdEncryptionKey();
            if (key == null) {
                return null;
            }
            byte[] iv = new byte[AES_BLOCK_SIZE];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(STORAGE_CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
            byte[] ciphertext = cipher.doFinal(pdfBytes);
            ByteArrayOutputStream out = new ByteArrayOutputStream(iv.length + ciphertext.length);
            out.write(iv);
            out.write(ciphertext);
            return out.toByteArray();
        } catch (Exception e) {
            LOGGER.error("Failed to encrypt PDF bytes for storage", e);
            return null;
        }
    }

    /**
     * Decrypts bytes that were stored via {@link #encryptPdfBytesForStorage(byte[])}. If decryption fails
     * (e.g. legacy unencrypted PDF in bucket), returns the input as-is for backward compatibility.
     *
     * @param storedBytes IV + ciphertext from GCP, or legacy plain PDF bytes
     * @return decrypted PDF bytes, or original bytes if not in encrypted format
     */
    public byte[] decryptPdfBytesFromStorage(byte[] storedBytes) {
        if (storedBytes == null || storedBytes.length == 0) {
            return storedBytes;
        }
        if (storedBytes.length <= AES_BLOCK_SIZE) {
            return storedBytes;
        }
        try {
            SecretKeySpec key = getNationalIdEncryptionKey();
            if (key == null) {
                return storedBytes;
            }
            byte[] iv = new byte[AES_BLOCK_SIZE];
            System.arraycopy(storedBytes, 0, iv, 0, AES_BLOCK_SIZE);
            int cipherLen = storedBytes.length - AES_BLOCK_SIZE;
            byte[] ciphertext = new byte[cipherLen];
            System.arraycopy(storedBytes, AES_BLOCK_SIZE, ciphertext, 0, cipherLen);
            Cipher cipher = Cipher.getInstance(STORAGE_CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            LOGGER.debug("Storage decryption failed (may be legacy plain PDF): " + e.getMessage());
            return storedBytes;
        }
    }

    /**
     * Stores the (optionally application-encrypted) bytes to GCP bucket NATIONAL_ID_DOC and returns the object path.
     * Call {@link #encryptPdfBytesForStorage(byte[])} on PDF bytes before this if you want bucket content unreadable.
     *
     * @param pdfBytes PDF bytes or encrypted blob
     * @return object path to the stored object, or null on failure
     */
    public String storePdfToGcp(byte[] pdfBytes) {
        return storePdfToGcp(pdfBytes, false);
    }

    /**
     * Stores bytes to GCP NATIONAL_ID_DOC (or NATIONAL_ID_DOC/temp for temporary storage).
     * Use temp=true for document/validate so the file can be moved to permanent when address is saved.
     *
     * @param pdfBytes PDF bytes or encrypted blob
     * @param temp     if true, store under NATIONAL_ID_DOC/temp/
     * @return object path, or null on failure
     */
    public String storePdfToGcp(byte[] pdfBytes, boolean temp) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return null;
        }
        String objectPath = gcpStorage.uploadNationalId(pdfBytes, temp);
        if (objectPath == null) {
            return null;
        }
        return objectPath;
    }
}
