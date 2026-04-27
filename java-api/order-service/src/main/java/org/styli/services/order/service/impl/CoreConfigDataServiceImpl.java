package org.styli.services.order.service.impl;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.styli.services.order.db.product.exception.NotFoundException;
import org.styli.services.order.db.product.pojo.StoreDetailsResponse;
import org.styli.services.order.db.product.pojo.StoreDetailsResponseDTO;
import org.styli.services.order.model.CoreConfigData;
import org.styli.services.order.model.Store;
import org.styli.services.order.repository.CoreConfigDataRepository;
import org.styli.services.order.repository.StoreRepository;
import org.styli.services.order.service.CoreConfigDataService;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.GenericConstants;

@Component
public class CoreConfigDataServiceImpl implements CoreConfigDataService {

    private static final Log LOGGER = LogFactory.getLog(CoreConfigDataServiceImpl.class);

    private static final String VERSION = "version";
    
    @Autowired
    CoreConfigDataRepository coreConfigDataRepository;

    @Autowired
    StoreRepository storeRepository;


    @Override
    public StoreDetailsResponseDTO getStoreDetails(Integer storeId) {

        StoreDetailsResponseDTO storeDetailsResponseDTO = new StoreDetailsResponseDTO();

        Store store = storeRepository.findByStoreId(storeId);
        if (store == null) {
            storeDetailsResponseDTO.setStatus(false);
            storeDetailsResponseDTO.setStatusCode("201");
            storeDetailsResponseDTO.setStatusMsg("Store not found!");
            return storeDetailsResponseDTO;
        }

        storeDetailsResponseDTO.setStatus(true);
        storeDetailsResponseDTO.setStatusCode("200");
        storeDetailsResponseDTO.setStatusMsg("Success!");

        StoreDetailsResponse storeDetailsResponse = new StoreDetailsResponse();

        String currency = getStoreCurrency(storeId);
        storeDetailsResponse.setCurrency(currency);
        storeDetailsResponse.setCode(store.getCode());
        storeDetailsResponse.setId(storeId);

        storeDetailsResponseDTO.setResponse(storeDetailsResponse);

        return storeDetailsResponseDTO;
    }


    @Override
    public String getStoreCurrency(Integer storeId) {
    	
    	Integer websiteId  = null;
    	
    	Store store = storeRepository.findByStoreId(storeId);
    	
	       if(null != store) {
	    	   
	    	   websiteId = store.getWebSiteId();
	       }
	       
	       if(null == websiteId) {
	    	   
	    	   websiteId = 0;
	       }
	   
	       
//        Get store currency
        CoreConfigData coreConfigData = coreConfigDataRepository.findByPathAndScopeId(GenericConstants.CONFIG_CURRENCY_OPTIONS_DEFAULT, websiteId);

//        Fallback to default store currency
        if (coreConfigData == null) {
            coreConfigData = coreConfigDataRepository.findByPathAndScopeId(GenericConstants.CONFIG_CURRENCY_OPTIONS_DEFAULT, Constants.ADMIN_STORE_ID);
        }

//        Fallback to base currency
        if (coreConfigData == null) {
            coreConfigData = coreConfigDataRepository.findByPathAndScopeId(GenericConstants.CONFIG_CURRENCY_OPTIONS_BASE, Constants.ADMIN_STORE_ID);
        }

        if (coreConfigData == null) return null;
        if (coreConfigData.getValue() == null) return null;

        return coreConfigData.getValue();
    }

    @Override
    public String getStoreLanguage(Integer storeId) {
//        Get store currency
        CoreConfigData coreConfigData = coreConfigDataRepository.findByPathAndScopeId(GenericConstants.CONFIG_GENERAL_LOCALE_CODE, storeId);

//        Fallback to default store currency
        if (coreConfigData == null) {
            coreConfigData = coreConfigDataRepository.findByPathAndScopeId(GenericConstants.CONFIG_GENERAL_LOCALE_CODE, Constants.ADMIN_STORE_ID);
        }

        if (coreConfigData == null) return null;
        if (coreConfigData.getValue() == null) return null;

        return coreConfigData.getValue();
    }

