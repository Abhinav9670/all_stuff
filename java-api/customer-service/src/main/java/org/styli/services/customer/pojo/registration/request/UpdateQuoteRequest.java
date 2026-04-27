package org.styli.services.customer.pojo.registration.request;

import lombok.*;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class UpdateQuoteRequest {
        @NotNull
        private Integer customerId;
        @NotNull
        @Min(1)
        private Integer storeId;
        private String phoneNumber;
}
