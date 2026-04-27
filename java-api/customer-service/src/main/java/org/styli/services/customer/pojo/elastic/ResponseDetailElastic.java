package org.styli.services.customer.pojo.elastic;

import com.algolia.search.models.indexing.FacetStats;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Created on 30-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@Data
public class ResponseDetailElastic implements Serializable {

    private static final long serialVersionUID = -7597329078181936470L;
    private List<HitsDetailResponseElastic> hits;
    private Integer nbHits;
    private Integer nbPages;
    private Integer page;
    private Map<String, Map<String, Integer>> facets;
    private Map<String, FacetStats> facets_stats;
    private RuleInfo ruleInfo;
    private Map<String, Map<String, String>> productFilterAttributes;
    private FlashSaleElastic flashSale;
    private Boolean inputObject;
    private Boolean envs;
    private Integer timeExec;
    private Integer took;
    private Integer eTook;
}
