import { all, call, fork, put, takeEvery } from 'redux-saga/effects';
import auth from '../../helpers/Firebase';
import { LOGIN_USER, REGISTER_USER, LOGOUT_USER, FORGOT_PASSWORD, RESET_PASSWORD, VERIFY_OTP } from '../actions';
import axios from 'axios';
import { createNotification } from '../../costumePages/costumComponents/Notifications';
const domain = new URL(process.env.SNTNL_API_HOST).hostname;

import {
  loginUserSuccess,
  loginUserError,
  registerUserSuccess,
  registerUserError,
  forgotPasswordSuccess,
  forgotPasswordError,
  resetPasswordSuccess,
  resetPasswordError,
  verifyOTPSuccess,
  verifyOTPError
} from './actions';

import { adminRoot, currentUser } from '../../constants/defaultValues';
import { setCurrentUser, getCurrentUser, removeFromCookie } from '../../helpers/Utils';

export function* watchVerifyOtp() {
  yield takeEvery(VERIFY_OTP, verifyWithOTP);
}

const verifyOTPAsync = async (otp, email, domain) => {
  try {
    const result = await axios.post(
      `${process.env.SNTNL_API_HOST}api/v1/auth/verify-otp`,
      {
        otp, email, domain
      },
      {
        headers: {
          'Content-Type': 'application/json'
        }
      }
    );
    console.log('result:::', result.data);
    return result;
  } catch (e) {
    createNotification({
      type: 'error',
      title: "OTP Verification Error",
      subtitle: 'OTP entered is wrong',
    });
    console.error('Login Error', error);
  }
};


const loginWithEmailPasswordAsync = async (email, password, domain) => {
  try {
    const user = await axios.post(`${process.env.SNTNL_API_HOST}api/v1/auth/login`, {
      email: email,
      password: password,
      domain
    });
    return user?.data;
  } catch {
    createNotification({
      type: 'error',
      title: 'Error While Login',
      subtitle: 'Please try again!'
    });
    console.error('Login Error');
  }
};

function* loginWithEmailPassword({ payload }) {
  console.log('inside loginWithEmailPassword');
  const { email, password } = payload.user;
  const { history } = payload;
  try {
    const loginUser = yield call(loginWithEmailPasswordAsync, email, password, domain);
    if (loginUser.status) {
      if (!loginUser?.data) {
        yield put(loginUserError(loginUser.message));
        return;
      }
      const { data: { message, uuid, displayName, registered, userEmail, token, refreshToken } = {} } = loginUser;
      const verifyOtpStatus = message;
      const item = {
        uuid: uuid,
        name: displayName,
        email: userEmail || email,
        emailVerified: registered,
        token: token,
        refreshToken: refreshToken
      };
      if (!verifyOtpStatus) {
        setCurrentUser(item);
        history.push(adminRoot)
      } else {
        window.localStorage.setItem('email', email);
        yield put(loginUserSuccess(item));
        history.push('/user/verify-otp');
      }
    } else {
      yield put(loginUserError(loginUser.message));
    }
  } catch (error) {
    console.log(error);
    yield put(loginUserError(error.response?.data?.data?.message));
  }
}

export function* watchLoginUser() {
  // eslint-disable-next-line no-use-before-define
  yield takeEvery(LOGIN_USER, loginWithEmailPassword);
}
export function* watchRegisterUser() {
  // eslint-disable-next-line no-use-before-define
  yield takeEvery(REGISTER_USER, registerWithEmailPassword);
}

const registerWithEmailPasswordAsync = async (email, password) =>
  // eslint-disable-next-line no-return-await
  await auth
    .createUserWithEmailAndPassword(email, password)
    .then(user => user)
    .catch(error => error);

function* registerWithEmailPassword({ payload }) {
  const { email, password } = payload.user;
  const { history } = payload;
  try {
    const registerUser = yield call(registerWithEmailPasswordAsync, email, password);
    if (!registerUser.message) {
      const item = { uid: registerUser.user.uid, ...currentUser };
      setCurrentUser(item);
      yield put(registerUserSuccess(item));
      history.push(adminRoot);
    } else {
      yield put(registerUserError(registerUser.message));
    }
  } catch (error) {
    yield put(registerUserError(error));
  }
}

function* verifyWithOTP({ payload }) {
  let { otp } = payload.user;
  const { history } = payload;
  console.log("OTP ", otp)
  try {
    const email = window.localStorage.getItem('email');
    const verifyOTP = yield call(verifyOTPAsync, otp, email, domain);
    if (verifyOTP.status) {
      const itemData = verifyOTP.data.data.userData;
      setCurrentUser(itemData);
      yield put(verifyOTPSuccess(itemData));
      history.push(adminRoot);
      window.localStorage.removeItem('email')
    } else {
      yield put(verifyOTPError(verifyOTP?.data?.message));
    }
  } catch (error) {
    yield put(verifyOTPError(error?.response?.data?.message));
  }
}

export function* watchLogoutUser() {
  // eslint-disable-next-line no-use-before-define
  yield takeEvery(LOGOUT_USER, logout);
}

const logoutAsync = async (history, uuid, token) => {
  if (uuid) {
    await axios.post(
      `${process.env.SNTNL_API_HOST}api/v1/auth/logout`,
      {
        uuid
      },
      {
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token.trim()}`
        }
      }
    );
  }
  history.push(adminRoot);
};

function* logout({ payload }) {
  const { history } = payload;
  const user = getCurrentUser();
  removeFromCookie();
  yield call(logoutAsync, history, user?.uuid, user?.token);
}

export function* watchForgotPassword() {
  // eslint-disable-next-line no-use-before-define
  yield takeEvery(FORGOT_PASSWORD, forgotPassword);
}

const forgotPasswordAsync = async email => {
  // eslint-disable-next-line no-return-await
  return await auth
    .sendPasswordResetEmail(email)
    .then(user => user)
    .catch(error => error);
};

function* forgotPassword({ payload }) {
  const { email } = payload.forgotUserMail;
  try {
    const forgotPasswordStatus = yield call(forgotPasswordAsync, email);
    if (!forgotPasswordStatus) {
      yield put(forgotPasswordSuccess('success'));
    } else {
      yield put(forgotPasswordError(forgotPasswordStatus.message));
    }
  } catch (error) {
    yield put(forgotPasswordError(error));
  }
}

export function* watchResetPassword() {
  // eslint-disable-next-line no-use-before-define
  yield takeEvery(RESET_PASSWORD, resetPassword);
}

const resetPasswordAsync = async (resetPasswordCode, newPassword) => {
  // eslint-disable-next-line no-return-await
  return await auth
    .confirmPasswordReset(resetPasswordCode, newPassword)
    .then(user => user)
    .catch(error => error);
};

function* resetPassword({ payload }) {
  const { newPassword, resetPasswordCode } = payload;
  try {
    const resetPasswordStatus = yield call(resetPasswordAsync, resetPasswordCode, newPassword);
    if (!resetPasswordStatus) {
      yield put(resetPasswordSuccess('success'));
    } else {
      yield put(resetPasswordError(resetPasswordStatus.message));
    }
  } catch (error) {
    yield put(resetPasswordError(error));
  }
}

export default function* rootSaga() {
  yield all([
    fork(watchLoginUser),
    fork(watchVerifyOtp),
    fork(watchLogoutUser),
    fork(watchRegisterUser),
    fork(watchForgotPassword),
    fork(watchResetPassword)
  ]);
}
