if (
  process.env.ELASTIC_APM_SERVICE_NAME &&
  process.env.ELASTIC_APM_SERVICE_NAME != ''
) {
  const apm = require('elastic-apm-node');
  apm.start({
    serviceName: `${process.env.ELASTIC_APM_SERVICE_NAME || ''}`,
    secretToken: '',
    captureBody: 'all',
    serverUrl: process.env.ELASTIC_APM_SERVER_URL,
  });

  const envLabels = [
    'NODE_ENV',
    'NODE_ENV_ALT',
    'PROJECT',
    'BUILD',
    'REV',
    'BRANCH',
    'TAG_NAME',
    'COMMIT_SHA',
    'REPO_NAME',
    'TEST_ENV_NAME',
    'NODE_VERSION',
    'ELASTIC_HOST',
  ];
  const labels = {};
  envLabels.forEach((l) => {
    if (process.env[l]) {
      labels[l] = process.env[l];
    }
  });

  global.logError = (e, custom = {}) => {
    try {
      if (apm) {
        apm.addLabels(labels);
        if (e && e.map) {
          e.map((error) => {
            apm.captureError(error, { ...custom });
          });
        } else {
          apm.captureError(e, { ...custom });
          console.error(e);
        }
      } else {
        console.error(e);
      }
    } catch (error) {
      console.error(error);
    }
  };
} else {
  global.logError = (e, custom = {}) => {
    console.error(e, custom);
  };
}
