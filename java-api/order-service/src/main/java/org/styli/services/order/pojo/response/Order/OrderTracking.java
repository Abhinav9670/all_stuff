package org.styli.services.order.pojo.response.Order;

import lombok.Data;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class OrderTracking {
    public String track_number;
    public String carrier_code;
    public String title;
}
