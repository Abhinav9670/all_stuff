const catchAsync = require('../utils/catchAsync');
const httpStatus = require('http-status');
const { checkMysqlHealth, checkMongoHealth } = require('../helpers/health');
const fs = require('fs');
const { storage } = require('../config/googleStorage');
const { logInfo } = require('../utils');

const permissionTargets = catchAsync(async (req, res) => {
  // only three fallback permissions to alert that values not received from consul
  const targets = global.baseConfig?.permissionTargets || ['order', 'customer'];

  const payload = {
    status: true,
    statusCode: '200',
    statusMsg: 'Permission targets fetched!',
    response: targets
  };
  res.status(httpStatus.OK).json(payload);
});

const healthCheck = catchAsync(async (req, res) => {
  try {
    const sqlStatus = await checkMysqlHealth();
    const {
      mongoHealth: mongoStatus,
      activeNodes: mongoActiveNodes
    } = await checkMongoHealth();

    res
      .status(httpStatus.OK)
      .json({ sqlStatus, mongoStatus, mongoActiveNodes });
  } catch (e) {
    res.status(500).json({});
  }
});

const downloadFileFromGCP = catchAsync(async (req, res) => {
  const dir = `${__dirname}/../../downloads`;
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir);
  }

  const bucketName = `${process.env.GS_BUCKET_NAME}`;
  const { file: srcFilename } = req.body;
  const fileNameSplit = srcFilename.split('/');
  const localFilename = fileNameSplit[fileNameSplit.length - 1];
  const destFilename = `${__dirname}/../../downloads/${Date.now()}_${localFilename}`;

  try {
    const options = {
      destination: destFilename
    };
    await storage.bucket(bucketName).file(srcFilename).download(options);
    logInfo('downloadFile', { srcFilename, bucketName, destFilename });
  } catch (e) {
    console.log('error in downloadFile method');
    global.logError(e);
    return res.status(500).json({ status: false, message: e.message });
  }

  try {
    return res.download(destFilename);
  } catch (e) {
    global.logError(e);
    return res.status(500).json({ status: false, message: e.message });
  }
});

module.exports = { permissionTargets, healthCheck, downloadFileFromGCP };
