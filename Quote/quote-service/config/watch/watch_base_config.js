const cache = require('memory-cache');
const { logErrorString } = require('../../helpers/utils');

const watchBaseConfig = ({ consul }) => {
  const envValue = `${process.env.STYLI_ENV}`;
  const baseConfigWatch = consul.watch({
    method: consul.kv.get,
    options: { key: `quote-service/base_config_${envValue}` },
    backoffFactor: 1000,
  });

  baseConfigWatch.on("change", function (baseConfig, err) {
    try {
      if (baseConfig?.Value) {
        cache.put("baseConfig", JSON.parse(baseConfig?.Value));
      }
    } catch (e) {
      logErrorString(e, "Error in Parsing Consul Base config " + e.message);
    }
  });
  baseConfigWatch.on("error", function (err) {
    logErrorString(err, `error watchin  base_config_${envValue} consul : `);
  });
};

module.exports = watchBaseConfig;
