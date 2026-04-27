package org.styli.services.order.pojo;

import lombok.Data;

import java.io.Serializable;

@Data
public class Province implements Serializable {
    private static final long serialVersionUID = 1L;
    String id;
    String name;
    String code;
    String name_ar;
}
