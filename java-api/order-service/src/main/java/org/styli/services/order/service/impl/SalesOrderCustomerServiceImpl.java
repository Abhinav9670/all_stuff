package org.styli.services.order.service.impl;

import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.model.Customer.CustomerEntity;
import org.styli.services.order.model.rma.AmastyRmaRequest;
import org.styli.services.order.model.rma.AmastyStoreCredit;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderGrid;
import org.styli.services.order.pojo.AccountDeletionEligibleRequest;
import org.styli.services.order.pojo.AccountDeletionEligibleResponse;
import org.styli.services.order.repository.Customer.CustomerEntityRepository;
import org.styli.services.order.repository.Rma.AmastyRmaRequestRepository;
import org.styli.services.order.repository.Rma.AmastyRmaStatusRepository;
import org.styli.services.order.repository.Rma.AmastyStoreCreditRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.service.SalesOrderCustomerService;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.consulValues.ConsulValues;
import org.styli.services.order.utility.consulValues.DeleteCustomer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class SalesOrderCustomerServiceImpl implements SalesOrderCustomerService {
	
	 private static final Log LOGGER = LogFactory.getLog(SalesOrderCustomerServiceImpl.class);
	 
	 @Autowired
	 SalesOrderRepository salesOrderRepository;
	 
	 @Autowired
	 SalesOrderGridRepository salesOrderGridRepository;
	 
	 @Autowired
	 CustomerEntityRepository customerEntityRepository;
	 
	@Autowired
	AmastyRmaRequestRepository amastyRmaRequestRepository;
	
	@Autowired
	AmastyRmaStatusRepository amastyRmaStatusRepository;

	@Autowired
	AmastyStoreCreditRepository amastyStoreCreditRepository;
	
	@Autowired
	OrderHelper orderHelper;
	
	private static final ObjectMapper mapper = new ObjectMapper();

	@Override
	public void updateSalesOrdersCustomerId(Integer hoursAgo) {
		
		try {
            List<SalesOrder> salesOrderList = salesOrderRepository.getAllOrdersForCustIdUpdate(hoursAgo);
            List<SalesOrderGrid> salesOrderGridList = salesOrderGridRepository.getAllOrdersForCustIdUpdate(hoursAgo);

            if (CollectionUtils.isNotEmpty(salesOrderList)) {

                for (final SalesOrder salesOrder : salesOrderList) {
                	
                	CustomerEntity custEntity = orderHelper.getCustomerDetails(null, salesOrder.getCustomerEmail());
                	
                	if(null != custEntity) {
                		salesOrder.setCustomerId(custEntity.getEntityId());
                        salesOrder.setCustomerIsGuest(0);
                        salesOrder.setCustomerFirstname(custEntity.getFirstName());
                        salesOrder.setCustomerLastname(custEntity.getLastName());
                        salesOrder.setCustomerGroupId(1);
                	}
                    salesOrderRepository.saveAndFlush(salesOrder);
                }
            }
            if (CollectionUtils.isNotEmpty(salesOrderGridList)) {

                for (final SalesOrderGrid salesOrderGrid : salesOrderGridList) {
                	
                	CustomerEntity custEntity = orderHelper.getCustomerDetails(null,salesOrderGrid.getCustomerEmail());
                	
                	if(null != custEntity) {
                		salesOrderGrid.setCustomerId(custEntity.getEntityId());
                        salesOrderGrid.setCustomerName(
                        		custEntity.getFirstName() + " " + custEntity.getLastName());
                        salesOrderGrid.setCustomerGroup("1");
                	}
                    salesOrderGridRepository.saveAndFlush(salesOrderGrid);
                }
            }
        } catch(Exception e) {
            LOGGER.error("Exception occurred:" + e.getMessage());
        }
	}
	
	@Override
	public AccountDeletionEligibleResponse checkAccountDeletionEligiblity(AccountDeletionEligibleRequest request) {

		AccountDeletionEligibleResponse response = new AccountDeletionEligibleResponse();
		ConsulValues orderConsulValues = new ConsulValues();
		String msgValue = Constants.getOrderConsulValues().get("orderConsulKeys");
		try {
			orderConsulValues = mapper.readValue(msgValue, ConsulValues.class);
			DeleteCustomer deleteCustomer = orderConsulValues.getDeleteCustomer();

			if (null != deleteCustomer) {
				Integer orderCount = salesOrderRepository.countPendingOrdersByCustomerId(request.getCustomerId(),
						deleteCustomer.getTerminalStatus());
				response.setOrders(orderCount > 0);
				
				List<Integer> returnReqStatus = amastyRmaStatusRepository.findByStatusCode(deleteCustomer.getRefundStatus());

				List<AmastyRmaRequest> rmaRequests = amastyRmaRequestRepository.findByCustomerIdAndStatusNotIn(
						request.getCustomerId(), returnReqStatus
				);
				response.setReturns(rmaRequests.size() > 0);

				List<AmastyStoreCredit> storeCredits = amastyStoreCreditRepository
						.findByCustomerId(request.getCustomerId());

				if (ObjectUtils.isNotEmpty(storeCredits) && ObjectUtils.isNotEmpty(storeCredits.get(0))
						&& ObjectUtils.isNotEmpty(storeCredits.get(0).getReturnableAmount())) {

					response.setStylicredit(storeCredits.get(0).getReturnableAmount().intValue() > 0);
				}

				response.setDeleteCustomerReasons(deleteCustomer.getDeleteCustomerReasons());
			}

		} catch (JsonProcessingException e1) {
			LOGGER.error("error in parse");
		} catch (Exception e) {
			LOGGER.error("Exception occurred in account deletion eligible " + e.getMessage());
		}

		return response;
	}
}
