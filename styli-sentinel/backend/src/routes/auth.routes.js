const express = require('express');
const router = express.Router();

const authController = require('../controllers/core/auth.controller');
const {
  loginRequestValidator,
  regenerateTokenRequestValidator,
  logoutRequestValidator,
  forcelogoutRequestValidator,
  otpRequestValidator,
} = require('../validator/auth.validate');
const { authServiceCheck } = require('../helper');

router.post('/login', loginRequestValidator, (req, res) =>
  authController.loginWithUserNamePassword({ res, req })
);

router.post('/verify-otp', otpRequestValidator, (req, res) =>
  authController.verifyOTPForUser({ res, req })
);

router.post('/regenerate-token', regenerateTokenRequestValidator, (req, res) =>
  authController.regenerateToken({ res, req })
);

router.post('/user-info', (req, res) => authController.userInfo({ res, req }));

router.post('/logout', logoutRequestValidator, (req, res) =>
  authController.logout({ res, req })
);

router.post('/validate', (req, res) =>
  authController.validateRequest({ res, req })
);

router.post('/force-logout/cron', (req, res) =>
  authController.forceLogout({ res, req })
);

router.post('/force-logout/select/bulk', authServiceCheck, (req, res) =>
  authController.forceLogoutBulk({ res, req })
);
router.post('/user/info', authServiceCheck, (req, res) => authController.userInformation({ res, req }));

module.exports = router;
