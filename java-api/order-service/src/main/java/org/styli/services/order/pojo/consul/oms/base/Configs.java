package org.styli.services.order.pojo.consul.oms.base;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created on 31-Oct-2022
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
@NonFinal
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class Configs implements Serializable {

    @JsonIgnore
    private static final long serialVersionUID = 6604275620328414602L;

    @JsonProperty("ksaQR")
    private Boolean ksaQR;

    @JsonProperty("nonKsaQR")
    private Boolean nonKsaQR;

    @JsonProperty("shortPickupEnabled")
    private Boolean shortPickupEnabled;

    @JsonProperty("carrierCodes")
    private List<CarrierCode> carrierCodes;

    @JsonProperty("paymentMethodTranslations")
    private LinkedHashMap<String, TranslationItem> paymentMethodTranslations = new LinkedHashMap<>();

    @JsonProperty("addressChangeFlagMap")
    private LinkedHashMap<String, Boolean> addressChangeFlagMap = new LinkedHashMap<>();

    @JsonProperty("timezone")
    private LinkedHashMap<String, String> timezone = new LinkedHashMap<>();

    @JsonProperty("trackingBaseUrl")
    private String trackingBaseUrl;

    @JsonProperty("apisCheckACL")
    private Boolean apisCheckACL;

    @JsonProperty("javaUpdateRefund")
    private Boolean javaUpdateRefund;


    @JsonIgnore
    public String getTranslationValue(Map<String, TranslationItem> translationItemMap, String key, String lang) {
        return getTranslationValue(translationItemMap, key, lang, "");
    }


    @JsonIgnore
    public String getTranslationValue(
            Map<String, TranslationItem> translationItemMap,  String key, String lang, String fallbackValue) {
        String result = fallbackValue;
        try {
            if(MapUtils.isNotEmpty(translationItemMap) && translationItemMap.get(key) != null) {
                final TranslationItem translationItem = translationItemMap.get(key);
                final String fetchedValue = translationItem.getValueOf(lang);
                if(StringUtils.isNotEmpty(fetchedValue)) {
                    result = fetchedValue;
                }
            }
        } catch (Exception e) {
            result = ((fallbackValue != null && !fallbackValue.equals(result))? fallbackValue :result);
        }
        return result;
    }
}
