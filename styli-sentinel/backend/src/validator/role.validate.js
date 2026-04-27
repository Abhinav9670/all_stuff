const { Joi, validate } = require('express-validation');

exports.roleAddRequestValidator = validate(
  {
    body: Joi.object({
      name: Joi.string().optional().trim().min(1).required(),
      description: Joi.string().optional().allow(null, ''),
      service: Joi.array().required(),
      action: Joi.array().required(),
      status: Joi.boolean().optional(),
    }),
  },
  { keyByField: true }
);

exports.roleUpdateRequestValidator = validate(
  {
    body: Joi.object({
      _id: Joi.string().optional(),
      name: Joi.string().optional().trim().min(1).required(),
      description: Joi.string().optional().allow(null, ''),
      service: Joi.array().required(),
      action: Joi.array().required(),
      status: Joi.boolean().optional(),
    }),
  },
  { keyByField: true }
);

exports.deleteroleValidator = validate(
  {
    body: Joi.object({
      _id: Joi.string().required(),
    }),
  },
  { keyByField: true }
);
