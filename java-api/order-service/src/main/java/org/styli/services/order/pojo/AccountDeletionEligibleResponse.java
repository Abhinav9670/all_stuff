package org.styli.services.order.pojo;

import java.util.List;

import org.styli.services.order.utility.consulValues.Message;

import lombok.Data;

@Data
public class AccountDeletionEligibleResponse {

    private boolean orders;
    private boolean returns;
    private boolean stylicredit;
    private boolean styliCoin;
    private boolean eligible;
    private List<Message> deleteCustomerReasons;

}
