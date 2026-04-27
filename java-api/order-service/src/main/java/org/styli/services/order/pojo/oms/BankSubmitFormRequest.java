package org.styli.services.order.pojo.oms;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * @author umesh.mahato@landmarkgroup.com
 * @project order-service
 * @created 05/01/2022 - 4:12 PM
 */

@Data
public class BankSubmitFormRequest {

    @NotEmpty
    private String iban;

    @NotNull
    @Min(1)
    private BigDecimal amount;

    @NotEmpty
    private String name;

    private String email;

    private String phoneNumber;

    @NotNull
    private Integer customerId;

    @NotNull
    private Integer storeId;

    @NotEmpty
    private String bankName;

    @NotEmpty
    private String swiftCode;

}
