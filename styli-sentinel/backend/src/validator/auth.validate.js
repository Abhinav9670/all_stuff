const { Joi, validate } = require('express-validation');

exports.loginRequestValidator = validate(
  {
    body: Joi.object({
      email: Joi.string().required(),
      password: Joi.string().required(),
      domain: Joi.string().optional(),
      restricted_users: Joi.string().optional()
    })
  },
  { keyByField: true }
);

exports.otpRequestValidator = validate(
  {
    body: Joi.object({
      otp: Joi.string().required(),
      email: Joi.string().required(),
      domain: Joi.string().optional()
    })
  },
  { keyByField: true }
);

exports.regenerateTokenRequestValidator = validate(
  {
    body: Joi.object({
      refreshToken: Joi.string().required()
    })
  },
  { keyByField: true }
);

exports.logoutRequestValidator = validate(
  {
    body: Joi.object({
      uuid: Joi.string().required()
    })
  },
  { keyByField: true }
);

exports.forcelogoutRequestValidator = validate(
  {
    body: Joi.object({
      daysCount: Joi.number().required()
    })
  },
  { keyByField: true }
);
