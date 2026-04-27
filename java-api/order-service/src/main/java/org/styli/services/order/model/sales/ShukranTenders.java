package org.styli.services.order.model.sales;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Date;

@Data
public class ShukranTenders {
     @JsonProperty("TenderCode")
     private String tenderCode;
     @JsonProperty("TenderAmount")
     private BigDecimal tenderAmount;
     @JsonProperty("TransactionDateTime")
     private String transactionDateTime;
}
