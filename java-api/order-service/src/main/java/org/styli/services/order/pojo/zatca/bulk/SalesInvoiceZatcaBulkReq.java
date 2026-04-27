package org.styli.services.order.pojo.zatca.bulk;

import java.sql.Timestamp;

import lombok.Data;

@Data
public class SalesInvoiceZatcaBulkReq {

	private String incrementId;
    
    private Timestamp createdAt;
}
