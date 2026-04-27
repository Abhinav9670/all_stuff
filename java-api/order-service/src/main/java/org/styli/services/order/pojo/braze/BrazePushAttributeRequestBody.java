package org.styli.services.order.pojo.braze;

import lombok.Data;

import java.util.List;

/**
 * @author umesh.mahato@landmarkgroup.com
 * @project order-service
 * @created 28/02/2022 - 10:53 AM
 */

@Data
public class BrazePushAttributeRequestBody {

    private List<BrazePushAttribute> attributes;
}
