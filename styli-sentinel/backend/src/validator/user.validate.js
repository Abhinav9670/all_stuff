const { Joi, validate } = require('express-validation');

exports.userAddRequestValidator = validate(
  {
    body: Joi.object({
      _id: Joi.string().required(),
      name: Joi.string().required(),
      roles: Joi.array().required(),
      authorization: Joi.boolean().optional(),
      authentication: Joi.boolean().optional(),
      status: Joi.boolean().optional(),
    }),
  },
  { keyByField: true }
);

exports.deleteuserValidator = validate(
  {
    body: Joi.object({
      _id: Joi.string().required(),
    }),
  },
  { keyByField: true }
);
