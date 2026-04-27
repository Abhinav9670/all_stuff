package org.styli.services.customer.pojo.epsilon.request;

import lombok.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ShukranEnrollmentRequest {
    @NotNull(message = "Customer Id should not be null or empty")
    private Integer customerId;
    @NotNull(message = "Store Id should not be null or empty")
    private Integer storeId;
    private String customerEmail;
}

