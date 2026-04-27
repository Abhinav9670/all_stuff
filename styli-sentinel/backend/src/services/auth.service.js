const axios = require('axios');
const userModel = require('../models/user.model');
const serviceModel = require('../models/service.model');
const roleModel = require('../models/role.model');
const otpModel = require('../models/otp.model');
const mongoose = require('mongoose');
const { firebaseAdminAuth } = require('../config/firebase-admin');
const { sendSgEmail } = require('../config/email-service');
const { testingAccounts } = require('../helper');

// Constant domain-to-service mapping
const DOMAIN_SERVICE_MAP = {
  'promo-apinj-prod.stylishop.com': 'Promo Service',
  'promo-apinj-qa.stylifashion.com': 'Promo Service',
  'customermg-prod.stylishop.com': 'Customer Management Service',
  'customermg-qa.stylishop.store': 'Customer Management Service',
  'prod-api.stylishop.com': 'OMS Service',
  'oms-api-qa.stylishop.store': 'OMS Service',
  'adrsmpr.stylishop.com': 'Address Mapper Service',
  'adrsmpr-qa.stylishop.store': 'Address Mapper Service',
  'tracpicapi-prod.stylishop.store': 'Tracpic Service',
  'tracpicapi-qa.stylishop.store': 'Tracpic Service',
  'eas.stylishop.com': 'EAS Service',
  'qa-eas.stylifashion.com': 'EAS Service',
  'vm-prod.stylishop.com': 'Search Service',
  'vm-qa.stylifashion.com': 'Search Service',
  'prod-sentinel-api.stylishop.com': 'Sentinel Service',
  'dev-sentinel-api.stylifashion.com': 'Sentinel Service',
  'mulin-api.stylishop.com': 'Mulin Service',
  'mulin-qa.stylifashion.com': 'Mulin Service',
  'ivyprodapi.stylishop.com': 'Ivy Service'
};

// Utility function to get service name from domain
const getServiceNameFromDomain = domain => {
  return DOMAIN_SERVICE_MAP[domain] || `Service for ${domain}`;
};

exports.loginWithUserNamePassword = async ({ email, password, domain }) => {
  try {
    const response = await axios.post(
      `${process.env.FIREBASE_URL}/accounts:signInWithPassword?key=${process.env.FIREBASE_APIKEY}`,
      { email: email, password: password, returnSecureToken: true },
      {
        headers: {
          'Content-Type': 'application/json'
        }
      }
    );

    const otp = Math.floor(100000 + Math.random() * 900000).toString();
    const { fromEmail, fromName } = global?.globalConfig?.emailConfig || {};

    let verifyOtp = false;

    if (domain) {
      const verifyotpStatus = await serviceModel.findById({ _id: domain }).lean().exec();
      verifyOtp = verifyotpStatus?.verifyotp;
    }
    if (domain && verifyOtp) {
      const serviceName = getServiceNameFromDomain(domain);
      console.log(`Service Name: ${serviceName}, Domain: ${domain}`);
      await sendSgEmail({
        to: email,
        from: { email: fromEmail, name: fromName },
        subject: 'OTP for Email Verification',
        html: `Your OTP for email verification ${serviceName} is : ${otp}`
      });

      let userData = {
        displayName: response?.data?.displayName == '' ? response?.data?.email : response?.data?.displayName,
        email: response?.data?.email,
        token: response?.data?.idToken,
        refreshToken: response?.data?.refreshToken,
        uuid: response?.data?.localId,
        registered: response?.data?.registered,
        verifyotp: verifyOtp
      };

      const updatedOtp = await otpModel.findOneAndUpdate(
        { _id: email },
        { otp, userData, domain },
        { new: true, upsert: true }
      );

      return {
        status: response.status,
        data: {
          verifyOtp,
          message: 'Verification OTP sent to registered email'
        }
      };
    } else {
      const firebaseCustomToken = await firebaseAdminAuth.createCustomToken(response.data.localId);
      return {
        status: response.status,
        data: {
          displayName: response?.data?.displayName == '' ? response?.data?.email : response?.data?.displayName,
          email: response?.data?.email,
          token: response?.data?.idToken,
          refreshToken: response?.data?.refreshToken,
          expiresIn: response?.data?.expiresIn,
          uuid: response?.data?.localId,
          registered: response?.data?.registered,
          firebaseCustomToken
        }
      };
    }
  } catch (error) {
    console.log(
      'loginWithUserNamePassword error::',
      error && typeof error === 'object' ? JSON.stringify(error) : error
    );
    return {
      data: error?.response?.data?.error,
      message: error?.response?.data?.error?.message,
      status: error?.response?.status,
      statusText: error?.response?.statusText
    };
  }
};

