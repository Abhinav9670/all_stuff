package org.styli.services.order.pojo.response;

import lombok.Data;

@Data
public class Meta {
    private boolean status;
    private int code;
    private String message;
    private Object details;
}

