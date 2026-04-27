package org.styli.services.customer.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Data

public class BrazeWishlistEventData {


    private List<SkuData> events;

}