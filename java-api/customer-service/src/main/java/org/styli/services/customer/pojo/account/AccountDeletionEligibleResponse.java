package org.styli.services.customer.pojo.account;

import lombok.Data;
import org.styli.services.customer.pojo.consul.Message;

import java.util.List;

@Data
public class AccountDeletionEligibleResponse {

    private boolean orders;
    private boolean returns;
    private boolean stylicredit;
    private boolean styliCoin;
    private boolean eligible;
    private List<Message> deleteCustomerReasons;

}