    @Override
	public BigDecimal getRMAThresholdInHours(Store store) throws NotFoundException {
		BigDecimal threshold;
		int websiteId = store.getWebSiteId();
		try {
			CoreConfigData coreConfigData = coreConfigDataRepository
					.findByPathAndScopeId(GenericConstants.CONFIG_SALES_RMA_PICKUP_THRESHOLD, websiteId);
			if (coreConfigData == null) {
				coreConfigData = coreConfigDataRepository.findByPathAndScopeId(
						GenericConstants.CONFIG_SALES_RMA_PICKUP_THRESHOLD, GenericConstants.DEFAULT_CONFIG_STORE_ID);
			}
			if (coreConfigData != null && null != coreConfigData.getValue()) {
				threshold = new BigDecimal(coreConfigData.getValue());
				return threshold;
			} else {
				LOGGER.info("coreConfigData is further null");
				throw new NotFoundException("RMA threshold not found", 500);
			}
		} catch (Exception e) {
			LOGGER.error("Could not fetch RMA applicable threshold for store: " + websiteId + e.getMessage());
			throw new NotFoundException("RMA threshold not found", 500);
		}
	}

    @Override
	public BigDecimal getStoreShipmentChargesThreshold(Store store) throws NotFoundException {
		BigDecimal threshold;
		int websiteId = store.getWebSiteId();
		try {
			CoreConfigData coreConfigData = coreConfigDataRepository
					.findByPathAndScopeId(GenericConstants.CONFIG_SALES_SHIPPING_CHARGES_THRESHOLD, websiteId);
			if (coreConfigData != null) {
				threshold = new BigDecimal(coreConfigData.getValue());
				return threshold;
			} else {
				throw new NotFoundException("shipping charges threshold not found", 500);
			}
		} catch (Exception e) {
			LOGGER.error("Could not fetch shipping charges threshold for website: " + websiteId);
			throw new NotFoundException("shipping charges threshold not found website" + websiteId, 500);
		}
	}

    @Override
	public BigDecimal getStoreShipmentCharges(Store store) throws NotFoundException {
		BigDecimal charges;
		int websiteId = 0;
		try {
			websiteId = store != null ? store.getWebSiteId() : 0;
			CoreConfigData coreConfigData = coreConfigDataRepository
					.findByPathAndScopeId(GenericConstants.CONFIG_SALES_SHIPPING_CHARGES, websiteId);
			charges = new BigDecimal(coreConfigData.getValue());
			return charges;
		} catch (Exception e) {
			LOGGER.error("Could not fetch shipping charges for store: " + websiteId);
			int storeId = Objects.nonNull(store) ? store.getStoreId() : 0;
			throw new NotFoundException("store shipping charges not found" + storeId, 500);
		}
	}

    @Override
	public BigDecimal getCodCharges(Store store) throws NotFoundException {
		BigDecimal charges;
		int websiteId = store.getWebSiteId();
		try {
			CoreConfigData coreConfigData = coreConfigDataRepository
					.findByPathAndScopeId(GenericConstants.CONFIG_SALES_COD_CHARGES, websiteId);
			if (coreConfigData != null) {
				charges = new BigDecimal(coreConfigData.getValue());
				return charges;
			} else {
				throw new NotFoundException("cod charges not found", 500);
			}
		} catch (Exception e) {
			LOGGER.error("Could not fetch cod charges for website: " + websiteId);
			throw new NotFoundException("cod charges not found:" + websiteId, 500);
		}
	}

    @Override
	public BigDecimal getTaxPercentage(Integer websiteId) throws NotFoundException {
		BigDecimal taxPercentage;
		try {
			CoreConfigData coreConfigData = coreConfigDataRepository
					.findByPathAndScopeId(GenericConstants.CONFIG_SALES_TAX_PERCENTAGE, websiteId);
			if (coreConfigData != null) {
				taxPercentage = new BigDecimal(coreConfigData.getValue());
				return taxPercentage;
			} else {
				throw new NotFoundException("tax percentage not found for website:" + websiteId, 500);
			}
		} catch (Exception e) {
			LOGGER.error("Could not fetch tax percentage for website: " + websiteId);
			throw new NotFoundException("tax percentage not found website:" + websiteId, 500);
		}
	}


	@Override
	public BigDecimal getCurrencyConversionRate(Integer webSiteId) throws NotFoundException {
		BigDecimal currencyConversionRate;
		try {
			CoreConfigData coreConfigData = coreConfigDataRepository
					.findByPathAndScopeId(GenericConstants.CONFIG_CURRENCY_CONVERSION_RATE, webSiteId);
			if (coreConfigData != null) {
				currencyConversionRate = new BigDecimal(coreConfigData.getValue());
				return currencyConversionRate;
			} else {
				throw new NotFoundException("Currency Conversion rate not found for webSite :" + webSiteId, 500);
			}
		} catch (Exception e) {
			LOGGER.error("Could not fetch currency conversion for store: " + webSiteId);
			throw new NotFoundException("currency conversion:" + webSiteId, 500);
		}
	}


