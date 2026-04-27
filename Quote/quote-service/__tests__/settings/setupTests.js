const app = require('../app');
const cache = require('memory-cache');
const constant = require('../dummy/mockPayload/constant.json');
const baseConfig = require('../dummy/baseConfig.json');
const appConfig = require('../dummy/appConfig.json');
const customerAuthConfig = require('../dummy/customerAuthConfig.json');
const paymentMethods = require('../dummy/paymentMethods.json');
const addressMapperSA = require('../dummy/addressMapperSA.json');
const addressMapperAE = require('../dummy/addressMapperAE.json');
const addressMapperKW = require('../dummy/addressMapperKW.json');
const addressMapperQA = require('../dummy/addressMapperQA.json');
const addressMapperBH = require('../dummy/addressMapperBH.json');
const addressMapperOM = require('../dummy/addressMapperOM.json');
const promoBaseUrl = require('../dummy/promoBaseUrl.json');
const promoKeyValue = require('../dummy/promoConsulValue.json');
require('../../node_modules/mysql2/node_modules/iconv-lite/lib').encodingExists('foo');

process.env.CONSUL_KEY='appConfig';
process.env.CONSUL_PROMO_BASE_URL='promoBaseUrl';
process.env.CONSUL_ADDRESS_MAPPER_KEY_SA='addressMapper_sa_test';
process.env.CONSUL_ADDRESS_MAPPER_KEY_AE='addressMapper_ae_test';
process.env.CONSUL_ADDRESS_MAPPER_KEY_KW='addressMapper_kw_test';
process.env.CONSUL_ADDRESS_MAPPER_KEY_BH='addressMapper_bh_test';
process.env.CONSUL_ADDRESS_MAPPER_KEY_QA='addressMapper_qa_test';
process.env.CONSUL_ADDRESS_MAPPER_KEY_OM='addressMapper_om_test';
process.env.GCP_ADDRESS_MAPPER_KEY_IN='gcp_addressMapper_in';
process.env.CONSUL_CUSTOMER_AUTH_CONFIG='customer_auth_config';
process.env.CONSUL_PAYMENT_METHODS='paymentMethods';
process.env.CONSUL_PROMO_REDEEM='promoRedemptionUrl';
const envValue = `${process.env.STYLI_ENV}`;
console.log = jest.fn();

beforeAll(async () => {
  try {
    cache.put("baseConfig", baseConfig);
    const customerAuthConfigCache = cache.get(process.env.CONSUL_CUSTOMER_AUTH_CONFIG);
    cache.put(process.env.CONSUL_CUSTOMER_AUTH_CONFIG, customerAuthConfig || customerAuthConfigCache);

    const existingCache = cache.get(process.env.CONSUL_KEY);
    cache.put(process.env.CONSUL_KEY, appConfig || existingCache);

    cache.put(process.env.CONSUL_PAYMENT_METHODS, paymentMethods);

    const existingAddressCache = cache.get(process.env.CONSUL_ADDRESS_MAPPER_KEY_SA);
        const parsedAddressJson = addressMapperSA;
        const saData = parsedAddressJson?.provinces
          ? parsedAddressJson?.provinces.SA
          : {};
        cache.put(process.env.CONSUL_ADDRESS_MAPPER_KEY_SA, saData || existingAddressCache);

    const existingAddressCacheAE = cache.get(process.env.CONSUL_ADDRESS_MAPPER_KEY_AE);
        const saDataAE = addressMapperAE?.provinces
          ? addressMapperAE?.provinces.AE
          : {};
        cache.put(process.env.CONSUL_ADDRESS_MAPPER_KEY_AE, saDataAE || existingAddressCacheAE);

    const existingAddressCacheKW = cache.get(process.env.CONSUL_ADDRESS_MAPPER_KEY_KW);
        const saDataKW = addressMapperKW?.provinces
          ? addressMapperKW?.provinces.KW
          : {};
        cache.put(process.env.CONSUL_ADDRESS_MAPPER_KEY_KW, saDataKW || existingAddressCacheKW);

    const existingAddressCacheBH = cache.get(process.env.CONSUL_ADDRESS_MAPPER_KEY_BH);
        const saDataBH = addressMapperAE?.provinces
          ? addressMapperBH?.provinces.BH
          : {};
        cache.put(process.env.CONSUL_ADDRESS_MAPPER_KEY_BH, saDataBH || existingAddressCacheBH);

    const existingAddressCacheQA = cache.get(process.env.CONSUL_ADDRESS_MAPPER_KEY_QA);
        const saDataQA = addressMapperQA?.provinces
          ? addressMapperQA?.provinces.QA
          : {};
        cache.put(process.env.CONSUL_ADDRESS_MAPPER_KEY_QA, saDataQA || existingAddressCacheQA);
    
    const existingAddressCacheOM = cache.get(process.env.CONSUL_ADDRESS_MAPPER_KEY_OM);
        const saDataOM = addressMapperOM?.provinces
          ? addressMapperOM?.provinces.OM
          : {};
        cache.put(process.env.CONSUL_ADDRESS_MAPPER_KEY_OM, saDataOM || existingAddressCacheOM);
    
    const existingPromoBaseUrlCache = cache.get(process.env.CONSUL_PROMO_BASE_UR);
        const data = promoBaseUrl[`${envValue}`] || "";
        cache.put(process.env.CONSUL_PROMO_BASE_UR, data || existingPromoBaseUrlCache);

    const existingPromoConsulKeysCache = cache.get(process.env.CONSUL_PROMO_REDEEM);
          const dataRedeem = promoKeyValue[process.env.CONSUL_PROMO_REDEEM] || "";
          cache.put(process.env.CONSUL_PROMO_REDEEM, dataRedeem || existingPromoConsulKeysCache);
      
  } catch (error) {
    console.log(error);
  }
});

afterAll(async () => {
  try {
    // await redisServer.stop();
    //  await pool.end();
  } catch (error) {
    console.log(error);
  }
});
