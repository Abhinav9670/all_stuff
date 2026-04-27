package org.styli.services.order.repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.styli.services.order.db.product.exception.NotFoundException;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.model.Store;
import org.styli.services.order.model.Eav.EavAttribute;
import org.styli.services.order.model.SalesOrder.SalesOrderStatusLabel;
import org.styli.services.order.model.SalesOrder.SalesOrderStatusState;
import org.styli.services.order.model.SalesOrder.SalesOrderStatusStatePK;
import org.styli.services.order.pojo.AttributeValue;
import org.styli.services.order.repository.Eav.EavAttributeRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusLabelRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusStateRepository;
import org.styli.services.order.service.CoreConfigDataService;
import org.styli.services.order.utility.Constants;

import com.algolia.search.DefaultSearchClient;
import com.algolia.search.SearchClient;

@Component
public class StaticComponents {

	private static final Log LOGGER = LogFactory.getLog(StaticComponents.class);
	@Autowired
	EavAttributeRepository eavAttributeRepository;

	@Autowired
	StoreRepository storeRepository;

	@Autowired
	CoreConfigDataService coreConfigDataService;

	@Autowired
	SalesOrderStatusStateRepository salesOrderStatusStateRepository;

	@Autowired
	SalesOrderStatusLabelRepository salesOrderStatusLabelRepository;

	Map<String, Integer> statusStepMap;
	Map<String, Integer> statusColorStepMap;

	Map<Integer, String> attrMap;


	Map<String, String> labelMap;
	Map<String, AttributeValue> attrStatusMap;

	private Integer priceAttrId;
	private Integer specialPriceAttrId;

	@Value("${algolia.api.key}")
	private String algoliaApiKey;

	@Value("${algolia.app.id}")
	private String algoliaAppId;

	private SearchClient client;

	@PostConstruct
	public void init() throws NotFoundException {

		// status step, color step and status labels starts
		Map<String, Integer> statusColorsMap = new HashMap<>();
		Map<String, Integer> statusStatesMap = new HashMap<>();
		List<SalesOrderStatusState> statusStates = salesOrderStatusStateRepository.findAll();
		if (CollectionUtils.isNotEmpty(statusStates)) {
			for (SalesOrderStatusState statusState : statusStates) {
				SalesOrderStatusStatePK key = statusState.getId();
				statusStatesMap.put(key.getStatus(), statusState.getStep());
				statusColorsMap.put(key.getStatus(), statusState.getColorStep());
			}
		}
		this.statusStepMap = statusStatesMap;
		this.statusColorStepMap = statusColorsMap;

		List<SalesOrderStatusLabel> statusLabels = salesOrderStatusLabelRepository.findAll();
		if (CollectionUtils.isNotEmpty(statusLabels)) {

		}

		this.attrMap = getAttributeMapList();

		for (Map.Entry<Integer, String> entry : attrMap.entrySet()) {

			if (entry.getValue().equalsIgnoreCase("price"))
				this.priceAttrId = entry.getKey();
			if (entry.getValue().equalsIgnoreCase("special_price"))
				this.specialPriceAttrId = entry.getKey();
		}


		this.client = DefaultSearchClient.create(algoliaAppId, algoliaApiKey);
	}

	public Map<Integer, String> getAttributeMapList() {

		List<EavAttribute> eavAttributeList = eavAttributeRepository.getEavAttributes();

		Map<Integer, String> mapValue = new HashMap<>();

		for (EavAttribute eavAttribute : eavAttributeList) {

			mapValue.put(eavAttribute.getAttributeId(), eavAttribute.getAttributeCode());
		}

		return mapValue;
	}



	public Map<String, String> getLabelMap() {
		return labelMap;
	}

	public Map<Integer, String> getAttrMap() {
		return attrMap;
	}

	public Integer getPriceAttrId() {
		return priceAttrId;
	}

	public Integer getSpecialPriceAttrId() {
		return specialPriceAttrId;
	}

	private String parseNullStr(Object val) {
		return (val == null) ? null : String.valueOf(val);
	}

	public SearchClient getAlgoliaClient() {

		return client;
	}

	public Map<String, Integer> getStatusStepMap() {

		return statusStepMap;
	}

	public Map<String, Integer> getStatusColorsStepMap() {

		return statusColorStepMap;
	}

	public Map<String, AttributeValue> getAttrStatusMap() {
		return attrStatusMap;
	}

}