	@Override
	public BigDecimal getStoreShipmentCharges(Integer websiteId) throws NotFoundException {
		BigDecimal charges;
		try {
			CoreConfigData coreConfigData = coreConfigDataRepository
					.findByPathAndScopeId(GenericConstants.CONFIG_SALES_SHIPPING_CHARGES, websiteId);
			if (coreConfigData != null) {
				charges = new BigDecimal(coreConfigData.getValue());
				return charges;
			} else {
				throw new NotFoundException("shipping charges not found", 500);
			}
		} catch (Exception e) {
			LOGGER.error("Could not fetch shipping charges for website: " + websiteId);
			throw new NotFoundException("shipping charges not found" + websiteId, 500);
		}
	}



	@Override
	public BigDecimal getCodCharges(Integer websiteId) throws NotFoundException {
		BigDecimal charges;
		try {
			CoreConfigData coreConfigData = coreConfigDataRepository
					.findByPathAndScopeId(GenericConstants.CONFIG_SALES_COD_CHARGES, websiteId);
			if (coreConfigData != null) {
				charges = new BigDecimal(coreConfigData.getValue());
				return charges;
			} else {
				throw new NotFoundException("cod charges not found", 500);
			}
		} catch (Exception e) {
			LOGGER.error("Could not fetch cod charges for website: " + websiteId);

			throw new NotFoundException("cod charges not found:" + websiteId, 500);
		}
	}


	@Override
	public BigDecimal getTaxPercentage(Store store) throws NotFoundException {
		BigDecimal taxPercentage;
		int websiteId = store != null ? store.getWebSiteId() : 0;

		try {
			CoreConfigData coreConfigData = coreConfigDataRepository
					.findByPathAndScopeId(GenericConstants.CONFIG_SALES_TAX_PERCENTAGE, websiteId);
			if (coreConfigData != null) {
				taxPercentage = new BigDecimal(coreConfigData.getValue());
				return taxPercentage;
			} else {
				throw new NotFoundException("tax percentage not found for website:" + websiteId, 500);
			}
		} catch (Exception e) {
			LOGGER.error("Could not fetch tax percentage for website: " + websiteId);
			throw new NotFoundException("tax percentage not found website:" + websiteId, 500);
		}
	}


	@Override
	public BigDecimal getRMAThresholdInHours(Integer websiteId, String code) throws NotFoundException {
		BigDecimal threshold;
		try {
			CoreConfigData coreConfigData = coreConfigDataRepository
					.findByPathAndScopeId(GenericConstants.CONFIG_SALES_RMA_PICKUP_THRESHOLD, websiteId);
			if (coreConfigData == null) {
				coreConfigData = coreConfigDataRepository.findByPathAndScopeId(
						GenericConstants.CONFIG_SALES_RMA_PICKUP_THRESHOLD, GenericConstants.DEFAULT_CONFIG_STORE_ID);
			}
			if (coreConfigData != null && null != coreConfigData.getValue()) {
				threshold = new BigDecimal(coreConfigData.getValue());
				return threshold;
			} else {
				throw new NotFoundException("RMA applicable threshold not found", 500);
			}
		} catch (Exception e) {
			LOGGER.error("Could not fetch RMA applicable threshold for store: " + websiteId + e.getMessage());
			throw new NotFoundException("RMA applicable threshold not found", 500);
		}
	}


	@Override
	public BigDecimal getStoreShipmentChargesThreshold(Integer websiteId) throws NotFoundException {
		BigDecimal threshold;
		try {
			CoreConfigData coreConfigData = coreConfigDataRepository
					.findByPathAndScopeId(GenericConstants.CONFIG_SALES_SHIPPING_CHARGES_THRESHOLD, websiteId);
			if (coreConfigData != null) {
				threshold = new BigDecimal(coreConfigData.getValue());
				return threshold;
			} else {
				throw new NotFoundException("shipping charges threshold not found", 500);
			}
		} catch (Exception e) {
			LOGGER.error("Could not fetch shipping charges threshold for website: " + websiteId);
			throw new NotFoundException("shipping charges threshold not found website" + websiteId, 500);
		}
	}

	@Override
	public BigDecimal getCustomDutiesPercentage(Store store) throws NotFoundException {
		BigDecimal customDutiesPercentage;
		try {
			int webSiteId = store.getWebSiteId();
			CoreConfigData coreConfigData = coreConfigDataRepository
					.findByPathAndScopeId(GenericConstants.CONFIG_CUSTOM_DUTIES_PERCENTAGE, webSiteId);
			if (coreConfigData != null) {
				customDutiesPercentage = new BigDecimal(coreConfigData.getValue());
				return customDutiesPercentage;
			} else {
				throw new NotFoundException("custom duties percentage not found website" + store.getWebSiteId(), 500);
			}
		} catch (Exception e) {
			LOGGER.error("Could not fetch custom duties percentage for website: " + store.getWebSiteId());
			throw new NotFoundException("custom duties percentage not found website:" + store.getWebSiteId(), 500);
		}
	}

