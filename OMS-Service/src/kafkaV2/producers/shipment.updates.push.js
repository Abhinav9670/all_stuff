const { producerV2 } = require('../index');

const pushShipmentUpdatesToKafka1 = async body => {
  const timestamp = new Date().toISOString().replace(/[-:.TZ]/g, "");
  const keyWithTimestamp = `shipmentUpdate_${timestamp}`;

  const respose = await producerV2.send({
    topic: `${process.env.KAFKA_SHIPMENT_UPDATE_TOPIC}`,
    messages: [{ key: keyWithTimestamp, value: JSON.stringify(body) }]
  });
  return respose;
};

const pushShipmentUpdatesToKafka2 = async body => {
  const timestamp = new Date().toISOString().replace(/[-:.TZ]/g, "");
  const keyWithTimestamp = `shipmentUpdate_${timestamp}`;

  const response = await producerV2.send({
    topic: `${process.env.KAFKA_SHIPMENT_RETURN_UPDATE_TOPIC}`,
    messages: [{ key: keyWithTimestamp, value: JSON.stringify(body) }]
  });

  return response;
};



module.exports = { pushShipmentUpdatesToKafka1,pushShipmentUpdatesToKafka2 };
