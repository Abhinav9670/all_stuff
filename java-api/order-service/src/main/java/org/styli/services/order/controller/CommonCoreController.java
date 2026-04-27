package org.styli.services.order.controller;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.styli.services.order.db.product.exception.NotFoundException;
import org.styli.services.order.pojo.AttributeValue;
import org.styli.services.order.service.CommonService;
import org.styli.services.order.service.CoreConfigDataService;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


@RestController
@JsonIgnoreProperties(ignoreUnknown = true)
public class CommonCoreController {

  @Autowired
  CommonService commonService;
  @Autowired
  CoreConfigDataService coreConfigDataService;

  @GetMapping("/getattributestatus")
  public Map<String, AttributeValue> getAttributeStatus() {
    return commonService.getAttributeStatus();
  }

  @GetMapping("/getcurrencyconversionrate/{websiteId}")
  public BigDecimal getCurrencyConversionRate(@PathVariable Integer websiteId) throws NotFoundException {

    return coreConfigDataService.getCurrencyConversionRate(websiteId);
  }

  @GetMapping("/getstorelanguagee/{websiteId}")
  public String getStoreLanguage(@PathVariable Integer websiteId) {

    return coreConfigDataService.getStoreLanguage(websiteId);
  }

  @GetMapping("/getstoreCurrencye/{websiteId}")
  public String getStoreCurrency(@PathVariable Integer websiteId) {

    return coreConfigDataService.getStoreCurrency(websiteId);
  }

  @GetMapping("/getshipmentchargesthreadsholde/{websiteId}")
  public BigDecimal getStoreShipmentChargesThreshold(@PathVariable Integer websiteId) throws NotFoundException {

    return coreConfigDataService.getStoreShipmentChargesThreshold(websiteId);
  }

  @GetMapping("/getshipmentchargese/{websiteId}")
  public BigDecimal getStoreShipmentCharges(@PathVariable Integer websiteId) throws NotFoundException {

    return coreConfigDataService.getStoreShipmentCharges(websiteId);
  }

  @GetMapping("/getcodchargese/{websiteId}")
  public BigDecimal getCodCharges(@PathVariable Integer websiteId) throws NotFoundException {

    return coreConfigDataService.getCodCharges(websiteId);
  }

  @GetMapping("/gettaxpercenatagee/{websiteId}")
  public BigDecimal getTaxPercentage(@PathVariable Integer websiteId) throws NotFoundException {

    return coreConfigDataService.getTaxPercentage(websiteId);
  }
  
  @GetMapping("/getcustomdutypercentage/{websiteId}")
  public BigDecimal getCustomDutiesPercentage(@PathVariable Integer websiteId) throws NotFoundException {

    return coreConfigDataService.getCustomDutiesPercentage(websiteId);
  }

  @GetMapping("/getimportfeepercenatge/{websiteId}")
  public BigDecimal getImportFeePercentage(@PathVariable Integer websiteId) throws NotFoundException {

    return coreConfigDataService.getImportFeePercentage(websiteId);
  }

  @GetMapping("/gettminimumdutyfee/{websiteId}")
  public BigDecimal getMinimumDutyFee(@PathVariable Integer websiteId) throws NotFoundException {

    return coreConfigDataService.getMinimumDutiesAmount(websiteId);
  }
  
  @GetMapping("/getquoteproductmaxqty/{websiteId}")
  public Integer getQuoteProductMaxQty(@PathVariable Integer websiteId) throws NotFoundException {

    return coreConfigDataService.getQuoteProductMaxQtyNumber(websiteId);
  }
  
  @GetMapping("/getimportmaxfeepercenatge/{websiteId}")
  public BigDecimal getImportMaxFeePercentage(@PathVariable Integer websiteId) throws NotFoundException {

    return coreConfigDataService.getImportMaxFeePercentage(websiteId);
  }
  
  @GetMapping("/getaddressdbversion")
  public Map<String, String> geAddrerssDatabaseVersion() throws NotFoundException {
    return coreConfigDataService.getCurrentAddressDbVersion();
    
  }
  
  @GetMapping("/saveaddressdbversion/{addressversion}")
  public Map<String, Object> saveAddressDatabaseVersion(@PathVariable String addressversion) throws NotFoundException {
    return coreConfigDataService.saveCurrentAddressDbVersion(addressversion);
    
  }
  
  @GetMapping("/getcatalogcurrencyconversionrate/{websiteId}")
  public BigDecimal getCatalogCurrencyConversionRate(@PathVariable Integer websiteId) throws NotFoundException {

    return coreConfigDataService.getCatalogCurrencyConversionRate(websiteId);
  }


}
