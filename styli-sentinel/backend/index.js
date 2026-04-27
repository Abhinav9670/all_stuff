const app = require('./src/app');

require('./src/config/mongodb');
require('./src/config/consul-config');

app.listen(process.env.PORT, () =>
  console.log(`server started on port ${process.env.PORT} (${process.env.NODE_ENV_ALT})`)
);
