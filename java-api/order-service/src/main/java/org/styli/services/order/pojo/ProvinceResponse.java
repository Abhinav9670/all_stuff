package org.styli.services.order.pojo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ProvinceResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Boolean status;
    private Integer statusCode;
    private String statusMsg;
    private List<Province> response;
}
