package org.styli.services.order.pojo.order;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Created on 24-May-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */


@Data
public class StoreCredit {

    //@NotNull
    //@Min(1)
    private Integer customerId;

    private String emailId;


    @NotNull
    private BigDecimal storeCredit;

    @NotNull
    @Min(1)
    private Integer storeId;

    private String referenceNumber;

    private String comment;

    private String store;

    private String initiatedBy;

    private String initiatedTime;

    private String jobId;

    private boolean returnableToBank;

}
