package org.styli.services.order.pojo.request.GetShipmentV3;

import java.util.List;

import lombok.Data;

@Data
public class ShipmentDetails {
    private String weight;
    private String breadth;
    private String height;
    private String length;
    private String invoice_value;
    private String cod_value;
    private String order_type;
    private String invoice_number;
    private String invoice_date;
    private String reference_number;
    private String currency_code;
    private List<Item> items;
    private List<QcChecks> qc;
}
