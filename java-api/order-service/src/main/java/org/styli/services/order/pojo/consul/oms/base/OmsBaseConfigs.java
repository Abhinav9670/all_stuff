package org.styli.services.order.pojo.consul.oms.base;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;


/**
 * Created on 31-Oct-2022
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
@NonFinal
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class OmsBaseConfigs implements Serializable {
    /**
     * POJO for Consul values from key "oms/base_<ENV>"
     */

    @JsonIgnore
    private static final long serialVersionUID = 3046453383039107476L;


    @JsonProperty("configs")
    private Configs configs;

    @JsonProperty("whatsappOrderConfig")
    private WhatsappOrderConfig whatsappOrderConfig;

}
