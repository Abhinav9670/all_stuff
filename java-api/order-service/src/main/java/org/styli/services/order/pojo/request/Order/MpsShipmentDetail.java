package org.styli.services.order.pojo.request.Order;

import lombok.Data;

import java.util.List;

@Data
public class MpsShipmentDetail {

    private String id;
    private Double weight;
    private Double breadth;
    private Double height;
    private Double length;
    private List<MpsShipmentItem> items;
}
