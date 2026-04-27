const { RmaRequest, RmaTracking } = require('../models/seqModels/index');
const { getRmaStatus } = require('./rma');

exports.validateRecreate = async ({ incIdsArr, awbIncMap }) => {
  const rmaStatus = await getRmaStatus();
  const errorStatusCodes = [15, 7, 27, 19];
  const availableRmaIds = [];
  const errorData = [['reference_number', 'awb', 'Error']];
  let hasError = false;
  const statusMap = rmaStatus.reduce((sMap, status) => {
    sMap[status.status_id] = status.title;
    return sMap;
  }, {});

  const response = await RmaRequest.findAll({
    // where: { rma_inc_id: { [Op.in]: incIdsArr } },
    where: { rma_inc_id: incIdsArr },
    include: [{ model: RmaTracking }]
  });

  response.forEach(rma => {
    const { RmaTrackings, status, rma_inc_id } = rma.dataValues;
    availableRmaIds.push(rma_inc_id);
    const trackingArr = RmaTrackings.map(tracking => {
      return tracking?.dataValues?.tracking_number;
    });

    if (errorStatusCodes.includes(status)) {
      hasError = true;
      errorData.push([
        rma_inc_id,
        awbIncMap[rma_inc_id],
        `current status : ${statusMap[status]}`
      ]);
    } else if (!trackingArr.length) {
      hasError = true;
      errorData.push([
        rma_inc_id,
        awbIncMap[rma_inc_id],
        awbIncMap[rma_inc_id]
          ? 'no AWB number mapped in system'
          : 'AWB missing in uploaded sheet'
      ]);
    } else if (!trackingArr.includes(`${awbIncMap[rma_inc_id]}`)) {
      hasError = true;
      errorData.push([
        rma_inc_id,
        awbIncMap[rma_inc_id],
        'combination of Return id and AWB does not exist'
      ]);
    }
  });
  // console.log({ statusMap, errorData, hasError });

  return { hasError, errorData, availableRmaIds };
};
