const httpStatus = require('http-status');
const tokenService = require('./token.service');
const userService = require('./user.service');
const Token = require('../models/token.model');
const ApiError = require('../utils/ApiError');
const { tokenTypes } = require('../config/tokens');
const mongoUtil = require('../utils/mongoInit');

/**
 * Login with username and password
 * @param {string} email
 * @param {string} password
 * @return {Promise<User>}
 */
const loginUserWithEmailAndPassword = async (email, password) => {
  const user = await userService.getUserByEmail(email);
  if (!user || !(await user.isPasswordMatch(password))) {
    throw new ApiError(httpStatus.UNAUTHORIZED, 'Incorrect email or password');
  }
  return user;
};

/**
 * Logout
 * @param {string} refreshToken
 * @return {Promise}
 */
const logout = async refreshToken => {
  const refreshTokenDoc = await Token.findOne({
    token: refreshToken,
    type: tokenTypes.REFRESH,
    blacklisted: false
  });
  if (!refreshTokenDoc) {
    throw new ApiError(httpStatus.NOT_FOUND, 'Not found');
  }
  await refreshTokenDoc.remove();
};

/**
 * Refresh auth tokens
 * @param {string} refreshToken
 * @return {Promise<Object>}
 */
const refreshAuth = async refreshToken => {
  try {
    const refreshTokenDoc = await tokenService.verifyToken(
      refreshToken,
      tokenTypes.REFRESH
    );
    const user = await userService.getUserById(refreshTokenDoc.user);
    if (!user) {
      throw new Error();
    }
    await refreshTokenDoc.remove();
    return tokenService.generateAuthTokens(user);
  } catch (error) {
    throw new ApiError(httpStatus.UNAUTHORIZED, 'Please authenticate');
  }
};

/**
 * Reset password
 * @param {string} resetPasswordToken
 * @param {string} newPassword
 * @return {Promise}
 */
const resetPassword = async (resetPasswordToken, newPassword) => {
  try {
    const resetPasswordTokenDoc = await tokenService.verifyToken(
      resetPasswordToken,
      tokenTypes.RESET_PASSWORD
    );
    const user = await userService.getUserById(resetPasswordTokenDoc.user);
    if (!user) {
      throw new Error();
    }
    await Token.deleteMany({ user: user.id, type: tokenTypes.RESET_PASSWORD });
    await userService.updateUserById(user.id, { password: newPassword });
  } catch (error) {
    throw new ApiError(httpStatus.UNAUTHORIZED, 'Password reset failed');
  }
};

const getPermissionList = async ({ email }) => {
  const permissionsArray = [];
  console.log(
    'global?.baseConfig?.superadmins',
    global?.baseConfig?.superadmins
  );
  if ((global?.baseConfig?.superadmins || []).indexOf(email) > -1) {
    permissionsArray.push({
      type: 'manage',
      target: 'all'
    });
  } else if (email) {
    const db = mongoUtil.getDb();

    try {
      const userData = await db.collection('users').findOne({ email: email });
      if (userData) {
        const permissionGroups = userData.permission_groups.map(el => el.value);

        await db
          .collection('groups')
          .find({ _id: { $in: permissionGroups } })
          .forEach(el => {
            if (el.permissions.length) {
              el.permissions.forEach(permission => {
                permissionsArray.push({
                  type: permission.type,
                  target: permission.target
                });
              });
            }
          });
      }
    } catch (e) {
      global.logError('error fetching permissions for user');
    }
  }
  return permissionsArray;
};

module.exports = {
  loginUserWithEmailAndPassword,
  logout,
  refreshAuth,
  resetPassword,
  getPermissionList
};
