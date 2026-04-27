const httpStatus = require('http-status');

const {
  createRole,
  getRoles,
  removeROle,
} = require('../../services/roles.service');
const { removeRole } = require('../../services/roles.service');


exports.roleAdd = async ({ res, req }) => {
  try {
    const { body } = req;
    const results = await createRole({ ...body, _action: 'add' });
    if (results) {
      res.status(httpStatus.OK).send(JSON.stringify(results));
    }
  } catch (e) {
    global.logError(e);
    res.status(httpStatus.OK).send(e.message);
  }
};

exports.roleUpdate = async ({ res, req }) => {
  try {
    const { body } = req;
    const results = await createRole({ ...body, _action: 'update' });
    if (results) {
      res.status(httpStatus.OK).send(JSON.stringify(results));
    }
  } catch (e) {
    global.logError(e);
    res.status(httpStatus.OK).send(e.message);
  }
};

exports.roleList = async ({ res, req }) => {
  try {
    const { body } = req;
    const results = await getRoles(body);
    if (results) {
      res.status(httpStatus.OK).send(JSON.stringify(results));
    }
  } catch (e) {
    global.logError(e);
    res.status(httpStatus.OK).send(e.message);
  }
};

exports.roleRemove = async ({ res, req }) => {
  try {
    const { body } = req;
    const results = await removeRole(body);
    if (results) {
      res.status(httpStatus.OK).send(JSON.stringify(results));
    }
  } catch (e) {
    global.logError(e);
    res.status(httpStatus.OK).send(e.message);
  }
};