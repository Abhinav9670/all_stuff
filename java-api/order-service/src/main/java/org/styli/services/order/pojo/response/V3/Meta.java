package org.styli.services.order.pojo.response.V3;

import lombok.Data;

@Data
public class Meta {
    private Boolean success;
    private Integer status;
    private Object message;
}
