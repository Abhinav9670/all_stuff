package org.styli.services.order.pojo.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusMessage {
    private String statusId;
    private String message;
    private String timestamp;
}