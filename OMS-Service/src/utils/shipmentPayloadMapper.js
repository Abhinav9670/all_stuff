/**
* Maps StatusCode to notification ID based on the new mapping criteria
* @param {number} statusCode - The StatusCode from new payload
* @returns {number} - Mapped notification ID
*/
const mapStatusCodeToNotificationId = (statusCode) => {
  const statusCodeToNotificationIdMap = {
    // DELIVERED - replace 5 by 132 and 112
    132: 5,
    112: 5,
    106: 5,

    // PICKED_UP - replace 2 and 3 by 105 and 126
    105: 2,
    126: 3,

    //OUT_FOR_DELIVERY - replace 3 by 111
    111: 3,
    110: 3,

    // PICKUP_FAILED - replace 7 by 139
    139: 7,

    // CANCELLED - replace 10 by 104 and 133
    104: 10,
    133: 10,

    // LOST - replace 16 by 116 and 135
    116: 16,
    135: 16,

    // FAILED_DELIVERY - replace 4 by 122
    122: 4,

    // OUT_FOR_PICKUP - replace 1 by 125
    125: 1,

    // RTO - replace 12 by 168
    168: 12,

    // RTO_INITIATED - replace 6 by 147
    147: 6
  };

  return statusCodeToNotificationIdMap[statusCode] || statusCode;
};

/**
* Maps new status format to existing abbreviated status format
* @param {string} newStatus - The Status from new payload
* @returns {string} - Mapped status in existing abbreviated format
*/
const mapStatusToExisting = (newStatus) => {
  const statusMapping = {
    'DELIVERED': 'DELIVERED',
    'IN_HUB': 'DELIVERED',
    'PICKED_UP': 'PICKEDUP',
    'OUT_FOR_DELIVERY': 'OFD',
    'IN_TRANSIT': 'OFD',
    'PICKUP_FAILED': 'PICKUP_FAILED',
    'CANCELLED': 'CANCELLED',
    'LOST': 'LOST',
    'FAILED_DELIVERY': 'FAILED_DELIVERY',
    'OUT_FOR_PICKUP': 'OUT_FOR_PICKUP',
    'RTO': 'RTO',
    'RTO_INITIATED': 'RTO_INITIATED'
  };

  return statusMapping[newStatus] || newStatus;
};

/**
* Maps new shipment payload format to existing structure
* @param {Object} newPayload - The new payload format
* @returns {Object} - Mapped payload in existing format
*/
const mapNewPayloadToExisting = (newPayload) => {
  const {
    waybill,
    ShipmentNo,
    Status,
    time_stamp,
    StatusCode,
    remarks,
    additional_remarks,
    items = [],
    additional_info = {}
  } = newPayload;

  // Determine if it's a return shipment based on StatusCode
  const returnStatusCodes = [124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 142, 144, 152, 156, 158, 160, 162, 164, 166, 167, 170];
  const isReturn = returnStatusCodes.includes(StatusCode);

  // Map StatusCode to notification ID
  const notificationId = mapStatusCodeToNotificationId(StatusCode);

  // Map Status to existing format
  const mappedStatus = mapStatusToExisting(Status);

  // Map to existing structure
  const mappedPayload = {
    waybill,
    additional: {
      latest_status: {
        reference_number: ShipmentNo,
        timestamp: time_stamp,
        remark: remarks || additional_remarks,
        items
      },
      is_rvp: isReturn,
      notification_event_id: notificationId,
      additional_info
    },
    status: mappedStatus
  };

  return mappedPayload;
};

module.exports = {
  mapNewPayloadToExisting,
  mapStatusCodeToNotificationId,
  mapStatusToExisting
};

 