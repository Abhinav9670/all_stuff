package org.styli.services.order.pojo.whatsapp.bot;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Created on 27-Oct-2022
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
public class MobileOrderListResponse {

    private String orderCount;

    private LinkedHashMap<String, String> idObject;

    private List<String> incrementIds;

    private String idsString;

    private String resultMode;
}
