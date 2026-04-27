const cache = require('memory-cache');
const { logError, logInfo } = require('../helpers/utils');
// const logger = require('../helpers/utils');
const watchBaseConfig = require('./watch/watch_base_config');

exports.initConsul = () => {
    try {
        // logger.info("Consul initialization starting...");
        const configCacheKey = process.env.CONSUL_KEY;
        const customerAuthConfigKey = process.env.CONSUL_CUSTOMER_AUTH_CONFIG;
        
        const consulOptions = {
          host: process.env.CONSUL_HOST,
          port: process.env.CONSUL_PORT,
          defaults: {
            token: process.env.CONSUL_TOKEN,
          },
          promisify: true
        };


        const consul = require('consul')(consulOptions);

        // logger.info("Consul watchers starting...");

        const customerAuthConfigWatch = consul.watch({
            method: consul.kv.get,
            options: { key: customerAuthConfigKey },
            backoffFactor: 1000,
        });

        customerAuthConfigWatch.on('change', function (data, res) {
            try {
                const customerAuthConfigCache = cache.get(customerAuthConfigKey);
                if (data?.Value) {
                    const parsedJson = JSON.parse(data?.Value);
                    cache.put(customerAuthConfigKey, parsedJson || customerAuthConfigCache);
                }
            } catch (e) {
                logError(e, "error settting quote config cache ");
            }
        });

        customerAuthConfigWatch.on('error', function (err) {
            logError(err, 'error watchin consul : ')
        });

        const watch = consul.watch({
            method: consul.kv.get,
            options: { key: configCacheKey },
            backoffFactor: 1000,
        });

        watch.on('change', function (data, res) {
            try {
                const existingCache = cache.get(configCacheKey);
                if (data?.Value) {
                    const parsedJson = JSON.parse(data?.Value);
                    cache.put(configCacheKey, parsedJson || existingCache);
                }
            } catch (e) {
                logError(e, "error settting config cache ");
            }
        });

        watch.on('error', function (err) {
            logError(err, 'error watchin consul : ')
        });

        if (process.env.REGION !== "IN") {
            adrsmprConsulCache(consul);
        }
        promoConsulCache(consul);
        watchAdminBaseConfig(consul);
        watchFreeShippingConfig(consul);
        consulPaymentMethodsWacher(consul);
        if(process.env.REGION == "IN"){
            consulTaxIn(consul)
        }
    } catch (e) {
        logError(e, 'Error initializing consul')
    }
}
/**
 * Capture Payment methods from Consul
 * @param {*} consul 
 */
const consulPaymentMethodsWacher = (consul) =>{
    const paymentMethodKey = process.env.CONSUL_PAYMENT_METHODS;
    const paymentMethodsWatcher = consul.watch({
        method: consul.kv.get,
        options: { key: paymentMethodKey },
        backoffFactor: 1000,
    });
    paymentMethodsWatcher.on('change', (updatedData) => {
        try {
            if (updatedData?.Value) {
                const paymentMethods = JSON.parse(updatedData?.Value);
                cache.put(`paymentMethods`, paymentMethods);
            }
        } catch (e) {
            logError(e, "error setting payment methods consul keys KWcache ");
        }
    });
    paymentMethodsWatcher.on('error', function (err) {
        logError(err, 'error watching payment methods consul keys : ')

    });
}



/**
 * Capture state mapping for India from Consul
 * @param {*} consul 
 */
 const consulTaxIn = (consul) =>{
    const taxMethodKey = process.env.CONSUL_TAX;
    const taxMethodsWatcher = consul.watch({
        method: consul.kv.get,
        options: { key: taxMethodKey },
        backoffFactor: 1000,
    });
    taxMethodsWatcher.on('change', (updatedData) => {
        try {
            if (updatedData?.Value) {
                const taxvalue = JSON.parse(updatedData?.Value);
                cache.put(`taxIn`, taxvalue);
            }
        } catch (e) {
            logError(e, "error setting payment methods consul keys KWcache ");
        }
    });
    taxMethodsWatcher.on('error', function (err) {
        logError(err, 'error watching payment methods consul keys : ')

    });
 }

/**
 * Capture Adrsmpr mapping
 * @param {*} consul 
 */
