package org.styli.services.order.pojo.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class BulkWalletUpdateAllString implements Serializable {

    private static final long serialVersionUID = 1L;


//    @JsonProperty("email_id")
//    private String emailId;

    private String store;

    private String store_id;

    private String amount_to_be_refunded;

    private String order_no;

    private String comment;

    private String email;

    private Integer customerId;

    private String counter;

    private String totalCount;

    private String initiatedBy;

    private String exceptionMessage;

    private String initiatedTime;

    private String jobId;

}
