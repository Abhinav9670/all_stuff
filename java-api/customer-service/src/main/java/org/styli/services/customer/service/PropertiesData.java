package org.styli.services.customer.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Configuration;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Configuration
public class PropertiesData {
    private String product_name;
    private String image;
    private String user_id;
    private double drop_percent;
    private double priceDiff;
    private double current_price;
    private double old_price;
    private String storeCurrency;
}