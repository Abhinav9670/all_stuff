package org.styli.services.order.pojo.response.V3;

import java.util.List;

import lombok.Data;

@Data
public class GetInvoiceV3ResponseBody {
    private String invoiceCode;
    private String invoiceUrl;
    private String invoiceDate;
    private List<InvoiceDetail> invoiceDetails;  
    private Boolean hasError;
    private String errorMessage;
}
