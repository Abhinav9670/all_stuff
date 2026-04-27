package org.styli.services.order.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.styli.services.order.db.product.exception.NotFoundException;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.pojo.QuoteDTO;
import org.styli.services.order.pojo.response.Customer;
import org.styli.services.order.pojo.response.CustomerStoreCreditResponse;
import org.styli.services.order.pojo.response.CustomerUpdateProfileResponse;

@Service
public interface CustomerService {
    CustomerUpdateProfileResponse getCustomerDetails(Integer customerId, Map<String, String> requestHeader);

    
    void deductStoreCreditV2(QuoteDTO quote, SalesOrder order, Stores store, BigDecimal amastyBaseStoreBalance) throws NotFoundException;


    CustomerStoreCreditResponse getCustomerStoreCredit(Integer customerId);
    
    public List<Customer> findReferralCustomers(List<Integer> customerIds);

}
