const { Joi, validate } = require('express-validation');

exports.serviceAddRequestValidator = validate(
  {
    body: Joi.object({
      domain: Joi.string().required(),
      name: Joi.string().required(),
      description: Joi.string().optional().allow(null, ''),
      authorization: Joi.boolean().optional(),
      authentication: Joi.boolean().optional(),
      status: Joi.boolean().optional(),
      verifyotp: Joi.boolean().optional()
    })
  },
  { keyByField: true }
);

exports.deleteRequestValidator = validate(
  {
    body: Joi.object({
      _id: Joi.string().required()
    })
  },
  { keyByField: true }
);
