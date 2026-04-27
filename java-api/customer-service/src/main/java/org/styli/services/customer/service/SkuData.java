package org.styli.services.customer.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Configuration;

import java.util.Date;
import java.util.List;

@Data

public class SkuData {
    private String external_id;
    private String time;
    private String name;
    private PropertiesData properties;
}