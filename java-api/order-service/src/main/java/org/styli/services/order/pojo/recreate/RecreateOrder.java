package org.styli.services.order.pojo.recreate;

import lombok.Data;
import org.styli.services.order.pojo.request.PaymentCodeENUM;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class RecreateOrder {

    @NotNull @Min(1)
    private Integer orderId;

    private Integer splitOrderId;

    @NotEmpty
    private List<Integer> requestedItems;

//    Do not ask me why I created a redundant key.
//    private Map<Integer, Integer> requestedItemsQty;
    private Map<@NotNull @Min(1) Integer, @NotNull @Min(1) Integer> requestedItemsQty;

    @NotNull
    private PaymentCodeENUM paymentMethod;

    private BigDecimal storeCreditApplied;

    private Boolean isSubmit = false;

    private Boolean isSplitOrder = false;
}
