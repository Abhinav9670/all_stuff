 package org.styli.services.order.pojo.request.Order;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class OmsRequestFilters {

	
    private List<Integer> storeId = new ArrayList<>();

    private List<Integer> source = new ArrayList<>();

    private List<String> appVersion = new ArrayList<>();
    
    private String incrementId;
    
    private List<String> status = new ArrayList<>();
    
    private String customerEmail;
    
    private String fromDate;
    
    private String toDate;
    
    private String customerName;

    private String paymentMethod;
    
    private String customerId;

    private Boolean isSplitOrder = null;
}