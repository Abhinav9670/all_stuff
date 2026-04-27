package org.styli.services.order.pojo.braze;

import lombok.Data;

import java.util.List;

/**
 * @author umesh.mahato@landmarkgroup.com
 * @project order-service
 * @created 24/02/2022 - 10:16 AM
 */

@Data
public class BrazeWalletUpdateEventRequestBody {
  private List<BrazeWalletUpdateEvent> events;
}
