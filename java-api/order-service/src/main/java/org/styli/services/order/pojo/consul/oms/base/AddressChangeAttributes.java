package org.styli.services.order.pojo.consul.oms.base;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import javassist.SerialVersionUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * @author Biswabhusan Pradhan <biswabhusan.pradhan@landmarkgroup.com>
 */
@Data
@NonFinal
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class AddressChangeAttributes implements Serializable {

    @JsonIgnore
    @Serial
    private static final long serialVersionUID = 6604275620328414602L;

    @JsonProperty("flag")
    private Boolean flag;

    @JsonProperty("addressChangeLimit")
    private Integer addressChangeLimit;

    @JsonProperty("terminalStatus")
    private String terminalStatus;

    @JsonProperty("serviceableLogisticPartner")
    private List<String> serviceableLogisticPartner;
}
