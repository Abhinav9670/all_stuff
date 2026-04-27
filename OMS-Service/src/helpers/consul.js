const axios = require('axios');

exports.updateConsul = async (key, value) => {
  try {
    const consulUrl = `http://${process.env.CONSUL_HOST}:${process.env.CONSUL_PORT}/v1/kv/${key}`;

    //  const cRsp = await axios.put(consulUrl, value);
    const cRsp = await axios.put(consulUrl, value, {
      headers: {
        Authorization: `Bearer ${process.env.CONSUL_TOKEN}`
      }
    });
    const { data } = cRsp;
    return data;
  } catch (e) {
    global.logError(e);
  }
};

exports.fetchConsul = async key => {
  try {
    const consulUrl = `http://${process.env.CONSUL_HOST}:${process.env.CONSUL_PORT}/v1/kv/${key}?raw=true`;
    // const cRsp = await axios.get(consulUrl);
    const cRsp = await axios.get(consulUrl, {
      headers: {
        Authorization: `Bearer ${process.env.CONSUL_TOKEN}`
      }
    });
    const { data } = cRsp;
    return data;
  } catch (e) {
    global.logError(e);
    return {};
  }
};
