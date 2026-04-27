package org.styli.services.order.pojo.mulin;

import lombok.Data;

@Data
public class Variant {
    private String sku;
    private String size;
    private SizeLabel sizeLabels;
}
