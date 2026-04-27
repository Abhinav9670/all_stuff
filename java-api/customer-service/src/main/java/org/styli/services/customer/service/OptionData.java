package org.styli.services.customer.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OptionData {
    private Long skuId;
    private double discount_SAR;
    private double discount_AED;
    private double discount_BHD;
    private double discount_KWD;
}