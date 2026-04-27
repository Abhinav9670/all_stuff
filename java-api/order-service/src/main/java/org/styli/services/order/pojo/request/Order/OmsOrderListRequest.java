package org.styli.services.order.pojo.request.Order;

import lombok.Data;

@Data
public class OmsOrderListRequest {


    
    private OmsRequestFilters filters;
    	
    private int offset;
    
    private int pageSize;
    
    private String query;

    private boolean useArchive;
    
}