package org.styli.services.order.helper;

import java.beans.PropertyDescriptor;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Base64;
import java.util.Calendar;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.model.rma.AmastyRmaRequest;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderAddress;
import org.styli.services.order.model.sales.SalesOrderStatusHistory;
import org.styli.services.order.pojo.CashfreeDetails;
import org.styli.services.order.pojo.RefundPaymentRespone;
import org.styli.services.order.pojo.cashfree.CashfreeRefundDTO;
import org.styli.services.order.pojo.cashfree.CashgramDataDTO;
import org.styli.services.order.pojo.cashfree.CashgramRequestDTO;
import org.styli.services.order.pojo.cashfree.CashgramResponseDTO;
import org.styli.services.order.pojo.cashfree.CashgramWebhookDTO;
import org.styli.services.order.pojo.order.PaymentReturnAdditioanls;
import org.styli.services.order.utility.Constants;

@Component
public class CashfreeHelper {

	private static final Log LOGGER = LogFactory.getLog(CashfreeHelper.class);
	private static final String RESPONSE = " Response : ";
	private static final String XCLIENTID = "x-client-id";
	private static final String CLIENTSECRET = "x-client-secret";
	private static final String X_CF_SIGNATURE = "X-Cf-Signature";
	private static final String APPVERSION = "x-api-version";
	private static final String AUTHORIZATION = "Authorization";

	@Autowired
	@Qualifier("restTemplateBuilder")
	private RestTemplate restTemplate;
	
	@Value("${cashfree.cg.public.key}")
	private String cashgramPublicKey;

