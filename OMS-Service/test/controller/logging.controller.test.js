const request = require('supertest');
jest.mock('redis', () => {
  const mockRedis = {
    connect: jest.fn().mockResolvedValue(true),
    on: jest.fn(),
    quit: jest.fn(),
    get: jest.fn().mockResolvedValue(null),
    set: jest.fn().mockResolvedValue('OK')
  };
  return {
    createClient: jest.fn(() => mockRedis)
  };
});

jest.mock('playwright', () => ({
  chromium: {
    launch: jest.fn().mockResolvedValue({
      newPage: jest.fn().mockResolvedValue({
        setContent: jest.fn(),
        pdf: jest.fn(),
        close: jest.fn()
      }),
      close: jest.fn()
    })
  }
}));

const app = require('../../src/app');
const loggingService = require('../../src/services/logging.service');
const { fetchAdminLogs } = require('../../src/helpers/logging');

jest.mock('../../src/services/logging.service');
jest.mock('../../src/helpers/logging');

describe('Log Controller', () => {
  describe('GET /logs/inventory/:sku/:inventory', () => {
    it('should return inventory logs', async () => {
      const mockLogs = [{ id: 1, sku: 'ABC123', message: 'Stock updated' }];
      loggingService.findInventoryLogBySku.mockResolvedValue(mockLogs);

      const response = await request(app).get(
        '/v1/logging/inventory/ABC123/INV1'
      );

      expect(response.status).toBe(200);
      expect(response.body).toEqual(mockLogs);
    });
  });

  describe('POST /logs/admin', () => {
    it('should return admin logs based on filters', async () => {
      const mockLogData = [{ id: 1, action: 'LOGIN', user: 'admin' }];
      fetchAdminLogs.mockResolvedValue(mockLogData);

      const requestBody = {
        filters: { user: 'admin' },
        offset: 0,
        pagesize: 10,
        query: 'LOGIN'
      };

      const response = await request(app)
        .post('/v1/logging/adminlogs')
        .send(requestBody);

      expect(response.status).toBe(200);
      expect(response.body).toEqual({ data: mockLogData, status: true });
      expect(fetchAdminLogs).toHaveBeenCalledWith(requestBody);
    });
  });
});