const adrsmprConsulCache = (consul) => {
  const addressMapperKeySA = process.env.CONSUL_ADDRESS_MAPPER_KEY_SA;
  const addressMapperKeyKW = process.env.CONSUL_ADDRESS_MAPPER_KEY_KW;
  const addressMapperKeyAE = process.env.CONSUL_ADDRESS_MAPPER_KEY_AE;
  const addressMapperKeyBH = process.env.CONSUL_ADDRESS_MAPPER_KEY_BH;
  const addressMapperKeyQA = process.env.CONSUL_ADDRESS_MAPPER_KEY_QA;
  const addressMapperKeyOM = process.env.CONSUL_ADDRESS_MAPPER_KEY_OM;

  const addrressMapperwatchSA = consul.watch({
    method: consul.kv.get,
    options: { key: addressMapperKeySA },
    backoffFactor: 1000,
  });

  addrressMapperwatchSA.on("change", function (addressData, res) {
    try {
      const existingAddressCache = cache.get(addressMapperKeySA);
      if (addressData?.Value) {
        const parsedAddressJson = JSON.parse(addressData?.Value);
        const saData = parsedAddressJson?.provinces
          ? parsedAddressJson?.provinces.SA
          : undefined;
        cache.put(addressMapperKeySA, saData || existingAddressCache);
      }
    } catch (e) {
      logError(e, "error settting address Mapper SA cache ");
    }
  });

  addrressMapperwatchSA.on("error", function (err) {
    logError(
      err,
      `error watchin address Mapper SA consul : ${addressMapperKeySA}`
    );
  });

  const addrressMapperwatchKW = consul.watch({
    method: consul.kv.get,
    options: { key: addressMapperKeyKW },
    backoffFactor: 1000,
  });

  addrressMapperwatchKW.on("change", function (addressData, res) {
    try {
      const existingAddressCache = cache.get(addressMapperKeyKW);
      if (addressData?.Value) {
        const parsedAddressJson = JSON.parse(addressData?.Value);

        const kwData = parsedAddressJson?.provinces
          ? parsedAddressJson?.provinces.KW
          : undefined;
        cache.put(addressMapperKeyKW, kwData || existingAddressCache);
      }
    } catch (e) {
      logError(e, "error settting address Mapper KWcache ");
    }
  });

  addrressMapperwatchKW.on("error", function (err) {
    logError(err, "error watchin address Mapper KW consul : ");
  });

  const addrressMapperwatchAE = consul.watch({
    method: consul.kv.get,
    options: { key: addressMapperKeyAE },
    backoffFactor: 1000,
  });

  addrressMapperwatchAE.on("change", function (addressData, res) {
    try {
      const existingAddressCache = cache.get(addressMapperKeyAE);
      if (addressData?.Value) {
        const parsedAddressJson = JSON.parse(addressData?.Value);

        const aeData = parsedAddressJson?.provinces
          ? parsedAddressJson?.provinces.AE
          : undefined;
        cache.put(addressMapperKeyAE, aeData || existingAddressCache);
      }
    } catch (e) {
      logError(e, "error settting address Mapper AE cache ");
    }
  });

  addrressMapperwatchAE.on("error", function (err) {
    logError(err, "error watchin address Mapper AE consul : ");
  });

  const addrressMapperwatchQA = consul.watch({
    method: consul.kv.get,
    options: { key: addressMapperKeyQA },
    backoffFactor: 1000,
  });

  addrressMapperwatchQA.on("change", function (addressData, res) {
    try {
      const existingAddressCache = cache.get(addressMapperKeyQA);
      if (addressData?.Value) {
        const parsedAddressJson = JSON.parse(addressData?.Value);

        const qaData = parsedAddressJson?.provinces
          ? parsedAddressJson?.provinces.QA
          : undefined;
        cache.put(addressMapperKeyQA, qaData || existingAddressCache);
      }
    } catch (e) {
      logError(e, "error settting address Mapper QA cache ");
    }
  });

  addrressMapperwatchQA.on("error", function (err) {
    logError(
      err,
      `error watchin address Mapper QA consul : ${addressMapperKeyQA}`
    );
  });

  const addrressMapperwatchBH = consul.watch({
    method: consul.kv.get,
    options: { key: addressMapperKeyBH },
    backoffFactor: 1000,
  });

  addrressMapperwatchBH.on("change", function (addressData, res) {
    try {
      const existingAddressCache = cache.get(addressMapperKeyBH);
      if (addressData?.Value) {
        const parsedAddressJson = JSON.parse(addressData?.Value);

        const bhData = parsedAddressJson?.provinces
          ? parsedAddressJson?.provinces.BH
          : undefined;
        cache.put(addressMapperKeyBH, bhData || existingAddressCache);
      }
    } catch (e) {
      logError(e, "error settting address Mapper BH cache ");
    }
  });

  addrressMapperwatchBH.on("error", function (err) {
    logError(
      err,
      `error watchin address Mapper BH consul :${addressMapperKeyBH} `
    );
  });

  const addrressMapperwatchOM = consul.watch({
    method: consul.kv.get,
    options: { key: addressMapperKeyOM },
    backoffFactor: 1000,
  });

  addrressMapperwatchOM.on("change", function (addressData, res) {
    try {
      const existingAddressCache = cache.get(addressMapperKeyOM);
      if (addressData?.Value) {
        const parsedAddressJson = JSON.parse(addressData?.Value);

        const omData = parsedAddressJson?.provinces
          ? parsedAddressJson?.provinces.OM
          : undefined;
        cache.put(addressMapperKeyOM, omData || existingAddressCache);
      }
    } catch (e) {
      logError(e, "error settting address Mapper OM cache ");
    }
  });

  addrressMapperwatchOM.on("error", function (err) {
    logError(
      err,
      `error watchin address Mapper OM consul :${addressMapperKeyOM} `
    );
  });
};

