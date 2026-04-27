package org.styli.services.order.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.orbitz.consul.Consul;
import com.orbitz.consul.KeyValueClient;
import com.orbitz.consul.cache.KVCache;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.pojo.config.FeatureFlag;
import org.styli.services.order.pojo.config.StoreConfigResponse;
import org.styli.services.order.pojo.consul.oms.base.OmsBaseConfigs;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.consulValues.MailPatternConfigs;
import org.styli.services.order.utility.consulValues.ServiceConfigs;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

@Component
public class ConsulComponent {

    private static final Log LOGGER = LogFactory.getLog(ConsulComponent.class);

    public static final ObjectMapper mapper = new ObjectMapper();

    @Value("${consul.ip.address}")
    private String consulIpAddress;

    @Value("${env}")
    private String env;

    @Value("${consul.port}")
    private String consulPort;
    
    @Value("${consul.token}")
    private String consulToken;
    
    private Consul client;

    @PostConstruct
    public void init() {
    	LOGGER.info("consulToken" + consulToken);
        client = Consul.builder().withUrl("http://" + consulIpAddress + ":" + consulPort)
        		.withAclToken(consulToken).build();
        final KeyValueClient kvClient = client.keyValueClient();
        cache(Constants.CONSUL_ORDER_KEYS, env, kvClient, false);
        cache(Constants.PROMO_CONSUL_VALUES, env, kvClient, false);
        cache(Constants.PROMO_URL_KEY, env, kvClient, true);
        authcache(Constants.AUTH_URL_KEY, env, kvClient);
        cacheOrderCredentials(Constants.CONSUL_ORDER_CREDENTIALS_KEY, kvClient,env);
        cache("sa", env, kvClient);
        cache("ae", env, kvClient);
        cache("kw", env, kvClient);
        cache("qa", env, kvClient);
        cache("bh", env, kvClient);
        cache("om", env, kvClient);
        cacheAppConfig(Constants.CONSUL_APPCONFIG_KEYS, kvClient, env);
        cacheAlphaToken(env, kvClient);
        cacheBetaToken(env, kvClient);
        cacheOmsBase(Constants.CONSUL_OMS_BASE_KEYS, kvClient, env);

        cacheFeatureConfig(Constants.CONSUL_FEATURECONGIF_KEYS, kvClient, env);
        startServiceCache(ServiceConfigs.CONSUL_CUSTOMER_SERVICE_KEY, env, kvClient);
        startMailPatternCache(MailPatternConfigs.CONSUL_MAIL_PATTERN_KEY, env, kvClient);

    }
    
	public Consul getConsul() {
		return client;
	}


    public static void cacheOmsBase(String key, KeyValueClient kvClient, String env) {
        String cacheKey = key + "_" + env;
        KVCache cache = KVCache.newCache(kvClient, cacheKey);
        cache.addListener(newValues -> {
            for (com.orbitz.consul.model.kv.Value value : newValues.values()) {
                if (value.getKey().equals(cacheKey)) {

                    String decodedValue = value.getValueAsString().get();
                    LOGGER.info("oms/base :" + decodedValue);
                    try {
                        OmsBaseConfigs omsBaseConfigs = Constants.JSON_MAPPER.readValue(
                                decodedValue,
                                OmsBaseConfigs.class
                        );

                        if(omsBaseConfigs != null) {
                            Constants.setOmsBaseConfigs(omsBaseConfigs);
                        }
                    }catch(Exception exception) {

                        LOGGER.error("error occoured during store parse from consul:"+exception.getMessage());
                    }


                }
            }
        });
        cache.start();
    }

