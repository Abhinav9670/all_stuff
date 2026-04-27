package org.styli.services.customer.pojo.registration.request;

import lombok.*;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class GetQuoteRequest {
        @NotNull
        private Integer customerId;
        @NotNull
        @Min(1)
        private Integer storeId;
        @NotNull
        private Integer bagView;
}
