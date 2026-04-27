package org.styli.services.order.pojo.response;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * @author Biswabhusan Pradhan <biswabhusan.pradhan@landmarkgroup.com>
 */
@Data
public class NavikAddressUpdateResponse implements Serializable {

    Boolean status;

    Map<String, String> data;
}