	/**
	 * Cash free Refund Payment
	 * 
	 * @param payload
	 * @return
	 *
	 */
	public RefundPaymentRespone refundPayment(SalesOrder order, PaymentReturnAdditioanls additionals) {
		RefundPaymentRespone response = new RefundPaymentRespone();
		CashfreeDetails cashfreeDetails = cashFreeDetails();
		try {
			AmastyRmaRequest rmaRequest = additionals.getRmaRequest();
			String refundAmount = additionals.getReturnAmount();
			
			if (Objects.isNull(cashfreeDetails)) {
				LOGGER.info("Cashfree Credentials not coming from consul: " + cashfreeDetails);
				throw new IllegalArgumentException("Cashfree details null coming from consul");
			}
			CashfreeRefundDTO payload = cashFreeRefundPayload(order, refundAmount, rmaRequest);
			String url = cashfreeDetails.getCashFreeBaseUrl() + "/orders/" + order.getIncrementId() + "/refunds";
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.add(XCLIENTID, cashfreeDetails.getCashFreeAppId());
			headers.add(CLIENTSECRET, cashfreeDetails.getCashFreeSecret());
			headers.add(APPVERSION, cashfreeDetails.getCashFreeVersion());
			HttpEntity<CashfreeRefundDTO> requestBody = new HttpEntity<>(payload, headers);
			ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.POST, requestBody, String.class);
			LOGGER.info("Cashfree Refund for Order Success, Order ID: " + order.getEntityId() + RESPONSE
					+ exchange.getBody());
			response.setStatus(true);
			response.setStatusCode("200");
			response.setStatusMsg("Refunded Successfully!");
		} catch (Exception e) {
			LOGGER.error("Cashfree Refund for Order Error,  Order ID:" + order.getEntityId() + RESPONSE + e);
			response.setStatus(false);
			response.setStatusCode("205");
			response.setStatusMsg(e.getMessage());
		}
		return response;
	}

	private CashfreeRefundDTO cashFreeRefundPayload(SalesOrder order, String refundAmount, AmastyRmaRequest rmaRequest) {
		CashfreeRefundDTO payLoad = new CashfreeRefundDTO();
		String returnAmount = amountToTwoDecimalPlace(new BigDecimal(refundAmount));
		payLoad.setRefundAmount(returnAmount);
		payLoad.setRefundId(rmaRequest.getRmaIncId());
		SalesOrderStatusHistory salesOrderStatusHistory = order.getSalesOrderStatusHistory().stream().findFirst()
				.orElse(null);
		if (Objects.nonNull(salesOrderStatusHistory)) {
			payLoad.setRefundNote(salesOrderStatusHistory.getStatus());
		}
		return payLoad;
	}

	/**
	 * Retrieve latest payment status
	 * 
	 * @param orderId
	 * @return
	 */
	public String retrievePayment(String orderId) {
		CashfreeDetails cashfreeDetails = cashFreeDetails();
		try {
			String url = cashfreeDetails.getCashFreeBaseUrl() + "/orders/" + orderId ;
			LOGGER.info("cashfree getStatus of  paymnet url: " + url);

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.add(XCLIENTID, cashfreeDetails.getCashFreeAppId());
			headers.add(CLIENTSECRET, cashfreeDetails.getCashFreeSecret());
			headers.add(APPVERSION, cashfreeDetails.getCashFreeVersion());
			HttpEntity<String> requestBody = new HttpEntity<>(null, headers);
			ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.GET, requestBody, String.class);
			LOGGER.info(
					"Cashfree Retrieve Payment Success-checked, Order ID: " + orderId + RESPONSE + exchange.getBody());
			return exchange.getBody();
		} catch (Exception e) {
			LOGGER.error("Cashfree Retrieve Payment Error, Order ID: " + orderId + RESPONSE + e);
		}
		return null;
	}

	private String amountToTwoDecimalPlace(BigDecimal amount) {
		DecimalFormat df = new DecimalFormat("###.##");
		return df.format(amount);
	}

	private CashfreeDetails cashFreeDetails() {
		return Constants.orderCredentials.getCashfree();
	}
	
	/**
	 * Generate token for cashgram using public key
	 * @return
	 */
	public String authorizeCashgram() {
		CashfreeDetails cashfreeDetails = cashFreeDetails();
		try {
			String url = cashfreeDetails.getCashgramBaseUrl() + "/payout/v1/authorize";
			String cfSignature = generateEncryptedSignature();
			LOGGER.info("cashgram authorize url: " + url + " Signature : " + cfSignature);
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.add(XCLIENTID, cashfreeDetails.getCashGramAppId());
			headers.add(CLIENTSECRET, cashfreeDetails.getCashGramSecret());
			headers.add(X_CF_SIGNATURE, cfSignature);
			HttpEntity<String> requestBody = new HttpEntity<>(null, headers);
			ResponseEntity<CashgramResponseDTO> response = restTemplate.exchange(url, HttpMethod.POST, requestBody, CashgramResponseDTO.class);
			LOGGER.info("Cashgram Authorzie token generated : " + RESPONSE + response.getBody());
			if (HttpStatus.OK.equals(response.getStatusCode())) {
				CashgramResponseDTO body = response.getBody();
				if (Objects.nonNull(body) && "200".equals(body.getSubCode())) {
					return body.getData().getToken();
				}else {
					LOGGER.error("Cashgram auth token not generated. Response: " + body);
				}
			}
			return null;
		} catch (Exception e) {
			LOGGER.error("Cashgram authorize Error " + RESPONSE + e);
		}
		return null;
	}
	
	/**
	 * Signature to be generated for cashfree authentication
	 * @return
	 */
	private String generateEncryptedSignature() {
		CashfreeDetails cashfreeDetails = cashFreeDetails();
		String clientIdWithEpochTimestamp = cashfreeDetails.getCashGramAppId() + "." + Instant.now().getEpochSecond();
		String encrytedSignature = "";
		try {
			byte[] keyBytes = Files
					.readAllBytes(new File(cashgramPublicKey).toPath());
			String publicKeyContent = new String(keyBytes);
			publicKeyContent = publicKeyContent.replaceAll("[\\t\\n\\r]", "").replace("-----BEGIN PUBLIC KEY-----", "")
					.replace("-----END PUBLIC KEY-----", "");
			KeyFactory kf = KeyFactory.getInstance("RSA");
			X509EncodedKeySpec keySpecX509 = new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyContent));
			RSAPublicKey pubKey = (RSAPublicKey) kf.generatePublic(keySpecX509);
			final Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
			cipher.init(Cipher.ENCRYPT_MODE, pubKey);
			encrytedSignature = Base64.getEncoder()
					.encodeToString(cipher.doFinal(clientIdWithEpochTimestamp.getBytes()));
		} catch (Exception e) {
			LOGGER.error("Error in generating Cashgram authorize token. Error " + e);
		}
		return encrytedSignature;
	}
	
	/**
	 * Create cashgram 
	 * @param cgToken
	 * @return
	 */
	public CashgramDataDTO createCashgram(SalesOrder order, PaymentReturnAdditioanls addtionals) {
		CashfreeDetails cashfreeDetails = cashFreeDetails();
		CashgramDataDTO cgResponse = new CashgramDataDTO();
		try {
			String token = authorizeCashgram();
			String url = cashfreeDetails.getCashgramBaseUrl() + "/payout/v1/createCashgram";
			CashgramRequestDTO payload = preparePayload(order, addtionals);
			LOGGER.info("create cashgram url: " + url + " Payload: " + payload + " Token : " + token);
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.add(AUTHORIZATION, "Bearer " + token);
			HttpEntity<CashgramRequestDTO> requestBody = new HttpEntity<>(payload, headers);
			ResponseEntity<CashgramResponseDTO> response = restTemplate.exchange(url, HttpMethod.POST, requestBody, CashgramResponseDTO.class);
			LOGGER.info("Create Cashgram success : " + RESPONSE + response.getBody());
			if (HttpStatus.OK.equals(response.getStatusCode())) {
				CashgramResponseDTO body = response.getBody();
				if (Objects.nonNull(body) && "200".equals(body.getSubCode())) {
					cgResponse.setCashgramLink(body.getData().getCashgramLink());
					cgResponse.setExpiry(payload.getLinkExpiry());
					return cgResponse;
				}else {
					LOGGER.error("Unable to create cashgram return. Response : " + body);
				}
			}
			return null;
		} catch (Exception e) {
			LOGGER.error("Create Cashgram Error " + RESPONSE + e);
		}
		return null;
	}

	/**
	 * Prepare payload to create cashgram 
	 * @param order
	 * @param addtionals
	 * @return
	 */
	private CashgramRequestDTO preparePayload(SalesOrder order, PaymentReturnAdditioanls addtionals) {
		Optional<SalesOrderAddress> address = order.getSalesOrderAddress().stream().findFirst();
		CashgramRequestDTO cgRequest = new CashgramRequestDTO();
		cgRequest.setAmount(addtionals.getReturnAmount());
		AmastyRmaRequest rmaRequest = addtionals.getRmaRequest();
		String rmaIncId;
		if (Objects.nonNull(rmaRequest)) {
			rmaIncId = rmaRequest.getRmaIncId();
		} else {
			rmaIncId = order.getIncrementId();
		}
		cgRequest.setCashgramId(rmaIncId);
		cgRequest.setName(order.getCustomerFirstname() + " " + order.getCustomerLastname());
		cgRequest.setRemarks("Styli Refund");
		if (address.isPresent()) {
			SalesOrderAddress adrs = address.get();
			if(Objects.nonNull(adrs.getTelephone())) {
				String[] phone = adrs.getTelephone().split(" ");
				cgRequest.setPhone(phone[1].trim());
			}
			cgRequest.setEmail(adrs.getEmail());
		} else {
			cgRequest.setEmail(order.getCustomerEmail());
		}
		Calendar cal = Calendar.getInstance();
		int days = cashFreeDetails().getCashGramExpireInDays();
		cal.add(Calendar.DAY_OF_MONTH, days);
		SimpleDateFormat sdf= new SimpleDateFormat("yyyy-MM-dd");
		String expiryDate = sdf.format(cal.getTime());
		cgRequest.setLinkExpiry(expiryDate);
		return cgRequest;
	}
	
	/**
	 * Validate if the request came from cashfree with the logic shared by cashfree team
	 * @param payload
	 * @return
	 * @throws NoSuchAlgorithmException
	 * @throws InvalidKeyException
	 */
	public boolean validateSignature(CashgramWebhookDTO payload) {
		try {
			CashfreeDetails cashfreeDetails = cashFreeDetails();
			Map<String, String> cashgramValues = getCashgramValues(payload);
			String concatinatedValue = cashgramValues.values().stream().filter(Objects::nonNull).reduce("",
					(partialString, element) -> partialString + element);
			String secret = cashfreeDetails.getCashGramSecret();
			String message = concatinatedValue;
			Mac sha256HMAC = Mac.getInstance("HmacSHA256");
			SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
			sha256HMAC.init(secretKey);
			String hash = org.apache.commons.codec.binary.Base64
					.encodeBase64String(sha256HMAC.doFinal(message.getBytes()));
			return payload.getSignature().equals(hash);
		} catch (Exception e) {
			LOGGER.error("Error In validating cashgram singature in webhook. Error : " + e);
		}
		return false;
	}

	private Map<String, String> getCashgramValues(CashgramWebhookDTO in) {
		Field[] fields = in.getClass().getDeclaredFields();
		Map<String, String> tmap = new TreeMap<>();
		for (Field field : fields) {
			try {
				String name = field.getName();
				if ("signature".equalsIgnoreCase(name))
					continue;
				PropertyDescriptor pd = new PropertyDescriptor(name, in.getClass());
				Method getter = pd.getReadMethod();
				Object value = getter.invoke(in);
				tmap.put(name, (String) value);
			} catch (Exception e) {
				LOGGER.error("Error In Getting the cashgram. Error : " + e);
			}
		}
		return tmap;
	}
}
