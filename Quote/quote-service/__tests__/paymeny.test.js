const app = require('./app');
const request = require('supertest');
const constant = require('./dummy/mockPayload/constant.json');
const payload = require('./dummy/mockPayload/requestPayload.json');
const { collection,customerCollection,addressCollection } = require('../config/couchbase.js');

describe('payment /rest/quote/auth/v5/payment', () => {
  beforeAll( async() => {
    await collection.upsert(`quote_1681477874120535`, constant.quoteForDisable, { expiry: Number(process.env.COUCHBASE_QUOTE_DOC_EXPIRY) });
    await customerCollection.upsert('fRules_1686277229718950',constant.fRuleCouchbase);
    await addressCollection.upsert('1',constant.adrsmprCouchbase);
  });
  afterAll(() => {
    jest.resetAllMocks();
  });
  describe('quote available', () => {
    it('should return a 200 status and response', async () => {
      const { body, statusCode } = await request(app).post(
        '/rest/quote/auth/v5/payment'
      ).set(constant.commonHeaders).send(payload.paymentPayload);
      expect(statusCode).toBe(200);
      expect(body.status).toBe(true);
      expect(body.statusCode).toBe('200');
    });
  });
});
