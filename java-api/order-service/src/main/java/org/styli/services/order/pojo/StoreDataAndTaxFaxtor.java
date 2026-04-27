package org.styli.services.order.pojo;

import lombok.Data;
import org.styli.services.order.db.product.pojo.Stores;

import java.math.BigDecimal;

@Data
public class StoreDataAndTaxFaxtor {
    public Stores store;
    public BigDecimal taxFactor;
}
