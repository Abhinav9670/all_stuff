const httpStatus = require('http-status');

const {
  createUser,
  getUsers,
  removeUser,
  createUserV2,
  updateUserV2,
} = require('../../services/users.service');


exports.userAdd = async ({ res, req }) => {
  try {
    const { body } = req;
    const results = await createUser({ ...body, _action: 'add' });
    if (results) {
      res.status(httpStatus.OK).send(JSON.stringify(results));
    }
  } catch (e) {
    global.logError(e);
    res.status(httpStatus.OK).send(e.message);
  }
};

exports.userUpdate = async ({ res, req }) => {
  try {
    const { body } = req;
    const results = await createUser({ ...body, _action: 'update' });
    if (results) {
      res.status(httpStatus.OK).send(JSON.stringify(results));
    }
  } catch (e) {
    global.logError(e);
    res.status(httpStatus.OK).send(e.message);
  }
};

exports.usersList = async ({ res, req }) => {
  try {
    const { body } = req;
    const results = await getUsers(body);
    if (results) {
      res.status(httpStatus.OK).send(JSON.stringify(results));
    }
  } catch (e) {
    global.logError(e);
    res.status(httpStatus.OK).send(e.message);
  }
};

exports.userRemove = async ({ res, req }) => {
  try {
    const { body } = req;
    const results = await removeUser(body);
    if (results) {
      res.status(httpStatus.OK).send(JSON.stringify(results));
    }
  } catch (e) {
    global.logError(e);
    res.status(httpStatus.OK).send(e.message);
  }
};