	@Override
	public BigDecimal getImportFeePercentage(Store store) throws NotFoundException {
		BigDecimal importFeePercentage;
		int webSiteId = 0;
		if (null != store) {
			try {
				webSiteId = store.getWebSiteId();
				CoreConfigData coreConfigData = coreConfigDataRepository
						.findByPathAndScopeId(GenericConstants.CONFIG_IMPORT_FEE_PERCENTAGE, webSiteId);
				if (coreConfigData != null) {
					importFeePercentage = new BigDecimal(coreConfigData.getValue());
					return importFeePercentage;
				} else {
					throw new NotFoundException("import fee percentage not found website" + store.getWebSiteId(), 500);
				}
			} catch (Exception e) {
				LOGGER.error("Could not fetch import fee percentage for website: " + webSiteId);
				throw new NotFoundException("import fee percentage not website:" + store.getWebSiteId(), 500);
			}
		} else {
			throw new NotFoundException("import fee percentage not website:" + webSiteId, 500);
		}
	}

	@Override
	public BigDecimal getMinimumDutiesAmount(Store store) throws NotFoundException {
		BigDecimal minimumDutiesAmount;
		try {
			int webSiteId = store.getWebSiteId();
			CoreConfigData coreConfigData = coreConfigDataRepository
					.findByPathAndScopeId(GenericConstants.CONFIG_MINIMUM_DUTIES_AMOUNT, webSiteId);
			if (coreConfigData != null) {
				minimumDutiesAmount = new BigDecimal(coreConfigData.getValue());
				return minimumDutiesAmount;
			} else {
				throw new NotFoundException("minimum duties amount not found for srore:" + store.getWebSiteId(), 500);
			}
		} catch (Exception e) {
			LOGGER.error("Could not fetch minimum duties amount for website: " + e.getMessage());
			throw new NotFoundException("minimum duties amount not found", 500);
		}
	}


		@Override
		public BigDecimal getCustomDutiesPercentage(Integer websiteId) throws NotFoundException {
	        BigDecimal customDutiesPercentage;
	        try {
	            CoreConfigData coreConfigData = coreConfigDataRepository.findByPathAndScopeId(
	                    GenericConstants.CONFIG_CUSTOM_DUTIES_PERCENTAGE,
	                    websiteId);
	            if(coreConfigData != null) {
	                customDutiesPercentage = new BigDecimal(coreConfigData.getValue());
	                return customDutiesPercentage;
	            }
	            else  {
	                throw new NotFoundException("custom duties percentage not found website"+websiteId, 500);
	            }
	        } catch (Exception e) {
	            LOGGER.error("Could not fetch custom duties percentage for website: " +e.getMessage());
	            throw new NotFoundException("custom duties percentage not found website:"+websiteId, 500);
	        }
	    }

		@Override
		public BigDecimal getImportFeePercentage(Integer websiteId) throws NotFoundException {
			BigDecimal importFeePercentage;
			try {
				CoreConfigData coreConfigData = coreConfigDataRepository
						.findByPathAndScopeId(GenericConstants.CONFIG_IMPORT_FEE_PERCENTAGE, websiteId);
				if (coreConfigData != null) {
					importFeePercentage = new BigDecimal(coreConfigData.getValue());
					return importFeePercentage;
				} else {
					throw new NotFoundException("import fee percentage not found website" + websiteId, 500);
				}
			} catch (Exception e) {
				LOGGER.error("Could not fetch import fee percentage for website: " + e.getMessage());
				throw new NotFoundException("import fee percentage not found website:" + websiteId, 500);
			}
		}

		@Override
		public BigDecimal getMinimumDutiesAmount(Integer websiteId) throws NotFoundException {
			BigDecimal minimumDutiesAmount;
			try {
				CoreConfigData coreConfigData = coreConfigDataRepository
						.findByPathAndScopeId(GenericConstants.CONFIG_MINIMUM_DUTIES_AMOUNT, websiteId);

				if (coreConfigData != null) {
					minimumDutiesAmount = new BigDecimal(coreConfigData.getValue());
					return minimumDutiesAmount;
				} else {
					throw new NotFoundException("minimum duties amount not found for website:" + websiteId, 500);
				}
			} catch (Exception e) {
				LOGGER.error("Could not fetch minimum duties amount for website: " + e.getMessage());
				throw new NotFoundException("minimum duties amount not found", 500);
			}
		}