exports.verifyOTPValidation = async ({ email, otp, domain }) => {
  try {
    const response = await otpModel.findOne({ _id: email, domain });

    if (response.otp === otp) {
      let userData = response?.userData;

      const deleted = await otpModel.deleteOne({ _id: email, domain });

      return {
        status: 200,
        data: {
          message: 'OTP is verified',
          userData
        }
      };
    } else {
      return {
        status: 500,
        data: {
          message: 'OTP entered is wrong'
        }
      };
    }
  } catch (error) {
    return {
      message: error?.message
    };
  }
};

exports.generateToken = async ({ refreshToken }) => {
  try {
    const response = await axios.post(
      `${process.env.FIREBASE_REFRESH_TOKEN_URL}/token?key=${process.env.FIREBASE_APIKEY}`,
      { refresh_token: refreshToken, grant_type: 'refresh_token' },
      {
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded'
        }
      }
    );
    return {
      status: response.status,
      data: {
        token: response?.data?.access_token,
        refreshToken: response?.data?.refresh_token,
        expiresIn: response?.data?.expires_in,
        uuid: response?.data?.user_id
      }
    };
  } catch (error) {
    return {
      data: error?.response?.data?.error,
      message: error?.response?.data?.error?.message,
      status: error?.response?.status,
      statusText: error?.response?.statusText
    };
  }
};

exports.getUserInfo = async ({ headers, token = null }) => {
  try {
    if (!token) {
      token = getTokenFromHeader({ headers });
    }

    const response = await this.getUserDetails({ token });
    return {
      ...response,
      status: true
    };
  } catch (error) {
    return {
      data: error?.response?.data?.error,
      message: error?.response?.data?.error?.message,
      statusText: error?.response?.statusText,
      status: false
    };
  }
};

exports.validateAPIRequest = async ({ method, headers, action }) => {
  try {
    const firebaseResponse = await this.getUserDetails({
      token: getTokenFromHeader({ headers })
    });
    const email = firebaseResponse?.email;
    if (!firebaseResponse || !firebaseResponse?.email) {
      return {
        statusCode: 401,
        status: false,
        message: `Authentication failed for Email: ${email}`
      };
    }
    const userData = await userModel.findOne({ _id: email });
    if (!userData) {
      return {
        statusCode: 403,
        status: false,
        message: `The Email: ${email} is not added contact tech-support`
      };
    }
    if (!userData?.status) {
      return {
        statusCode: 403,
        status: false,
        message: `The Email: ${email} is disabled contact tech-support`
      };
    }
    const host = getDomain({ headers });
    const service = await serviceModel.findOne({ _id: host.toString() });
    if (service && !service?.status) {
      global.logError(`Authorization disabled for ${service?._id}`);
      // res.status(200).send({ status: true });
      return {
        statusCode: 200,
        status: true,
        message: `Success`,
        user: firebaseResponse
      };
    }
    if (service && service?.authorization) {
      const roles = userData.roles.map(e => e.toString());
      const fullUrl = `${method.toUpperCase()}|${host}${action}`;
      console.log(`Full URL:: ${fullUrl}, roles:: ${JSON.stringify(roles)}`);
      return await validateAuthorization({ fullUrl, roles, firebaseResponse });
    }
    global.logError(`Service is not exist in sentinel ${host}`);
    return {
      statusCode: 200,
      status: true,
      message: `Success`,
      user: firebaseResponse
    };
  } catch (error) {
    global.logError(error);
    return {
      statusCode: 500,
      status: false,
      message: `ERROR`
    };
  }
};

