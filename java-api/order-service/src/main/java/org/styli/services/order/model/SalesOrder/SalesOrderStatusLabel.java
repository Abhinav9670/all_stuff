package org.styli.services.order.model.SalesOrder;

import java.io.Serializable;
import javax.persistence.*;

import lombok.Data;

//@Embeddable
//class SalesOrderStatusLabelPK implements Serializable {
//
//  @Column(name = "status", insertable = false, nullable = false)
//  private String status;
//
//  @Column(name = "store_id", insertable = false, nullable = false, columnDefinition = "SMALLINT")
//  private Integer storeId;
//
//}

/**
 * Sales Order Status Label Table
 */
@Entity
@Data
@Table(name = "sales_order_status_label")
public class SalesOrderStatusLabel implements Serializable {
  private static final long serialVersionUID = 1L;

  @EmbeddedId
  private SalesOrderStatusLabelPK id;

  @Column(name = "label", nullable = false)
  private String label;

}