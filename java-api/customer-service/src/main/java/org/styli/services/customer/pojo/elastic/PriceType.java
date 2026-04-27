package org.styli.services.customer.pojo.elastic;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * Created on 30-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@Data
public class PriceType implements Serializable {

    private static final long serialVersionUID = -1658625608335691310L;
    @JsonProperty("default")
    private double defaultPrice;

    @JsonProperty("default_original_formated")
    private String defaultOriginalFormatted;
}
