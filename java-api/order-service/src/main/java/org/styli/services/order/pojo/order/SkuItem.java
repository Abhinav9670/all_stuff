package org.styli.services.order.pojo.order;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkuItem {
    private String sku;
    private String warehosueId;
    private String shipementMode;
    private String sellerId;
    private String sellername;
    private List<StatusMessage> statusMessage;
}