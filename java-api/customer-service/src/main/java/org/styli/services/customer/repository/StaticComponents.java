package org.styli.services.customer.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.styli.services.customer.exception.NotFoundException;
import org.styli.services.customer.model.EavAttribute;
import org.styli.services.customer.model.Store;
import org.styli.services.customer.pojo.Stores;
import org.styli.services.customer.repository.Customer.CustomerAddressEntityRepository;
import org.styli.services.customer.repository.Customer.EavAttributeRepository;
import org.styli.services.customer.service.CoreConfigDataService;
import org.styli.services.customer.utility.GenericConstants;

import com.algolia.search.DefaultSearchClient;
import com.algolia.search.SearchClient;

@Component
@Scope("singleton")
public class StaticComponents {

    @Autowired
    CustomerAddressEntityRepository customerAddressEntityRepository;

    @Autowired
    StoreRepository storeRepository;

    @Autowired
    EavAttributeRepository eavAttributeRepository;

    @Autowired
    CoreConfigDataService coreConfigDataService;

    Map<Integer, String> attrMap;

    List<Stores> storesArray = new ArrayList<>();

    private SearchClient searchClient;

    Map<String, String> labelMap;

    @Value("${algolia.api.key}")
    private String algoliaApiKey;

    @Value("${algolia.app.id}")
    private String algoliaAppId;

    @PostConstruct
    public void init() throws NotFoundException {

        this.attrMap = getAttributeMapList();


        List<Store> storesList = storeRepository.findAll();

        for (Store storeObj : storesList) {
            if (!storeObj.getStoreId().equals(GenericConstants.ADMIN_STORE_ID)) {
                Stores stores = new Stores();
                stores.setStoreId(parseNullStr(storeObj.getStoreId()));
                stores.setStoreCode(parseNullStr(storeObj.getCode()));
                stores.setStoreLanguage(coreConfigDataService.getStoreLanguage(storeObj.getStoreId()));
                stores.setStoreCurrency(coreConfigDataService.getStoreCurrency(storeObj.getStoreId()));
               
                stores.setWebsiteId(storeObj.getWebSiteId());
           
                stores.setWebsiteCode("");
                this.storesArray.add(stores);
            }
        }

        this.searchClient = DefaultSearchClient.create(algoliaAppId, algoliaApiKey);

    }

    public Map<String, String> getLabelMap() {
        return labelMap;
    }


    public Map<Integer, String> getAttributeMapList() {

        List<EavAttribute> eavAttributeList = eavAttributeRepository.getEavAttributes();

        Map<Integer, String> mapValue = new HashMap<>();

        for (EavAttribute eavAttribute : eavAttributeList) {

            mapValue.put(eavAttribute.getAttributeId(), eavAttribute.getAttributeCode());
        }

        return mapValue;
    }

    private String parseNullStr(Object val) {
        return (val == null) ? null : String.valueOf(val);
    }

    public Map<Integer, String> getAttrMap() {
        return attrMap;
    }

    public List<Stores> getStoresArray() {
        return storesArray;
    }

    public SearchClient getAlgoliaClient() {

        return searchClient;
    }

}