const validateAuthorization = async ({ fullUrl, roles, firebaseResponse }) => {
  try {
    const roleObj = roles.map(e => {
      return mongoose.Types.ObjectId(e);
    });
    console.log(
      JSON.stringify({
        _id: { $in: roleObj },
        action: fullUrl.toString()
      })
    );

    const rolePermission = await roleModel.find({
      _id: { $in: roleObj },
      action: fullUrl.toString()
    });
    console.log('rolePermission::: ', JSON.stringify(rolePermission));
    if (rolePermission.length > 0) {
      return {
        statusCode: 200,
        status: true,
        message: `Success`,
        user: firebaseResponse
      };
    } else {
      return {
        statusCode: 403,
        status: false,
        message: `Permission for ${fullUrl} not give, Please contact Tech-support`
      };
    }
  } catch (error) {
    global.logError(error);
    return {
      statusCode: 500,
      status: false,
      message: `Error occured`
    };
  }
};

exports.getUserDetails = async ({ token }) => {
  try {
    const response = await axios.post(
      `${process.env.FIREBASE_URL}/accounts:lookup?key=${process.env.FIREBASE_APIKEY}`,
      { idToken: token },
      {
        headers: {
          'Content-Type': 'application/json'
        }
      }
    );
    if (response?.data?.users?.length > 0 && response.status == 200) {
      return {
        email: response?.data?.users?.[0]?.email,
        uuid: response?.data?.users?.[0]?.localId,
        displayName: response?.data?.users?.[0]?.displayName || '',
        photoUrl: response?.data?.users?.[0]?.photoUrl || '',
        status: true
      };
    }
  } catch (error) {
    console.log('Firebase token Error', error);
    return false;
  }

  return false;
};

const getDomain = ({ headers }) => {
  return headers?.authority ? headers?.authority : headers.host;
};

const getTokenFromHeader = ({ headers }) => {
  return headers?.authorization?.split(' ')?.[1];
};

exports.updateUserDetail = async (email, uuid) => {
  const loginTime = new Date();

  const userUpdate = await userModel.updateOne(
    { _id: email.toString() },
    {
      last_logged_in_time: loginTime,
      uuid: uuid,
      login_status: true
    }
  );

  return true;
};

exports.updateUser = async uuid => {
  const updated = await userModel.updateOne({ uuid: uuid.toString() }, { login_status: false });

  return true;
};

exports.cronForceLogout = async countHoursAgo => {
  try {
    const allUsers = await userModel.find({ last_logged_in_time: { $lte: countHoursAgo } });

    for (const e of allUsers) {
      await firebaseAdminAuth.revokeRefreshTokens(e?.uuid);
      await userModel.updateOne({ _id: e?._id.toString() }, { login_status: false });
    }
    return true;
  } catch (err) {
    console.log('Error thrown during force logout feature', err?.message);
    return false;
  }
};

exports.bulkLogout = async usersArray => {
  try {
    for (const u of usersArray) {
      const fire = await firebaseAdminAuth.revokeRefreshTokens(u?.uuid);
      await userModel.updateOne({ _id: u?._id?.toString() }, { login_status: false });
      return true;
    }
  } catch (e) {
    console.log('Error thrown during force logout feature', e?.message);
    return false;
  }
};

exports.getSingleUser = async ({ email }) => {
  try {
    const userData = await userModel.findOne({ _id: email }).lean().exec();
    return userData;
  } catch (error) {
    console.log('Error fetching user details', error);
  }
};
