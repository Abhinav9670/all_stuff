/* eslint-disable max-lines-per-function */
/* eslint-disable no-undef */
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
console.log = jest.fn();
console.error = jest.fn();
const app = require('../../src/app');
const RUN_CONFIG = require('../run.config.json');
const HEADER_TOKEN = RUN_CONFIG['X-HEADER-TOKEN'];
jest.mock('axios');
jest.mock('firebase-admin');
jest.setTimeout(80000);

describe('misc_api', () => {
  beforeAll(() => {
    global.logError = jest.fn(() => ({}));
    global.baseConfig = { permissionTargets: ['order', 'customer'] };
  });
  beforeEach(() => { });

  describe('misc_routes', () => {
    it('should get permission targets', async () => {
      const response = await request(app)
        .get('/v1/misc/gerPermissionTargets')
        .set({ authorization: RUN_CONFIG.JWT_TOKEN, 'X-Header-Token': HEADER_TOKEN });
      expect(response.status).toBe(200);
      expect(response.body.status).toBe(true);
      expect(Array.isArray(response.body.response)).toBe(true);
    });

    it('should get health check', async () => {
      const response = await request(app)
        .get('/v1/misc/health-check')
        .set({ authorization: RUN_CONFIG.JWT_TOKEN, 'X-Header-Token': HEADER_TOKEN });
      expect(response.status).toBe(200);
    });

    it('should handle downloadFileFromGCP error', async () => {
      // Send a request with missing/invalid body to trigger error
      const response = await request(app)
        .post('/v1/misc/download/file')
        .send({})
        .set({ authorization: RUN_CONFIG.JWT_TOKEN, 'X-Header-Token': HEADER_TOKEN });
      expect([400, 500]).toContain(response.status);
    });

    it('should download file from GCP (success path, covers mkdir and download)', async () => {
      // Arrange mocks for fs and storage
      const fs = require('fs');
      const storage = require('../../src/config/googleStorage').storage;
      const testFile = 'test/file.txt';
      const testDir = `${__dirname}/../../downloads`;
      const testDest = `${testDir}/123456_file.txt`;

      jest.spyOn(fs, 'existsSync').mockReturnValue(false);
      jest.spyOn(fs, 'mkdirSync').mockImplementation(() => { });
      // Mock Date.now to return a fixed value
      jest.spyOn(Date, 'now').mockReturnValue(123456);
      // Mock storage.bucket().file().download
      const downloadMock = jest.fn().mockResolvedValue();
      const fileMock = jest.fn(() => ({ download: downloadMock }));
      const bucketMock = jest.fn(() => ({ file: fileMock }));
      storage.bucket = bucketMock;
      // Mock res.download by patching express response prototype
      const resDownload = jest.spyOn(require('express').response, 'download').mockImplementation(function (dest) {
        this.status(200).json({ status: true, file: dest });
      });

      const response = await request(app)
        .post('/v1/misc/download/file')
        .send({ file: testFile })
        .set({ authorization: RUN_CONFIG.JWT_TOKEN, 'X-Header-Token': HEADER_TOKEN });

      expect(fs.existsSync).toHaveBeenCalledWith(expect.stringContaining('downloads'));
      expect(fs.mkdirSync).toHaveBeenCalled();
      expect(storage.bucket).toHaveBeenCalled();
      expect(downloadMock).toHaveBeenCalled();
      expect(resDownload).toHaveBeenCalled();
      expect(response.status).toBe(200);
      expect(response.body.status).toBe(true);
      expect(response.body.file).toContain('downloads/123456_file.txt');

      // Restore mocks
      jest.restoreAllMocks();
    });
  });
});
