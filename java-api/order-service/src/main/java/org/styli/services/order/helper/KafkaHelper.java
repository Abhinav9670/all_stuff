package org.styli.services.order.helper;

import java.util.*;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.model.SalesOrder.SalesCreditmemoGrid;
import org.styli.services.order.model.rma.AmastyRmaRequest;
import org.styli.services.order.model.sales.ProxyOrder;
import org.styli.services.order.model.sales.RtoAutoRefund;
import org.styli.services.order.model.sales.SalesInvoiceGrid;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderAddress;
import org.styli.services.order.model.sales.SalesOrderGrid;
import org.styli.services.order.pojo.kafka.DeleteCustomerKafka;
import org.styli.services.order.pojo.kafka.DeleteCustomerStatusUpdateRequest;
import org.styli.services.order.repository.Rma.AmastyRmaRequestRepository;
import org.styli.services.order.repository.SalesOrder.ProxyOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesInvoiceGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderAddressRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.VaultPaymentTokenRepository;
import org.styli.services.order.utility.Constants;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class KafkaHelper {
	
	private static final Log LOGGER = LogFactory.getLog(KafkaHelper.class);
	
	private static final String DELETED_USER = "deleted_user_";
	private static final String STYLISHOP_COM = "@stylishop.com";
	
	@Autowired
	private SalesOrderRepository salesOrderRepository;
	
	@Autowired
	private SalesOrderGridRepository salesOrderGridRepository;
	
	@Autowired
	private SalesOrderAddressRepository salesOrderAddressRepository;
	
	@Autowired
	private SalesInvoiceGridRepository salesInvoiceGridRepository;
	
	@Autowired
	private SalesCreditmemoGridRepository salesCreditmemoGridRepository;
	
	@Autowired
	ProxyOrderRepository proxyOrderRepository;
	
	@Autowired
	AmastyRmaRequestRepository amastyRmaRequestRepository;
	
	
	@Autowired
	VaultPaymentTokenRepository vaultPaymentTokenRepository;

	@Autowired
    @Qualifier("withoutEureka")
    private RestTemplate restTemplate;
	 
	 @Value("${auth.internal.header.bearer.token}")
	 private String internalAuthBearerToken;
	 
	 private static final ObjectMapper mapper = new ObjectMapper();
	
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void processKafkaDeleteCustomer(DeleteCustomerKafka deleteCustomerKafka) {

		boolean status = true;
		
		try {
			if(Objects.nonNull(deleteCustomerKafka)) {
				LOGGER.info("Processing kafka messages on Delete Customer of CustomerId: " + deleteCustomerKafka.getCustomerId());
				
				deleteCustomerInSalesOrder(deleteCustomerKafka);
				deleteCustomerInSalesOrderAddress(deleteCustomerKafka);
				deleteCustomerInSalesOrderGrid(deleteCustomerKafka);
				deleteCustomerInProxyOrder(deleteCustomerKafka);
				deleteCustomerInAmastyRmaRequest(deleteCustomerKafka);
				deleteCustomerSavedCard(deleteCustomerKafka);
				
			}
		} catch(Exception e) {
			status = false;
			LOGGER.error("Error while processing delete customer kafka " + e);
		}

		if(Objects.nonNull(deleteCustomerKafka))
			updateCustomerStatus(deleteCustomerKafka, status);


	}

	private void deleteCustomerSavedCard(DeleteCustomerKafka deleteCustomerKafka) {
		
		 vaultPaymentTokenRepository.deleteByCustomerId(deleteCustomerKafka.getCustomerId());
		 
		 LOGGER.info("delete saved card done:");
	}

	private void updateCustomerStatus(DeleteCustomerKafka deleteCustomerKafka, boolean status) {
		try {
			HttpHeaders requestHeaders = new HttpHeaders();
			requestHeaders.setContentType(MediaType.APPLICATION_JSON);
			requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
			requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
			
			if (null != internalAuthBearerToken && internalAuthBearerToken.contains(",")) {
				List<String> authTokenList = Arrays.asList(internalAuthBearerToken.split(","));
				if (CollectionUtils.isNotEmpty(authTokenList) && !authTokenList.isEmpty()) {
					requestHeaders.add("authorization-token", authTokenList.get(0));
				}
			}
			
			DeleteCustomerStatusUpdateRequest request = new DeleteCustomerStatusUpdateRequest();
			request.setCustomerId(deleteCustomerKafka.getCustomerId());
			request.setStatus(status);
			request.setTask("oms");

			HttpEntity<DeleteCustomerStatusUpdateRequest> requestBody = new HttpEntity<>(request, requestHeaders);
			String url =  Constants.orderCredentials.getOrderDetails().getCustomerServiceBaseUrl()
					+ "/rest/customer/delete/status/update";

			LOGGER.info("delete customer status update request URL:" + url);
			LOGGER.info("delete customer status update request body:" + mapper.writeValueAsString(requestBody.getBody()));
			restTemplate.exchange(url, HttpMethod.POST, requestBody, Object.class);
			
		} catch (JsonProcessingException e1) {
			LOGGER.error("error in parse delete customer status update ");
		} catch (RestClientException e) {

			LOGGER.error("Exception occurred  during REST call delete customer status update:" + e.getMessage());

		} catch (Exception e) {
			LOGGER.error("Exception occurred in delete customer status update " + e.getMessage());
		}
	}

	private void deleteCustomerInAmastyRmaRequest(DeleteCustomerKafka deleteCustomerKafka) {
		List<AmastyRmaRequest> rmaRequests = amastyRmaRequestRepository.findByCustomerId(deleteCustomerKafka.getCustomerId());
		
		if (Objects.nonNull(rmaRequests)) {
			for (AmastyRmaRequest rmaRequest : rmaRequests) {
				if (Objects.nonNull(rmaRequest)) {
					rmaRequest.setCustomerName("");
				}
			}
			amastyRmaRequestRepository.saveAll(rmaRequests);
			amastyRmaRequestRepository.flush();
		}
	}

	private void deleteCustomerInProxyOrder(DeleteCustomerKafka deleteCustomerKafka) {
		List<ProxyOrder> proxyOrders = proxyOrderRepository.findByCustomerId(deleteCustomerKafka.getCustomerId());
		String email = DELETED_USER + deleteCustomerKafka.getCustomerId() + STYLISHOP_COM;
		
		if (Objects.nonNull(proxyOrders)) {
			for (ProxyOrder proxyOrder : proxyOrders) {
				if (Objects.nonNull(proxyOrder)) {
					proxyOrder.setEmail(email);
				}
			}
			proxyOrderRepository.saveAll(proxyOrders);
			proxyOrderRepository.flush();		}
		
	}

	private void deleteCustomerInSalesOrderGrid(DeleteCustomerKafka deleteCustomerKafka) {
		List<SalesOrderGrid> ordersGrid = salesOrderGridRepository.findByCustomerId(deleteCustomerKafka.getCustomerId());
		String email = DELETED_USER + deleteCustomerKafka.getCustomerId() + STYLISHOP_COM;
		
		if (Objects.nonNull(ordersGrid)) {
			for (SalesOrderGrid orderGrid : ordersGrid) {
				if (Objects.nonNull(orderGrid)) {
					orderGrid.setCustomerEmail(email);
					orderGrid.setCustomerName(null);
					orderGrid.setBillingAddress(null);
					orderGrid.setBillingName(null);
					orderGrid.setShippingInformation(null);
					orderGrid.setShippingName(null);
					orderGrid.setShippingAddress(null);
				}
			}
			salesOrderGridRepository.saveAll(ordersGrid);
			salesOrderGridRepository.flush();
		}
	}

	private void deleteCustomerInSalesOrderAddress(DeleteCustomerKafka deleteCustomerKafka) {

		List<SalesOrderAddress> salesOrderAddressList = salesOrderAddressRepository
				.findByCustomerId(deleteCustomerKafka.getCustomerId());

		if (CollectionUtils.isNotEmpty(salesOrderAddressList)) {

			for (SalesOrderAddress orderAddress : salesOrderAddressList) {

				orderAddress.setTelephone(null);
				orderAddress.setEmail(null);
				orderAddress.setFirstname(null);
				orderAddress.setLastname(null);
				orderAddress.setPostcode(null);
				orderAddress.setStreet(null);
				orderAddress.setNearestLandmark(null);
                orderAddress.setUnitNumber(null);
                orderAddress.setKsaAddressComplaint(null);
                orderAddress.setShortAddress(null);
                orderAddress.setPostalCode(null);
				salesOrderAddressRepository.saveAndFlush(orderAddress);
			}
		}
	}

	private void deleteCustomerInSalesOrder(DeleteCustomerKafka deleteCustomerKafka) {
		List<SalesOrder> orders = salesOrderRepository.findByCustomerId(deleteCustomerKafka.getCustomerId());
		String email = DELETED_USER + deleteCustomerKafka.getCustomerId() + STYLISHOP_COM;
		if(Objects.nonNull(orders)) {
			for(SalesOrder order: orders) {
				if(Objects.nonNull(order)) {
					order.setCustomerSuffix(null);
					order.setCustomerDob(null);
					order.setCustomerEmail(email);
					order.setCustomerFirstname(null);
					order.setCustomerGender(null);
					order.setCustomerGroupId(null);
					order.setCustomerLastname(null);
					order.setCustomerMiddlename(null);
					order.setCustomerPrefix(null);
					
					SalesInvoiceGrid salesInvoice = salesInvoiceGridRepository.findByOrderId(order.getEntityId());
					if(Objects.nonNull(salesInvoice)) {
						salesInvoice.setCustomerEmail(email);
						salesInvoice.setCustomerName(null);
						salesInvoice.setBillingAddress(null);
						salesInvoice.setBillingName(null);
						salesInvoice.setShippingAddress(null);
						salesInvoice.setShippingInformation(null);
						salesInvoiceGridRepository.saveAndFlush(salesInvoice);
					}
					
					List<SalesCreditmemoGrid> salesCreditmemoGridList = salesCreditmemoGridRepository.findByOrderId(order.getEntityId());
					
					if (Objects.nonNull(salesCreditmemoGridList)) {
						for (SalesCreditmemoGrid salesCreditmemoGrid : salesCreditmemoGridList) {
							if (Objects.nonNull(salesCreditmemoGrid)) {
								salesCreditmemoGrid.setCustomerEmail(email);
								salesCreditmemoGrid.setCustomerName("");
								salesCreditmemoGrid.setBillingAddress(null);
								salesCreditmemoGrid.setBillingName(null);
								salesCreditmemoGrid.setShippingAddress(null);
								salesCreditmemoGrid.setShippingInformation(null);
							}
						}
						salesCreditmemoGridRepository.saveAll(salesCreditmemoGridList);
						salesCreditmemoGridRepository.flush();					}
					
					if (Objects.nonNull(order.getRtoAutoRefund())) {
						List<RtoAutoRefund> rtoAutoRefund = order.getRtoAutoRefund().stream().toList();
						if (Objects.nonNull(rtoAutoRefund)) {
							rtoAutoRefund.forEach(rto -> rto.setCustomerEmail(email));
						}
					}
				}
			}
			salesOrderRepository.saveAll(orders);
			salesOrderRepository.flush();
		}
	}

}