		@Override
		public Integer getQuoteProductMaxQtyNumber(Integer websiteId) throws NotFoundException {
			Integer quoteProductMaxQty = 10;
			try {
				CoreConfigData coreConfigData = coreConfigDataRepository
						.findByPathAndScopeId(GenericConstants.CONFIG_CART_PRODUCT_MAX_QTY, websiteId);
				if (coreConfigData == null) {
					coreConfigData = coreConfigDataRepository.findByPathAndScopeId(
							GenericConstants.CONFIG_CART_PRODUCT_MAX_QTY, GenericConstants.DEFAULT_CONFIG_STORE_ID);
				}
				if (coreConfigData != null && null != coreConfigData.getValue()) {
					quoteProductMaxQty = Integer.parseInt(coreConfigData.getValue());
					return quoteProductMaxQty;
				} else {
					LOGGER.info("coreConfigData is further null for quote paroduct max cap");
					throw new NotFoundException("quote product max cap not found", 500);
				}
			} catch (Exception e) {
				LOGGER.error("quote product max cap not found for store: " + websiteId + e.getMessage());
				throw new NotFoundException("quote product max cap not found", 500);
			}
		}

		@Override
		public BigDecimal getImportMaxFeePercentage(Integer websiteId) throws NotFoundException {
			BigDecimal importmaxFeePercentage;
			try {
				CoreConfigData coreConfigData = coreConfigDataRepository
						.findByPathAndScopeId(GenericConstants.CONFIG_IMPORT_MAX_FEE_PERCENTAGE, websiteId);
				if (coreConfigData != null) {
					importmaxFeePercentage = new BigDecimal(coreConfigData.getValue());
					return importmaxFeePercentage;
				} else {
					throw new NotFoundException("import max fee percentage not found website" + websiteId, 500);
				}
			} catch (Exception e) {
				LOGGER.error("Could not fetch import max fee percentage for website: " + e.getMessage());
				throw new NotFoundException("import max fee percentage not found website:" + websiteId, 500);
			}
		}

		@Override
		public Map<String, String> getCurrentAddressDbVersion() throws NotFoundException {
			Map<String, String> result = new HashMap<>();
			CoreConfigData coreConfigData = null;
			String dbVersion;
			coreConfigData = coreConfigDataRepository
					.findByPathAndScopeId(GenericConstants.CONFIG_CURRENT_ADDRESS_DB_VERSION, Constants.ADMIN_STORE_ID);
			if (coreConfigData != null) {
				dbVersion = coreConfigData.getValue();
				result.put(VERSION, dbVersion);
			} else {
				throw new NotFoundException("db version not found", 500);
			}
			return result;
		}

		@Override
		public Map<String, Object> saveCurrentAddressDbVersion(String addressversion) throws NotFoundException {
			Map<String, Object> result = new LinkedHashMap<>();
			try {
				if (StringUtils.isNotBlank(addressversion)) {
					CoreConfigData coreConfigData = coreConfigDataRepository.findByPathAndScopeId(
							GenericConstants.CONFIG_CURRENT_ADDRESS_DB_VERSION, Constants.ADMIN_STORE_ID);
					if (null != coreConfigData) {
						coreConfigData.setValue(addressversion);
						coreConfigDataRepository.saveAndFlush(coreConfigData);
					}
				} else {
					result.put("status", false);
					result.put(VERSION, addressversion);
					return result;
				}
			} catch (Exception e) {
				LOGGER.error("Could not save address database version " + e.getMessage());
				throw new NotFoundException("Could not save address database version:", 500);
			}
			result.put("status", true);
			result.put(VERSION, addressversion);
			return result;
		}

		@Override
		public BigDecimal getCatalogCurrencyConversionRate(Integer webSiteId) throws NotFoundException {
			BigDecimal currencyConversionRate;
			try {
				CoreConfigData coreConfigData = coreConfigDataRepository
						.findByPathAndScopeId(GenericConstants.CATALOG_CURRENCY_CONVERSION_RATE, webSiteId);
				if (coreConfigData != null) {
					currencyConversionRate = new BigDecimal(coreConfigData.getValue());
					return currencyConversionRate;
				} else {
					throw new NotFoundException("Catalog Currency Conversion rate not found for webSite :" + webSiteId,
							500);
				}
			} catch (Exception e) {
				LOGGER.error("Could not fetch catalog currency conversion for store: " + e.getMessage());
				throw new NotFoundException("catalog currency conversion:" + webSiteId, 500);
			}
		}
	
}
