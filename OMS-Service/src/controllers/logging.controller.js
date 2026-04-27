const catchAsync = require('../utils/catchAsync');
const loggingService = require('../services/logging.service');
const { fetchAdminLogs } = require('../helpers/logging');

const findInventoryLogs = catchAsync(async (req, res) => {
  const sku = req.params.sku;
  const inventory = req.params.inventory;
  const logs = await loggingService.findInventoryLogBySku(sku, inventory);
  res.send(logs);
});

const findAdminLogs = catchAsync(async (req, res) => {
  const { filters, offset, pagesize, query } = req.body;
  const logData = await fetchAdminLogs({ filters, offset, pagesize, query });
  res.send({ data: logData, status: true });
});

module.exports = {
  findInventoryLogs,
  findAdminLogs
};