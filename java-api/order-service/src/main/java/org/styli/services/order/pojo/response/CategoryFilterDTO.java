package org.styli.services.order.pojo.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import org.styli.services.order.pojo.KeyValuePair;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Created on 16-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Setter
@Getter
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CategoryFilterDTO implements Serializable {

    private Integer totalCount;

    private Map<String, List<KeyValuePair>> filters;

    private List<Integer> totalProductIds;

}
