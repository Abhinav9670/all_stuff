const httpStatus = require('http-status');
const {
  createService,
  getService,
  removeService,
  statusUpdateService,
} = require('../../services/services.service');
const {
  createServiceAction,
  getServiceAction,
  removeServiceAction,
  bulkUploadActions,
  csvUploadActions,
  statusUpdateServiceAction,
} = require('../../services/action.service');

exports.test = async ({ res, req }) => {
  res.status(200).send({ status: 'hi' });
};

exports.serviceAdd = async ({ res, req }) => {
  try {
    const { body } = req;
    const results = await createService({ ...body, _action: 'add' });
    if (results) {
      res.status(httpStatus.OK).send(JSON.stringify(results));
    }
  } catch (e) {
    global.logError(e);
    res.status(httpStatus.OK).send(e.message);
  }
};

exports.serviceUpdate = async ({ res, req }) => {
  try {
    const { body } = req;
    const results = await createService({ ...body, _action: 'update' });
    if (results) {
      res.status(httpStatus.OK).send(JSON.stringify(results));
    }
  } catch (e) {
    global.logError(e);
    res.status(httpStatus.OK).send(e.message);
  }
};

exports.serviceList = async ({ res, req }) => {
  try {
    const { body } = req;
    const results = await getService(body);
    if (results) {
      res.status(httpStatus.OK).send(JSON.stringify(results));
    }
  } catch (e) {
    global.logError(e);
    res.status(httpStatus.OK).send(e.message);
  }
};

exports.serviceRemove = async ({ res, req }) => {
  try {
    const { body } = req;
    const results = await removeService(body);
    if (results) {
      res.status(httpStatus.OK).send(JSON.stringify(results));
    }
  } catch (e) {
    global.logError(e);
    res.status(httpStatus.OK).send(e.message);
  }
};

exports.serviceStatus = async ({ res, req }) => {
  try {
    const { body } = req;
    const results = await statusUpdateService(body);
    if (results) {
      res.status(httpStatus.OK).send(JSON.stringify(results));
    }
  } catch (e) {
    global.logError(e);
    res.status(httpStatus.OK).send(e.message);
  }
};

exports.serviceActionAdd = async ({ res, req }) => {
  try {
    const { body } = req;
    const results = await createServiceAction({ ...body, _action: 'add' });
    if (results) {
      res.status(httpStatus.OK).send(JSON.stringify(results));
    }
  } catch (e) {
    global.logError(e);
    res.status(httpStatus.OK).send(e.message);
  }
};

exports.serviceActionUpdate = async ({ res, req }) => {
  try {
    const { body } = req;
    const results = await createServiceAction({ ...body, _action: 'update' });
    if (results) {
      res.status(httpStatus.OK).send(JSON.stringify(results));
    }
  } catch (e) {
    global.logError(e);
    res.status(httpStatus.OK).send(e.message);
  }
};

exports.serviceActionList = async ({ res, req }) => {
  try {
    const { body } = req;
    const results = await getServiceAction(body);
    if (results) {
      res.status(httpStatus.OK).send(JSON.stringify(results));
    }
  } catch (e) {
    global.logError(e);
    res.status(httpStatus.OK).send(e.message);
  }
};

exports.serviceActionRemove = async ({ res, req }) => {
  try {
    const { body } = req;
    const results = await removeServiceAction(body);
    if (results) {
      res.status(httpStatus.OK).send(JSON.stringify(results));
    }
  } catch (e) {
    global.logError(e);
    res.status(httpStatus.OK).send(e.message);
  }
};

exports.serviceActionStatus = async ({ res, req }) => {
  try {
    const { body } = req;
    const results = await statusUpdateServiceAction(body);
    if (results) {
      res.status(httpStatus.OK).send(JSON.stringify(results));
    }
  } catch (e) {
    global.logError(e);
    res.status(httpStatus.OK).send(e.message);
  }
};

exports.bulkUpload = async ({ res, req }) => {
  try {
    const { body } = req;
    const results = await bulkUploadActions(body);
    if (results) {
      res.status(httpStatus.OK).send(JSON.stringify(results));
    }
  } catch (e) {
    global.logError(e);
    res.status(httpStatus.OK).send(e.message);
  }
};

exports.upload = async ({ res, req }) => {
  const path = require('path');
  var csvtojson = require('csvtojson');

  const filePath = path.resolve(__dirname, 'Action.csv');

  try {
    // Read the CSV file and convert it to JSON
    const jsonArray = await csvtojson().fromFile(filePath);
    // Process each row of the CSV file
    // jsonArray.forEach(async (row) => {

    const results = await csvUploadActions(jsonArray);
    if (results) {
      res.status(httpStatus.OK).send(JSON.stringify(results));
    }

    // });
  } catch (error) {
    console.error('Error reading CSV file:', error);
    res.status(500).send('Error reading CSV file.');
  }
};