const promoConsulCache = (consul) => {
  const promoConsulKeys =
    process.env.CONSUL_PROMO_KEYS + "_" + process.env.STYLI_ENV;
  const promoBaseURLKey = process.env.CONSUL_PROMO_BASE_URL;
  const promoBaseURLWatch = consul.watch({
    method: consul.kv.get,
    options: { key: promoBaseURLKey },
    backoffFactor: 1000,
  });

  promoBaseURLWatch.on("change", function (baseUrls, res) {
    try {
      const existingPromoBaseUrlCache = cache.get(promoBaseURLKey);
      if (baseUrls?.Value) {
        const parsedPromoBaseUrlJson = JSON.parse(baseUrls?.Value);
        const envValue = `${process.env.STYLI_ENV}`;

        const data = parsedPromoBaseUrlJson[`${envValue}`] || "";
        cache.put(promoBaseURLKey, data || existingPromoBaseUrlCache);
      }
    } catch (e) {
      logError(e, "error settting address Mapper KWcache ");
    }
  });
  promoBaseURLWatch.on("error", function (err) {
    logError(err, "error watchin promo base url consul : ");
  });

  const promoConsulKeysWatch = consul.watch({
    method: consul.kv.get,
    options: { key: promoConsulKeys },
    backoffFactor: 1000,
  });

  promoConsulKeysWatch.on("change", function (baseUrls, res) {
    try {
      const existingPromoConsulKeysCache = cache.get(`promoRedemptionUrl`);
      const existingTimeframesOptionsCache = cache.get(`timeframesOptionsRange`);
      if (baseUrls?.Value) {
        const parsedPromoConsulKeysJson = JSON.parse(baseUrls?.Value);
        const envValue = `${process.env.STYLI_ENV}`;

        // logger.info(`Consul promo keys updated - envValue: ${envValue}, baseUrlsValue: ${baseUrls?.Value}`);

        const data = parsedPromoConsulKeysJson[`promoRedemptionUrl`] || "";
        cache.put(`promoRedemptionUrl`, data || existingPromoConsulKeysCache);

        const timeframesOptions = parsedPromoConsulKeysJson[`timeframesOptionsRange`] || existingTimeframesOptionsCache;
        cache.put(`timeframesOptionsRange`, timeframesOptions);
      }
    } catch (e) {
      logError(e, "error setting promo consul keys KWcache ");
    }
  });
  promoConsulKeysWatch.on("error", function (err) {
    logError(err, "error watching promo consul keys : ");
  });
  try {
    watchBaseConfig({ consul });
  } catch (e) {
    logError(e, "Error in watching base config");
  }
};

const watchAdminBaseConfig = (consul) => {
  const envValue = `${process.env.STYLI_ENV}`;
  const baseConfigWatch = consul.watch({
    method: consul.kv.get,
    options: { key: `admin/base_${envValue}` },
    backoffFactor: 1000,
  });

  baseConfigWatch.on("change", function (baseConfig, err) {
    try {
      if (baseConfig?.Value) {
        cache.put("adminBaseConfig", JSON.parse(baseConfig?.Value));
      }
    } catch (e) {
      logError(e, "Error in Parsing Consul Admin Base config " + e.message);
    }
  });
  baseConfigWatch.on("error", function (err) {
    logError(err, `error watchin  admin/base_${envValue} consul : `);
  });
};

const watchFreeShippingConfig = (consul) => {
  const envValue = `${process.env.STYLI_ENV}`;
  const freeShippingConfigWatch = consul.watch({
    method: consul.kv.get,
    options: { key: `admin/free_shipping_config_${envValue}` },
    backoffFactor: 1000,
  });

  freeShippingConfigWatch.on("change", function (freeShippingConfig, err) {
    try {
      if (freeShippingConfig?.Value) {
        cache.put("freeShippingConfigConsul", JSON.parse(freeShippingConfig?.Value));
      }
    } catch (e) {
      logError(e, "Error in Parsing Consul Free Shipping Config " + e.message);
    }
  });

  freeShippingConfigWatch.on("error", function (err) {
    logError(err, `error watchin  admin/free_shipping_config_${envValue} consul : `);
  });
};