    public static void cacheAppConfig(String key, KeyValueClient kvClient, String env) {
        String cacheKey = key + "_" + env;
        KVCache cache = KVCache.newCache(kvClient, cacheKey);
        cache.addListener(newValues -> {
            for (com.orbitz.consul.model.kv.Value value : newValues.values()) {
                if (value.getKey().equals(cacheKey)) {

                    String decodedValue = value.getValueAsString().get();
                    LOGGER.info("appConfig :" + decodedValue);
                    try {

                    	 Gson g = new Gson();
                         StoreConfigResponse storeConfigResponseFromConsul = g.fromJson(decodedValue, StoreConfigResponse.class);
                         if(null != storeConfigResponseFromConsul.getBaseCurrencyCode()) {
                        	 Constants.setBaseCurrencyCode(storeConfigResponseFromConsul.getBaseCurrencyCode());
                         }
                         List<Stores>  storesList = storeConfigResponseFromConsul.getEnvironments().get(0).getStores();
                         Constants.setShukranProgramCode(storeConfigResponseFromConsul.getShukranProgramCode());
                         Constants.setShukranSourceApplication(storeConfigResponseFromConsul.getShukranSourceApplication());
                         Constants.setGlobalRedisKey(storeConfigResponseFromConsul.getGlobalRedisKey());
                         Constants.setStoresList(storesList);
                         Constants.setShukranEnrollmentConceptCode(storeConfigResponseFromConsul.getShukranEnrollmentConceptCode());
                         Constants.setShukranEnrollmentCommonCode(storeConfigResponseFromConsul.getShukranEnrollmentCommonCode());
                         Constants.setShukranItemTypeCode(storeConfigResponseFromConsul.getShukranItemTypeCode());
                         Constants.setShukranTransactionRTPRURL(storeConfigResponseFromConsul.getShukranTransactionRTPRURL());
                         Constants.setShukarnEnrollmentStoreCode(storeConfigResponseFromConsul.getShukarnEnrollmentStoreCode());
                         Constants.setDisabledServices(storeConfigResponseFromConsul.getDisabledServices());
                    }catch(Exception exception) {

                    	LOGGER.error("error occoured during store parse from consul:"+exception.getMessage());
                    }


                }
            }
        });
        cache.start();
    }

    public static void cache(String key, String env, KeyValueClient kvClient, boolean isFromPromoUrl) {
        if (isFromPromoUrl) {
            KVCache cache = KVCache.newCache(kvClient, key);
            cache.addListener(newValues -> {
                for (com.orbitz.consul.model.kv.Value value : newValues.values()) {
                    if (value.getKey().equals(key)) {
                        String decodedValue = value.getValueAsString().get();
                        LOGGER.info("decodedValue:" + decodedValue);
                        try {
                            HashMap<String, Object> map = new Gson().fromJson(decodedValue, HashMap.class);
                            Constants.setPromoBaseUrl(map.get(env).toString());
                        } catch (Exception e) {
                            LOGGER.info("consul catch error:" + e.getMessage());

                        }
                    }
                }
            });
            cache.start();
        } else {
            KVCache cache = KVCache.newCache(kvClient, key + "_" + env);
            cache.addListener(newValues -> {
                for (com.orbitz.consul.model.kv.Value value : newValues.values()) {
                    if (value.getKey().equals(key + "_" + env)) {
                        String decodedValue = value.getValueAsString().get();
                        LOGGER.info("decodedValue:" + decodedValue);
                        
                        if(key.contains(Constants.PROMO_CONSUL_VALUES)) {
                        	Constants.setPromoConsulValues(decodedValue);
                        } else {
                        	Constants.setOrderConsulValues(decodedValue);
                        }
                    }
                }
            });
            cache.start();
        }

    }

    public static void authcache(String key, String env, KeyValueClient kvClient) {
            KVCache cache = KVCache.newCache(kvClient, key);
            cache.addListener(newValues -> {
                for (com.orbitz.consul.model.kv.Value value : newValues.values()) {
                	 if (value.getKey().equals(key + "_" + env))  {
                        String decodedValue = value.getValueAsString().get();
                        LOGGER.info("auth decodedValue:" + decodedValue);
                        try {
                            HashMap<String, Object> map = new Gson().fromJson(String.valueOf(decodedValue),
                                    HashMap.class);
                            Constants.setJwtToken(map);
                        } catch (Exception e) {
                            LOGGER.info("consul catch error:" + e.getMessage());

                        }
                    }
                }
            });
            cache.start();
        }

    

    public static void cacheOrderCredentials(String key, KeyValueClient kvClient, String env) {
        String cacheKey = key+"_"+env ;
        KVCache cache = KVCache.newCache(kvClient, cacheKey);
        cache.addListener(newValues -> {
            for (com.orbitz.consul.model.kv.Value value : newValues.values()) {
                if (value.getKey().equals(cacheKey)) {

                	String decodedValue = value.getValueAsString().get();
                	LOGGER.info("order credentials:"+decodedValue);
                	Constants.setOrderCredentials(decodedValue);

                }
            }
        });
        cache.start();
    }

    public static void cache(String countryCode, String env, KeyValueClient kvClient) {
        KVCache cache = KVCache.newCache(kvClient, "addressMapper_" + countryCode + "_" + env);
        cache.addListener(
            newValues -> {
              for (com.orbitz.consul.model.kv.Value value : newValues.values()) {
                if (value.getKey().equals("addressMapper_" + countryCode + "_" + env)) {
                  String decodedValue = value.getValueAsString().get();
                  LOGGER.info(" address decodedValue:" + decodedValue);
                  Constants.setAddressMapper(countryCode, String.valueOf(decodedValue));
                }
              }
            });
        cache.start();
      }

