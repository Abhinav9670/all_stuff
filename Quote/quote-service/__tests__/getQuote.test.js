const app = require("./app");
const couchbase = require("couchbase");
const request = require("supertest");
const constant = require("./dummy/mockPayload/constant.json");
const axios = require("axios");
const payload = require("./dummy/mockPayload/requestPayload.json");
const response = require("./dummy/mockPayload/externalResponse.json");

const cache = require('memory-cache');

const {
  initcluster,
  collection
} = require("../config/couchbase.js");

describe("get quote POST/rest/quote/auth/v6/get", () => {
  beforeAll( async() => {
    await initcluster();
    const basicDataColl = await collection();
    await basicDataColl.upsert(`quote_1684234744639834`, constant.addQuoteObj, { expiry: Number(process.env.COUCHBASE_QUOTE_DOC_EXPIRY) });
    await basicDataColl.upsert(`quote_1684234744639835`, constant.addQuoteObj2, { expiry: Number(process.env.COUCHBASE_QUOTE_DOC_EXPIRY) });
  });
  afterAll(() => {
    jest.resetAllMocks();
  });
  describe("quote available", () => {
    it("should return a 200 status and response", async () => {
      axios.post = async (url, req) => {
        if (url.endsWith("/api/detail")) {
          return response.vmResQuote_1684234744639834;
        }
        if (url.endsWith("/api/v1/checkRedeemCoinOnOrder")) {
          return response.easResponseCoinNotApplied;
        }
        if (url.endsWith("customer/oms/details")) {
          return response.getCustomerInfo;
        }
        if (url.endsWith("/rest/order/firstorder")) {
          return response.getOrderList;
        }
        if (url.endsWith("/coupon/validate")) {
          return response.promoValidateRes;
        }
        if (url.endsWith("/api/inventory/storefront/atp")) {
          const resData = JSON.parse(JSON.stringify(response.inventoryRes));
          const reqSkus = req && req?.skus ? req?.skus : [];
          const skuQty = [];
          reqSkus.forEach((sku) => {
            skuQty.push({
              sku: sku,
              value: "99999.0000",
            });
          });
          resData.data.response =
            skuQty.length > 0 ? skuQty : resData.data.response;
          return resData;
        }
      };
      // await new Promise((r) => setTimeout(r, 2000));
      const { statusCode } = await request(app)
        .post("/rest/quote/auth/v6/get")
        .set(constant.commonHeaders)
        .send(payload.getPayload);
      expect(statusCode).toBe(200);
    });
  });
  describe("quote available with coupon applied", () => {
    it("should return a 200 status and response", async () => {
      axios.post = async (url, req) => {
        if (url.endsWith("/api/detail")) {
          return response.vmResQuote_1686569333347279;
        }
        if (url.endsWith("customer/oms/details")) {
          return response.getCustomerInfo3910693;
        }
        if (url.endsWith("/rest/order/firstorder")) {
          return response.getOrderList;
        }
        if (url.endsWith("/coupon/validate")) {
          return response.promoValidateRes_1686569333347279;
        }
        if (url.endsWith("/api/inventory/storefront/atp")) {
          const resData = JSON.parse(JSON.stringify(response.inventoryRes));
          const reqSkus = req && req?.skus ? req?.skus : [];
          const skuQty = [];
          reqSkus.forEach((sku) => {
            skuQty.push({
              sku: sku,
              value: "99999.0000",
            });
          });
          resData.data.response =
            skuQty.length > 0 ? skuQty : resData.data.response;
          return resData;
        }
      };

      const { statusCode } = await request(app)
        .post("/rest/quote/auth/v6/get")
        .set(constant.commonHeaders)
        .send(payload.getPayloadCoupon);
      expect(statusCode).toBe(200);
    });
  });
  describe("webhookFreeShippingAppiled", () => {
    it("should return a 200 status and response", async () => {
      axios.post = async (url, req) => {
        if (url.endsWith("/api/detail")) {
          return response.vmResQuote_1684234744639834;
        }
        if (url.endsWith("/api/v1/checkRedeemCoinOnOrder")) {
          return response.easResponseCoinNotApplied;
        }
        if (url.endsWith("customer/oms/details")) {
          return response.getCustomerInfo3910693;
        }
        if (url.endsWith("/rest/order/firstorder")) {
          return response.getOrderList;
        }
        if (url.endsWith("/coupon/validate")) {
          return response.promoValidateRes_1684234744639833;
        }
        if (url.endsWith("/api/inventory/storefront/atp")) {
          const resData = JSON.parse(JSON.stringify(response.inventoryRes));
          const reqSkus = req && req?.skus ? req?.skus : [];
          const skuQty = [];
          reqSkus.forEach((sku) => {
            skuQty.push({
              sku: sku,
              value: "99999.0000",
            });
          });
          resData.data.response =
            skuQty.length > 0 ? skuQty : resData.data.response;
          return resData;
        }
      };
      const { statusCode } = await request(app)
        .post("/rest/quote/auth/v6/get")
        .set(constant.commonHeaders)
        .send(payload.getPayloadFreeShip);
      expect(statusCode).toBe(200);
    });
  });
  describe("quote available for coin applied", () => {
    it("should return a 200 status and response", async () => {
      axios.post = async (url, req) => {
        if (url.endsWith("/api/detail")) {
          return response.vmResQuote_1684234744639836;
        }
        if (url.endsWith("/api/v1/checkRedeemCoinOnOrder")) {
          return response.easResponseCoin;
        }
        if (url.endsWith("customer/oms/details")) {
          return response.getCustomerInfo;
        }
        if (url.endsWith("/rest/order/firstorder")) {
          return response.getOrderList;
        }
        if (url.endsWith("/coupon/validate")) {
          return response.promoValidateRes;
        }
        if (url.endsWith("/api/inventory/storefront/atp")) {
          const resData = JSON.parse(JSON.stringify(response.inventoryRes));
          const reqSkus = req && req?.skus ? req?.skus : [];
          const skuQty = [];
          reqSkus.forEach((sku) => {
            skuQty.push({
              sku: sku,
              value: "99999.0000",
            });
          });
          resData.data.response =
            skuQty.length > 0 ? skuQty : resData.data.response;
          return resData;
        }
      };
      // fetchPromoResponse.mockReturnValue(constant.promoResponseCouponApplied);
      const { statusCode } = await request(app)
        .post("/rest/quote/auth/v6/get")
        .set(constant.commonHeaders)
        .send(payload.getPayloadCoin);
      expect(statusCode).toBe(200);
    });
  });

  describe("check totalShukranBurn Flag in Quote Products", () => {
    it("should not have totalShukranBurn flag value for all", async () => {
      const { statusCode, body: { response: {quoteProducts} } } = await request(app)
      .post("/rest/quote/auth/v6/get")
      .set(constant.commonHeaders)
      .send(payload.getPayload);

      expect(statusCode).toBe(200);
      const allHaveShukranBurn = quoteProducts.every(product =>
        product.not.hasOwnProperty('totalShukranBurn') || product.hasOwnProperty('totalShukranBurn', 0)
      );
      expect(allHaveShukranBurn).toBe(true);
    });
  });
  
  describe("check isSale Flag in Quote Products", () => {
    it("should have isSale flag value as 0 for all", async () => {
      const { statusCode, body: { response: {quoteProducts} } } = await request(app)
      .post("/rest/quote/auth/v6/get")
      .set(constant.commonHeaders)
      .send(payload.getPayload);

      const allHaveIsSale = quoteProducts.every(product =>
        product.hasOwnProperty('isSale') && product.isSale === 0
      );
      expect(statusCode).toBe(200);
      expect(allHaveIsSale).toBe(true);
    })

    it("should have isSale flag as 1 for all", async () => {
      const { statusCode, body: { response: {quoteProducts} } } = await request(app)
      .post("/rest/quote/auth/v6/get")
      .set(constant.commonHeaders)
      .send(payload.getPayload2);

      const allHaveIsSale = quoteProducts.every(product =>
        product.hasOwnProperty('isSale') && product.isSale === 1
      );
      expect(statusCode).toBe(200);
      expect(allHaveIsSale).toBe(true);    
    });
  });

  describe("check shukran related Flags in Quote Products", () => {
    it("should have shukran related flag for all", async () => {
      const { statusCode, body: { response: {quoteProducts} } } = await request(app)
      .post("/rest/quote/auth/v6/get")
      .set(constant.commonHeaders)
      .send(payload.getPayload);

      expect(statusCode).toBe(200);
      const allHaveShukranBurn = quoteProducts.every(product =>
        product.hasOwnProperty('shukranBurn') && product.hasOwnProperty('shukranBurnInCurrency') && product.hasOwnProperty('shukranBurnInBaseCurrency')
      );
      expect(allHaveShukranBurn).toBe(true);
    });
    it("check applied and available shukran cash value are equal", async () => {
      const { statusCode, body: { response } } = await request(app)
      .post("/rest/quote/auth/v6/get")
      .set(constant.commonHeaders)
      .send(payload.getPayload);
      
      expect(statusCode).toBe(200);
      expect(response.appliedShukranPoint).toEqual(response.shukranAvailablePoint);
    });
  });

  describe("check shukranEarn Flag in Quote Products", () => {
    it("should have shukranEarn flag for all", async () => {
      const { statusCode, body: { response: {quoteProducts} } } = await request(app)
      .post("/rest/quote/auth/v6/get")
      .set(constant.commonHeaders)
      .send(payload.getPayload);

      expect(statusCode).toBe(200);
      const allHaveShukranEarn = quoteProducts.every(product =>
        product.hasOwnProperty('shukranEarn')
      );
      expect(allHaveShukranEarn).toBe(true);
    });
  });

  describe("check shukran related Flags in Quote root level", () => {
    it("should have shukran related flags in root level", async () => {
      const { statusCode, body: { response } } = await request(app)
      .post("/rest/quote/auth/v6/get")
      .set(constant.commonHeaders)
      .send(payload.getPayload);

      expect(statusCode).toBe(200);
      expect(response).hasOwnProperty('totalShukranBurn');
      expect(response).hasOwnProperty('totalShukranBurnValueInCurrency');
      expect(response).hasOwnProperty('totalShukranBurnValueInBaseCurrency');
      expect(response).hasOwnProperty('appliedShukranPoint');
      expect(response).hasOwnProperty('appliedShukranCashValue');
      expect(response).hasOwnProperty('shukranAvailablePoint');
      expect(response).hasOwnProperty('shukranAvailableCashValue');
    });
  });

  describe("check totalShukranEarn Flag in Quote", () => {
    it("should have totalShukranEarn flag in root level", async () => {
      const { statusCode, body: { response } } = await request(app)
      .post("/rest/quote/auth/v6/get")
      .set(constant.commonHeaders)
      .send(payload.getPayload);

      
      expect(statusCode).toBe(200);
      expect(response).hasOwnProperty('totalShukranEarn');
    });
  });

  describe("check styliCashBurn functionality with statusCoinApllied", () => {
    it("should have styliCashBurn fields in quoteProducts when statusCoinApllied is 1", async () => {
      const { statusCode, body: { response } } = await request(app)
        .post("/rest/quote/auth/v6/get")
        .set(constant.commonHeaders)
        .send(payload.getPayloadCoin);

      expect(statusCode).toBe(200);
      
      // Check that coinDiscountData exists in the response
      expect(response).hasOwnProperty('coinDiscountData');
      expect(response.coinDiscountData).toBeDefined();
      
      // Check that all products have styliCashBurn fields
      const allHaveStyliCashBurn = response.quoteProducts.every(product =>
        product.hasOwnProperty('styliCashBurn') && 
        product.hasOwnProperty('styliCashBurnInCurrency') && 
        product.hasOwnProperty('styliCashBurnInBaseCurrency')
      );
      expect(allHaveStyliCashBurn).toBe(true);
      
      // Verify that styliCashBurn values are numbers (not null/undefined)
      const allStyliCashBurnValuesValid = response.quoteProducts.every(product =>
        typeof product.styliCashBurn === 'number' &&
        typeof product.styliCashBurnInCurrency === 'number' &&
        typeof product.styliCashBurnInBaseCurrency === 'number'
      );
      expect(allStyliCashBurnValuesValid).toBe(true);
    });

    it("should have styliCashBurn fields as 0 when statusCoinApllied is 0", async () => {
      axios.post = async (url, req) => {
        if (url.endsWith("/api/detail")) {
          return response.vmResQuote_1684234744639834;
        }
        if (url.endsWith("/api/v1/checkRedeemCoinOnOrder")) {
          return response.easResponseCoinNotApplied;
        }
        if (url.endsWith("customer/oms/details")) {
          return response.getCustomerInfo;
        }
        if (url.endsWith("/rest/order/firstorder")) {
          return response.getOrderList;
        }
        if (url.endsWith("/coupon/validate")) {
          return response.promoValidateRes;
        }
        if (url.endsWith("/api/inventory/storefront/atp")) {
          const resData = JSON.parse(JSON.stringify(response.inventoryRes));
          const reqSkus = req && req?.skus ? req?.skus : [];
          const skuQty = [];
          reqSkus.forEach((sku) => {
            skuQty.push({
              sku: sku,
              value: "99999.0000",
            });
          });
          resData.data.response =
            skuQty.length > 0 ? skuQty : resData.data.response;
          return resData;
        }
      };

      const { statusCode, body: { response: {quoteProducts} } } = await request(app)
        .post("/rest/quote/auth/v6/get")
        .set(constant.commonHeaders)
        .send(payload.getPayload);

      expect(statusCode).toBe(200);
      
      // Check that all products have styliCashBurn fields set to 0
      const allStyliCashBurnZero = quoteProducts.every(product =>
        product.hasOwnProperty('styliCashBurn') && product.styliCashBurn === 0 &&
        product.hasOwnProperty('styliCashBurnInCurrency') && product.styliCashBurnInCurrency === 0 &&
        product.hasOwnProperty('styliCashBurnInBaseCurrency') && product.styliCashBurnInBaseCurrency === 0
      );
      expect(allStyliCashBurnZero).toBe(true);
    });
  });
});