// Tests for src/services/auth.service.js
// Follows the structure and mocking style of existing tests

process.env = {
    ...process.env,
    MONGODB_URL: 'mongodb://localhost:27017/test',
    NODE_ENV: 'test',
};

const httpStatus = require('http-status');
const authService = require('../../../src/services/auth.service');
const tokenService = require('../../../src/services/token.service');
const userService = require('../../../src/services/user.service');
const Token = require('../../../src/models/token.model');
const ApiError = require('../../../src/utils/ApiError');
const mongoUtil = require('../../../src/utils/mongoInit');

jest.mock('../../../src/services/token.service');
jest.mock('../../../src/services/user.service');
jest.mock('../../../src/models/token.model');

global.logError = jest.fn();
global.baseConfig = { superadmins: ['admin@example.com'] };

describe('auth.service', () => {
    afterEach(() => { jest.clearAllMocks(); });

    describe('loginUserWithEmailAndPassword', () => {
        it('should login with correct credentials', async () => {
            const user = { isPasswordMatch: jest.fn().mockResolvedValue(true) };
            userService.getUserByEmail.mockResolvedValue(user);
            const result = await authService.loginUserWithEmailAndPassword('a@b.com', 'pass');
            expect(result).toBe(user);
        });
        it('should throw error with wrong credentials', async () => {
            userService.getUserByEmail.mockResolvedValue(null);
            await expect(authService.loginUserWithEmailAndPassword('a@b.com', 'bad')).rejects.toThrow(ApiError);
        });
    });

    describe('logout', () => {
        it('should remove refresh token', async () => {
            const remove = jest.fn();
            Token.findOne.mockResolvedValue({ remove });
            await authService.logout('token');
            expect(remove).toHaveBeenCalled();
        });
        it('should throw if token not found', async () => {
            Token.findOne.mockResolvedValue(null);
            await expect(authService.logout('token')).rejects.toThrow(ApiError);
        });
    });

    describe('refreshAuth', () => {
        it('should refresh tokens for valid refresh token', async () => {
            const remove = jest.fn();
            tokenService.verifyToken.mockResolvedValue({ user: 'id', remove });
            userService.getUserById.mockResolvedValue({ id: 'id' });
            tokenService.generateAuthTokens.mockResolvedValue({ access: 'a', refresh: 'r' });
            const result = await authService.refreshAuth('token');
            expect(result).toEqual({ access: 'a', refresh: 'r' });
            expect(remove).toHaveBeenCalled();
        });
        it('should throw if user not found', async () => {
            tokenService.verifyToken.mockResolvedValue({ user: 'id', remove: jest.fn() });
            userService.getUserById.mockResolvedValue(null);
            await expect(authService.refreshAuth('token')).rejects.toThrow(ApiError);
        });
        it('should throw on error', async () => {
            tokenService.verifyToken.mockRejectedValue(new Error('fail'));
            await expect(authService.refreshAuth('token')).rejects.toThrow(ApiError);
        });
    });

    describe('resetPassword', () => {
        it('should reset password for valid token', async () => {
            tokenService.verifyToken.mockResolvedValue({ user: 'id' });
            userService.getUserById.mockResolvedValue({ id: 'id' });
            Token.deleteMany.mockResolvedValue();
            userService.updateUserById.mockResolvedValue();
            await expect(authService.resetPassword('token', 'newpass')).resolves.toBeUndefined();
        });
        it('should throw if user not found', async () => {
            tokenService.verifyToken.mockResolvedValue({ user: 'id' });
            userService.getUserById.mockResolvedValue(null);
            await expect(authService.resetPassword('token', 'newpass')).rejects.toThrow(ApiError);
        });
        it('should throw on error', async () => {
            tokenService.verifyToken.mockRejectedValue(new Error('fail'));
            await expect(authService.resetPassword('token', 'newpass')).rejects.toThrow(ApiError);
        });
    });

    describe('getPermissionList', () => {
        it('should return manage all for superadmin', async () => {
            const result = await authService.getPermissionList({ email: 'admin@example.com' });
            expect(result).toEqual([{ type: 'manage', target: 'all' }]);
        });
        it('should return permissions for normal user', async () => {
            const db = { collection: jest.fn() };
            const userData = { permission_groups: [{ value: 1 }], email: 'user@x.com' };
            const groupData = [{ permissions: [{ type: 'read', target: 'order' }] }];
            db.collection.mockImplementation(name => {
                if (name === 'users') return { findOne: jest.fn().mockResolvedValue(userData) };
                if (name === 'groups') return { find: jest.fn().mockReturnValue({ forEach: cb => groupData.forEach(cb) }) };
            });
            if (global.mockGetDbFn) {
              global.mockGetDbFn.mockReturnValue(db);
            } else {
              mongoUtil.getDb = jest.fn(() => db);
            }
            const result = await authService.getPermissionList({ email: 'user@x.com' });
            expect(result).toEqual([{ type: 'read', target: 'order' }]);
        });
        it('should handle db errors gracefully', async () => {
            const db = { collection: jest.fn().mockReturnValue({ findOne: jest.fn().mockRejectedValue(new Error('fail')) }) };
            if (global.mockGetDbFn) {
              global.mockGetDbFn.mockReturnValue(db);
            } else {
              mongoUtil.getDb = jest.fn(() => db);
            }
            const result = await authService.getPermissionList({ email: 'fail@x.com' });
            expect(result).toEqual([]);
            expect(global.logError).toHaveBeenCalled();
        });
    });
});
