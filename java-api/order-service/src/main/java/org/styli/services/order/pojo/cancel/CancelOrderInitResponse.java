package org.styli.services.order.pojo.cancel;

import lombok.Data;

import java.util.List;

/**
 * @author Umesh, 28/05/2020
 * @project product-service
 */

@Data
public class CancelOrderInitResponse {
    List<Reason> reasons;
}
