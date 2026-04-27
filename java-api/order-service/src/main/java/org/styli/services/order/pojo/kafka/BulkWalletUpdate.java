package org.styli.services.order.pojo.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;

@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class BulkWalletUpdate implements Serializable {

    private static final long serialVersionUID = 1L;


//    @JsonProperty("email_id")
//    private String emailId;

    private String store;

    private Integer store_id;

    private BigDecimal amount_to_be_refunded;

    private String order_no;

    private String comment;

    private String email;

    private Integer customerId;

    private Integer counter;

    private Integer totalCount;

    private String initiatedBy;

    private String initiatedTime;

    private String jobId;

    @JsonProperty("returnable_to_bank")
    private boolean returnableToBank;

}
