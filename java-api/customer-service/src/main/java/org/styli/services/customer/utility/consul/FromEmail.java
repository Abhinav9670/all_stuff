package org.styli.services.customer.utility.consul;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;

import java.util.LinkedHashMap;

/**
 * Created on 26-Jul-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@Data
@AllArgsConstructor(staticName = "of")
public class FromEmail {

  private String mail;
  private LinkedHashMap<String, String> name;

  public FromEmail() {
	  //empty constructor
  }

  public String getName(String langCode) {
    String result = "";
    if (MapUtils.isNotEmpty(name)
        && StringUtils.isNotEmpty(langCode)
        && StringUtils.isNotEmpty(name.get(langCode))) {
      result = name.get(langCode);
    }
    return result;
  }
}
