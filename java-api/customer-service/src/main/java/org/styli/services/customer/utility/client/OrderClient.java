package org.styli.services.customer.utility.client;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import javax.validation.constraints.NotNull;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.styli.services.customer.utility.exception.CustomerException;
import org.styli.services.customer.utility.pojo.category.MagentoCategoryListRes;
import org.styli.services.customer.utility.pojo.config.CoreConfigDataService;
import org.styli.services.customer.utility.pojo.config.Store;
import org.styli.services.customer.utility.pojo.config.StoreGroup;
import org.styli.services.customer.utility.pojo.config.Stores;

@FeignClient(value = "order", url = "${order.ribbon.listOfServers}")
public interface OrderClient {

        @GetMapping("/findallstores")
        List<Store> findAllStores();

        @GetMapping("/findstorebystoreid/{storeId}")
        Store findStoreByStoreId(@PathVariable @NotNull Integer storeId);

        @GetMapping("/findbywebsiteid/{websiteId}")
        List<Store> findByWebsiteId(@PathVariable @NotNull Integer websiteId);

        @GetMapping("/getstoresarray")
        List<Stores> getStoresArray();

        @GetMapping("/getallstoregroup")
        List<StoreGroup> getAllStoreGroup();

        @GetMapping("/getcoreconfigdataservice/storeid/{storeId}/websiteid/{websiteId}/storecode/{storeCode}")
        CoreConfigDataService getCoreConfigDataService(@PathVariable @NotNull Integer storeId,
                        @PathVariable @NotNull Integer websiteId, @PathVariable @NotNull String storeCode);

        @GetMapping("/getmagentocategories/{storeId}")
        MagentoCategoryListRes getAllCategories(@RequestHeader Map<String, String> requestHeader,
                        @PathVariable Integer storeId) throws CustomerException;

        @GetMapping("/getcurrencyconversionrate/{websiteId}")
		BigDecimal getCurrencyConversionRate(@PathVariable @NotNull Integer websiteId);

        @GetMapping("/getstorelanguagee/{websiteId}")
		String getStoreLanguage(@PathVariable @NotNull Integer websiteId);

        @GetMapping("/getstoreCurrencye/{websiteId}")
		String getStoreCurrency(@PathVariable @NotNull Integer websiteId);

        @GetMapping("/getshipmentchargesthreadsholde/{websiteId}")
		BigDecimal getStoreShipmentChargesThreshold(@PathVariable @NotNull Integer websiteId);

        @GetMapping("/getshipmentchargese/{websiteId}")
		BigDecimal getStoreShipmentCharges(@PathVariable @NotNull Integer websiteId);

        @GetMapping("/getcodchargese/{websiteId}")
		BigDecimal getCodCharges(@PathVariable @NotNull Integer websiteId);

        @GetMapping("/gettaxpercenatagee/{websiteId}")
		BigDecimal getTaxPercentage(@PathVariable @NotNull Integer websiteId);
        
        
        @GetMapping("/getcustomdutypercentage/{websiteId}")
		BigDecimal getCustomDutiesPercentage(@PathVariable @NotNull Integer websiteId);

        @GetMapping("/getimportfeepercenatge/{websiteId}")
		BigDecimal getImportFeePercentage(@PathVariable @NotNull Integer websiteId);

        @GetMapping("/gettminimumdutyfee/{websiteId}")
		BigDecimal getMinimumDutyFee(@PathVariable @NotNull Integer websiteId);
        
        @GetMapping("/getquoteproductmaxqty/{websiteId}")
		Integer getQuoteProductMaxQty(@PathVariable @NotNull Integer websiteId);
        
        @GetMapping("/getimportmaxfeepercenatge/{websiteId}")
		BigDecimal getimportMaxfeepercenatge(@PathVariable @NotNull Integer websiteId);
        
        @GetMapping("/getcatalogcurrencyconversionrate/{websiteId}")
		BigDecimal getCatalogCurrencyConversionRate(@PathVariable @NotNull Integer websiteId);
        
        
        

}
