const app = require("./app");
const couchbase = require("couchbase");
const request = require("supertest");
const constant = require("./dummy/mockPayload/constant.json");
const axios = require("axios");
const payload = require("./dummy/mockPayload/requestPayload.json");
const response = require("./dummy/mockPayload/externalResponse.json");

describe("get quote POST/rest/quote/auth/v6/get", () => {
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
        .set(constant.headerAccount2)
        .send(payload.getPayloadCoin);
      expect(statusCode).toBe(200);
    });
  });
});
