package org.styli.services.order.model.sales;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;

/**
 * Sales Order Product Details
 */
@Getter
@Setter
@Entity
@Table(name = "sales_order_product_details")
public class SalesOrderProductDetails implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id", insertable = false, nullable = false)
    private Integer itemId;

//    @Column(name = "order_id", columnDefinition = "INT", nullable = false)
//    private Integer orderId;

    @Column(name = "store_id", columnDefinition = "SMALLINT")
    private Integer storeId;

    @Column(name = "product_id", columnDefinition = "INT")
    private Integer productId;

    @Column(name = "sku")
    private String sku;

    @Column(name = "name")
    private String name;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "is_return_applicable", columnDefinition = "SMALLINT")
    private Integer isReturnApplicable;

    @Column(name = "size")
    private String size;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "order_id", nullable = false, insertable = true, updatable = false)
    @JsonIgnore
    private SalesOrder salesOrder;

}
