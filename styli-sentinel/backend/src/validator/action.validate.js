const { Joi, validate } = require('express-validation');

exports.serviceActionAddRequestValidator = validate(
  {
    body: Joi.object({
      domain: Joi.string().required(),
      name: Joi.string().required(),
      url: Joi.string().required(),
      httpMethod: Joi.string()
        .required()
        .valid('POST', 'GET', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'),
      type: Joi.string().required().valid('R', 'W'),
      description: Joi.string().optional().allow(null, ''),
      authorization: Joi.boolean().optional(),
      authentication: Joi.boolean().optional(),
      status: Joi.boolean().optional(),
    }),
  },
  { keyByField: true }
);

exports.bulkRequestRequestValidator = validate(
  {
    body: Joi.array().items(
      Joi.object({
        domain: Joi.string().required(),
        name: Joi.string().required(),
        domainName: Joi.string().required(),
        url: Joi.string().required(),
        httpMethod: Joi.string()
          .required()
          .valid('POST', 'GET', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'),
        type: Joi.string().required().valid('R', 'W'),
        description: Joi.string().optional().allow(null, ''),
        authorization: Joi.boolean().optional(),
        authentication: Joi.boolean().optional(),
        status: Joi.boolean().optional(),
      })
    ),
  },
  { keyByField: true }
);