	public static void cacheAlphaToken(String env, KeyValueClient kvClient) {
		KVCache cache = KVCache.newCache(kvClient, Constants.CONSUL_ALPHA_TOKEN_KEY + env);
		cache.addListener(newValues -> {
			for (com.orbitz.consul.model.kv.Value value : newValues.values()) {
				if (value.getKey().equals(Constants.CONSUL_ALPHA_TOKEN_KEY + env)) {
					String decodedValue = value.getValueAsString().get();
					LOGGER.info("Alpha Auth Token:" + decodedValue);
					Constants.setAlphaToken(String.valueOf(decodedValue));
				}
			}
		});
		cache.start();
	}
	
	public void updateAlphaToken(String token) {
		KeyValueClient kvclient = client.keyValueClient();
		kvclient.putValue(Constants.CONSUL_ALPHA_TOKEN_KEY + env, token);
	}

	public static void cacheBetaToken(String env, KeyValueClient kvClient) {
		KVCache cache = KVCache.newCache(kvClient, Constants.CONSUL_BETA_TOKEN_KEY + env);
		cache.addListener(newValues -> {
			for (com.orbitz.consul.model.kv.Value value : newValues.values()) {
				if (value.getKey().equals(Constants.CONSUL_BETA_TOKEN_KEY + env)) {
					String decodedValue = value.getValueAsString().get();
					LOGGER.info("Beta Auth Token:" + decodedValue);
					Constants.setBetaToken(String.valueOf(decodedValue));
				}
			}
		});
		cache.start();
	}
	
	public void updateBetaToken(String token) {
		KeyValueClient kvclient = client.keyValueClient();
		kvclient.putValue(Constants.CONSUL_BETA_TOKEN_KEY + env, token);
	}
	
    public static void cacheFeatureConfig(String key, KeyValueClient kvClient, String env) {
    	 String cacheKey = key + "_" + env;
         KVCache cache = KVCache.newCache(kvClient, cacheKey);
         cache.addListener(newValues -> {
             for (com.orbitz.consul.model.kv.Value value : newValues.values()) {
                 if (value.getKey().equals(cacheKey)) {
                     String decodedValue = value.getValueAsString().get();
                     LOGGER.info("feature_config :" + decodedValue);
                     try {
                 	 	Gson g = new Gson();
                 	 	FeatureFlag featureFlag = g.fromJson(decodedValue, FeatureFlag.class);
                 	 	List<Integer>  zatcaFlag = featureFlag.getZatca();
	                    Constants.setZatcaFlag(zatcaFlag);
                     }catch(Exception exception) {
                     	LOGGER.error("error occoured during store parse from consul:"+exception.getMessage());
                     }
                 }
             }
         });
         cache.start();
    }

    public static void startServiceCache(String key, String env, KeyValueClient kvClient) {
        final String rootPath = key + "_" + env;
        KVCache cache = KVCache.newCache(kvClient, rootPath);
        cache.addListener(
                newValues -> {
                    for (com.orbitz.consul.model.kv.Value value : newValues.values()) {
                        if (value.getKey().equals(rootPath)) {
                            String decodedValue = value.getValueAsString().get();
                            LOGGER.info("customerServiceCache decodedValue:" + decodedValue);
                            try {
                                LinkedHashMap<String, Object> map =
                                        mapper.readValue(decodedValue, LinkedHashMap.class);
                                if(MapUtils.isNotEmpty(map))
                                    ServiceConfigs.setConsulServiceMap(map);
                            } catch (Exception e) {
                                LOGGER.error("consul customerServiceCache error:" + e.getMessage());
                            }
                        }
                    }
                });
        cache.start();
    }

    public static void startMailPatternCache(String key, String env, KeyValueClient kvClient) {
        final String rootPath = key + "_" + env;
        KVCache cache = KVCache.newCache(kvClient, rootPath);
        cache.addListener(
                newValues -> {
                    for (com.orbitz.consul.model.kv.Value value : newValues.values()) {
                        if (value.getKey().equals(rootPath)) {
                            String decodedValue = value.getValueAsString().get();
                            LOGGER.info("customerMailPatternCache decodedValue:" + decodedValue);
                            try {
                                LinkedHashMap<String, Object> map =
                                        mapper.readValue(decodedValue, LinkedHashMap.class);
                                if(MapUtils.isNotEmpty(map))
                                    MailPatternConfigs.setConsulMailPatternMap(map);
                            } catch (Exception e) {
                                LOGGER.error("consul mailPatternCache error:" + e.getMessage());
                            }
                        }
                    }
                });
        cache.start();
    }
}