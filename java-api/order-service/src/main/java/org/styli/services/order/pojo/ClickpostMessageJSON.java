package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * @author Umesh, 30/05/2020
 * @project product-service
 */

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClickpostMessageJSON {

    private String type;
    private String value;

}
