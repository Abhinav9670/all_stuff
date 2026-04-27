package org.styli.services.order.pojo.response.V3;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TaxItem {
    public String type;
    public Integer rate;
    public BigDecimal taxPerUnit;
    public BigDecimal taxTotal;
}
