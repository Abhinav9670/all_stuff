package org.styli.services.order.pojo.consul.oms.base;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang.StringUtils;

import java.io.Serializable;

/**
 * Created on 31-Oct-2022
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
@NonFinal
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class TranslationItem implements Serializable {

    @JsonIgnore
    private static final long serialVersionUID = 343385949201564898L;

    @JsonProperty("en")
    private String en;

    @JsonProperty("ar")
    private String ar;

    public String getValueOf(String lang) {
        if("ar".equalsIgnoreCase(lang)) {
            return ar;
        }
        return en;
    }

}
