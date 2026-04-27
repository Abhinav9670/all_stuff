package org.styli.services.customer.pojo.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
public class ProvinceResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private Boolean status;
    private Integer statusCode;
    private String statusMsg;
    private List<Province> response;
}
