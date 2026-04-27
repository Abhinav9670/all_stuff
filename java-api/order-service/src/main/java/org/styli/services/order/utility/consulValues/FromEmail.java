package org.styli.services.order.utility.consulValues;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;

import java.util.LinkedHashMap;

/**
 * @author Biswabhusan Pradhan <biswabhusan.pradhan@landmarkgroup.com>
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
