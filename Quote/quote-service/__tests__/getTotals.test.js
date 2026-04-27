const app = require('./app');
const request = require('supertest');
const constant = require('./dummy/mockPayload/constant.json');
const payload = require('./dummy/mockPayload/requestPayload.json');
const axios = require('axios');
const response = require('./dummy/mockPayload/externalResponse.json');
const {
  initcluster,
  collection,
  customerCollection,
  addressCollection
} = require("../config/couchbase.js");
const { pool } = require('../helpers/mysqlPool');

describe('get quote POST/rest/quote/auth/v6/get/totals', () => {
  beforeAll( async() => {
    await initcluster();
    const basicDataColl = await collection();
    await basicDataColl.upsert(`quote_1684234744639834`, constant.addQuoteObj, { expiry: Number(process.env.COUCHBASE_QUOTE_DOC_EXPIRY) });

    constant.addQuoteObj.totalBurnCashValue = 0;
    constant.addQuoteObj.customerId = "3910695";
    await basicDataColl.upsert(`quote_1684234744639835`, constant.addQuoteObj, { expiry: Number(process.env.COUCHBASE_QUOTE_DOC_EXPIRY) });
    
    const custColl = await customerCollection();
    custColl.upsert('fRules_1686277229718950',constant.fRuleCouchbase);
    
    const addrColl = await addressCollection();
    addrColl.upsert('1',constant.adrsmprCouchbase);
  });
  afterAll(() => {
    jest.resetAllMocks();
    pool.end();
  });
  for (const eachReq of payload.getTotalsPayload) {
  describe('quote available', () => {
    it('should return a 200 status and response', async () => {   
      axios.get = async (url) => {
        console.log('addrssURL',url);
        if(url.endsWith('/api/address/search/city/1')){
          return response.getCityDetail;
        }
      };
      axios.post = async (url,req) => {
        if(url.endsWith('/api/detail')){
             return response.vmResQuote_1684234744639834;
        }
        if(url.endsWith('/api/v1/checkRedeemCoinOnOrder')){
          return response.easResponseCoinNotApplied;
        }
        if(url.endsWith('customer/oms/details')){
          return response.getCustomerInfo;
        }
        if(url.endsWith('/rest/order/firstorder')){
          return response.getOrderList;
        }
        if(url.endsWith('/coupon/validate')){
          return response.promoValidateRes;
        }
        if(url.endsWith('/v1/payment/options')){
          return response.paymentRes;
        }
        if(url.endsWith('/api/inventory/storefront/atp')){
          const resData = JSON.parse(JSON.stringify(response.inventoryRes));
           const reqSkus= req && req?.skus ? req?.skus : [];
           const skuQty = [];
           reqSkus.forEach(sku => {
            skuQty.push({
              sku: sku,
              value: "99999.0000"
          });
          });
          resData.data.response = skuQty.length > 0 ? skuQty : resData.data.response;
          return resData;
        }
      };
      const { body, statusCode } = await request(app).post(
        '/rest/quote/auth/v6/get/totals'
      ).set(constant.commonHeaders).send(eachReq);
      expect(statusCode).toBe(200);
      expect(body.status).toBe(true);
      expect(body.statusCode).toBe('200');
      expect(typeof body.response === 'object').toBe(true);
    });
  });
}

describe('check totalShukranEarn property', () => {
  it('should have a totalShukranEarn property', async () => {    
    const { statusCode, body } = await request(app)
    .post("/rest/quote/auth/v6/get/totals")
    .set(constant.commonHeaders)
    .send(payload.getTotalsPayload[0]);

    expect(statusCode).toBe(200);

    expect(body?.status).toBe(true);
    expect(body?.statusCode).toBe('200');

    expect(body?.response).toHaveProperty('totalShukranEarn');
  });

  it('should not have a totalShukranEarn property', async () => {    
    const payLoadCust = payload.getTotalsPayload[0];
    payLoadCust.customerId = "";
    const { statusCode, body } = await request(app)
    .post("/rest/quote/auth/v6/get/totals")
    .set(constant.commonHeaders)
    .send(payLoadCust);

    expect(statusCode).toBe(200);

    expect(body?.status).toBe(false);
    expect(body?.statusCode).toBe('202');

    if (body?.response) {
      expect(body.response).not.toHaveProperty('totalShukranEarn');
    } else {
      expect(body).not.toHaveProperty('response.totalShukranEarn');
    }
  });
});

});
