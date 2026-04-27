const { inTaxTypes } = require('../../constants');
const { logInfo } = require('../../utils');

exports.getGstInPan = warehouseId => {
  let gstIN = '';
  let panNo = '';
  if (warehouseId) {
    const mapper = global.javaOrderServiceConfig?.inventory_mapping || [];
    if (mapper.length) {
      const match = mapper.find(
        el => Number(el.warehouse_id) == Number(warehouseId)
      );
      if (match?.GSTIN) gstIN = match?.GSTIN;
      else logInfo('GSTIN not found!');
      if (match?.PAN) panNo = match?.PAN;
      else logInfo('PAN not found!');
    }
  }
  return { gstIN, panNo };
};

exports.getStateCode = state => {
  const mapper = global.taxConfig?.stateCode || {};
  return mapper?.[state.toUpperCase()];
};

exports.getInTaxTypes = () => {
  return inTaxTypes.map(v => {
    return {
      titlePercentage: `${v} Rate`,
      titleAmount: `${v} Amount`
    };
  });
};
