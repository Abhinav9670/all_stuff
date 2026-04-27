package org.styli.services.order.pojo.response.Order;

import lombok.Builder;
import lombok.Data;
import java.sql.Timestamp;
import java.util.List;

@Data
@Builder
public class IncrementIdTrackingResponse {
    private String incrementId;
    private String orderType; // "GLOBAL" or "LOCAL"
    private List<StatusHistoryItem> statusHistory; // List of statuses in chronological order
    
    @Data
    @Builder
    public static class StatusHistoryItem {
        private String status;
        private String note;
        private Timestamp timestamp;
    }
}

