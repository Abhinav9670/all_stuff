const app = require('./app');
const request = require('supertest');

describe('health Check ', () => {
      it('GET/rest/quote/health-check should return a 200 status ', async () => {
        const { body, statusCode } = await request(app).get(
          '/rest/quote/health-check'
        );
        expect(statusCode).toBe(200);
        expect(body.status).toBe(true);
        expect(body.statusCode).toBe("200");
      });

      it('GET/rest/quote/couch-health-check should return a 200 status ', async () => {
        const { body, statusCode } = await request(app).get(
          '/rest/quote/couch-health-check'
        );
        expect(statusCode).toBe(200);
      });
  });
  