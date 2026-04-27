package org.styli.services.order.pojo.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Getter
@AllArgsConstructor
public enum OrderStatusENUM {

    SUCCESS("success"), FAILED("failed"), CANCELED("canceled");

    public String value;

}
