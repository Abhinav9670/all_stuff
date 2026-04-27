const {
  validateAPIRequest,
  loginWithUserNamePassword,
  generateToken,
  getUserInfo,
  updateUserDetail,
  updateUser,
  cronForceLogout,
  bulkLogout,
  getSingleUser,
  verifyOTPValidation
} = require('../../services/auth.service');
const { firebaseAdminAuth } = require('../../config/firebase-admin');
const userModel = require('../../models/user.model');
const roleModel = require('../../models/role.model');
const { default: mongoose } = require('mongoose');
const logger = require('../../helper/logger');

exports.validateRequest = async ({ req, res }) => {
  const {
    body: { headers, action, method },
    headers: actualHeaders
  } = req;
  logger.info(
    `action:: ${action},authority:: ${headers?.authority}, host:: ${headers?.host}, dev-token:: ${actualHeaders?.['dev-token']}`
  );
  if (headers?.authority != headers.host) {
    return res.status(403).json({ error: 'header authority and host mistmatch', status: false });
  }
  const response = await validateAPIRequest({
    method,
    headers,
    action
  });
  return res.status(response?.statusCode || 500).send({
    message: response?.message || 'Please contact Tech-Support',
    status: response?.status ?? false,
    user: response?.user
  });
};

exports.loginWithUserNamePassword = async ({ req, res }) => {
  const {
    body: { email, password, domain, restricted_users }
  } = req;
  let isRolePresent;
  if (restricted_users && domain && email) {
    //restrict user for particular service
    let userDetails = await userModel.findOne({ _id: email });
    logger.info('loginWithUserNamePassword --> userDetails', userDetails);
    if (userDetails?.roles && userDetails?.roles.length > 0) {
      let formattedRoles = userDetails?.roles.map(value => mongoose.Types.ObjectId(value));
      console.log('formattedRoles', formattedRoles);
      isRolePresent = await roleModel.find({
        _id: { $in: formattedRoles },
        service: domain
      });
      console.log('isRolePresent', isRolePresent);
      if (isRolePresent.length === 0) {
        return res.status(401).send({
          status: false,
          data: {
            message: 'User Does not have access to this service'
          }
        });
      }
    }
  }

  const response = await loginWithUserNamePassword({
    email,
    password,
    domain
  });
  logger.info('loginWithUserNamePassword --> responsept', response);

  if (response?.status == 200) {
    const resp = await updateUserDetail(email, response?.data?.uuid);

    if (resp) {
      logger.info('resp', response);
      if (restricted_users === 'true') {
        isRolePresent = isRolePresent?.map(value => value.name);
        response.data.roles = isRolePresent || [];
      }
      logger.info('response', response);
      return res.status(200).send({
        data: response.data,
        status: true
      });
    }
  } else {
    return res.status(response?.status || 500).send({
      data: response?.data,
      message: response?.message || 'Please contact Tech-Support',
      status: false
    });
  }
};

exports.verifyOTPForUser = async ({ req, res }) => {
  const {
    body: { otp, email, domain }
  } = req;

  const response = await verifyOTPValidation({
    otp,
    email,
    domain
  });
  if (response?.status == 200) {
    return res.status(200).send({
      data: response.data,
      status: true
    });
  } else {
    return res.status(response?.status || 500).send({
      data: response?.data,
      message: response?.data?.message || 'OTP validation failed',
      status: false
    });
  }
};

exports.regenerateToken = async ({ req, res }) => {
  const {
    body: { refreshToken },
    headers
  } = req;
  const response = await generateToken({
    refreshToken
  });

  if (response?.status == 200) {
    const userInfo = await getUserInfo({
      headers,
      token: response.data?.token
    });
    if (!userInfo.status) {
      return res.status(500).send({
        data: {
          ...userInfo?.data
        },
        message: userInfo?.message || 'Please contact Tech-Support',
        status: false
      });
    }
    const userValue = await getSingleUser({ email: userInfo?.email });
    const firebaseCustomToken = await firebaseAdminAuth.createCustomToken(response.data.uuid);
    if (userValue?.uuid && userValue?.last_logged_in_time) {
      return res.status(200).send({
        data: {
          ...response?.data,
          email: userInfo.email,
          displayName: userInfo.email,
          refreshToken: refreshToken,
          firebaseCustomToken
        },
        statusCode: '200',
        status: true
      });
    } else {
      const resp = await updateUserDetail(userInfo?.email, userInfo?.uuid);
      const firebaseCustomToken = await firebaseAdminAuth.createCustomToken(response.data.uuid);
      return res.status(200).send({
        data: {
          ...response?.data,
          email: userInfo.email,
          displayName: userInfo.email,
          refreshToken: refreshToken,
          firebaseCustomToken
        },
        statusCode: '200',
        status: true
      });
    }
  } else {
    return res.status(response?.status || 500).send({
      data: {
        ...response?.data
      },
      message: response?.message || 'Please contact Tech-Support',
      status: false
    });
  }
};

exports.userInfo = async ({ req, res }) => {
  const { headers } = req;
  const response = await getUserInfo({ headers });
  if (response?.status) {
    return res.status(200).send(response);
  }
  return res.status(403).send(response);
};

exports.logout = async ({ req, res }) => {
  try {
    const {
      body: { uuid }
    } = req;
    await firebaseAdminAuth.revokeRefreshTokens(uuid);

    const resp = await updateUser(uuid);

    if (resp) {
      return res.status(200).send({
        status: true,
        message: 'Logout successfully'
      });
    }
  } catch (error) {
    return res.status(500).send({
      status: false,
      message: error.message
    });
  }
};

exports.forceLogout = async ({ req, res }) => {
  try {
    const enableStatus = global?.globalConfig?.enableForceLogout;
    if (enableStatus) {
      const hoursCount = global?.globalConfig?.forceLogoutThresholdHrs;
      let currentDate = new Date();

      let countHoursAgo = new Date(currentDate);
      countHoursAgo.setHours(currentDate.getHours() - hoursCount);

      const resp = await cronForceLogout(countHoursAgo);

      if (resp) {
        return res.status(200).send({
          status: true,
          message: 'Forced Logout Successfull'
        });
      } else {
        return res.status(500).send({
          status: true,
          message: 'Error occurred in Force Logout'
        });
      }
    } else {
      return res.status(201).send({
        status: false,
        message: 'Force Logout not enabled'
      });
    }
  } catch (error) {
    return res.status(500).send({
      status: false,
      message: error.message
    });
  }
};

exports.forceLogoutBulk = async ({ req, res }) => {
  try {
    const enableStatus = global?.globalConfig?.enableForceLogout;

    if (enableStatus) {
      const {
        body: { usersArray }
      } = req;

      const resp = await bulkLogout(usersArray);

      if (resp) {
        return res.status(200).send({
          status: true,
          message: 'Bulk Forced Log Out'
        });
      }
    } else {
      return res.status(201).send({
        status: true,
        message: 'Force Logout not enabled'
      });
    }
  } catch (error) {
    return res.status(500).send({
      status: false,
      message: error.message
    });
  }
};

exports.userInformation = async ({ req, res }) => {
  try {
    if (!req?.body?.email) {
      return res.status(400).json({ error: 'email is Required' });
    }

    const userValue = await userModel.findOne({ _id: req?.body?.email });

    return res.json({
      message: 'Users fetch successfully',
      user: userValue || {},
      status: true
    });
  } catch (error) {
    return res.status(500).send({
      status: false,
      message: error.message
    });
  }
};
