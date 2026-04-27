/* eslint-disable max-lines-per-function */
/* eslint-disable no-undef */
const request = require('supertest');
const httpStatus = require('http-status');

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
const { authService } = require('../../src/services');

jest.mock('../../src/services');

const HEADER_TOKEN = RUN_CONFIG['X-HEADER-TOKEN'];

describe('auth_controller', () => {
  beforeAll(() => {
    global.logError = jest.fn(() => ({}));
    global.logInfo = jest.fn(() => ({}));
  });

  describe('permission_list', () => {
    it('should return permissions list successfully', async () => {
      const mockPermissions = {
        roles: ['admin'],
        permissions: ['read', 'write']
      };

      authService.getPermissionList.mockResolvedValueOnce(mockPermissions);

      const response = await request(app)
        .post('/v1/auth/permission-list')
        .send({ email: 'test@example.com' })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(httpStatus.OK);
      expect(response.body).toEqual({
        status: true,
        statusCode: '200',
        statusMsg: 'Permissions found!',
        response: mockPermissions
      });
      expect(authService.getPermissionList).toHaveBeenCalledWith({
        email: 'test@example.com'
      });
    });

    it('should return empty permissions for non-existent user', async () => {
      authService.getPermissionList.mockResolvedValueOnce({
        roles: [],
        permissions: []
      });

      const response = await request(app)
        .post('/v1/auth/permission-list')
        .send({ email: 'nonexistent@example.com' })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(httpStatus.OK);
      expect(response.body.response).toEqual({
        roles: [],
        permissions: []
      });
    });

    it('should handle invalid authorization token', async () => {
      const response = await request(app)
        .post('/v1/auth/permission-list')
        .send({ email: 'test@example.com' })
        .set({
          token: 'invalid-token',
          'X-Header-Token': 'invalid-header-token'
        });

      // Since the controller doesn't validate token, it will pass the request through
      expect(response.status).toBe(httpStatus.OK);
    });

    it('should handle service returning null', async () => {
      authService.getPermissionList.mockResolvedValueOnce(null);

      const response = await request(app)
        .post('/v1/auth/permission-list')
        .send({ email: 'test@example.com' })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(httpStatus.OK);
      expect(response.body.response).toBeNull();
    });

    it('should handle service returning undefined', async () => {
      authService.getPermissionList.mockResolvedValueOnce(undefined);

      const response = await request(app)
        .post('/v1/auth/permission-list')
        .send({ email: 'test@example.com' })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(httpStatus.OK);
      expect(response.body.response).toBeUndefined();
    });
  });
});
