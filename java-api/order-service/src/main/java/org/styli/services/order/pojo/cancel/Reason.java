package org.styli.services.order.pojo.cancel;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author Umesh, 28/05/2020
 * @project product-service
 */

@Data
@AllArgsConstructor
public class Reason {

    private String reasonId;
    private String reason;

}
