package org.styli.services.order.utility.consulValues;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeleteCustomer {

	@JsonProperty("reasons")
    private List<Message> deleteCustomerReasons;

    @JsonProperty("orderTerminalStatus")
    private List<String> terminalStatus;

    @JsonProperty("rmaTerminalStatus")
    private List<String> refundStatus;

}