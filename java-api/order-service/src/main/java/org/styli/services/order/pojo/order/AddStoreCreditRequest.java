package org.styli.services.order.pojo.order;

import lombok.Data;

import java.util.List;


@Data
public class AddStoreCreditRequest {

    private List<StoreCredit> storeCredits;

//    This is used to decide the action type such as "changed from admin" or "blank action"
    private StyliCreditType styliCreditType;

//    This is to handle amount restriction such as bulk updates and oms updates have different restrictions.
//    If not set, no restriction will be imposed.
    private String updateRequestType;

}
