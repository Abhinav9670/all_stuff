const express = require('express');
const morgan = require('./morgan');
const bodyParser = require('body-parser');
const compress = require('compression');
const methodOverride = require('method-override');
const cors = require('cors');
const helmet = require('helmet');
const { v4: uuidv4 } = require('uuid');
const routes = require('../api/routes/v1');
const { traceNamespace } = require('../utils/global-logger');

const corsOptions = {
  origin: [
    'https://dev.stylifashion.com',
    'https://qa.stylifashion.com',
    'https://uat.stylifashion.com',
    'https://stylishop.com'
  ],
  methods: 'GET,HEAD,PUT,PATCH,POST,DELETE',
  preflightContinue: true,
  optionsSuccessStatus: 204
};

const app = express();

app.use(morgan.successHandler);
app.use(morgan.errorHandler);

app.use(bodyParser.json({ limit: '100mb' }));
app.use(bodyParser.urlencoded({ extended: true }));

// gzip compression
app.use(compress());

app.use((req, res, next) => {
  traceNamespace.bindEmitter(req);
  traceNamespace.bindEmitter(res);

  const traceId = uuidv4();

  traceNamespace.run(() => {
    traceNamespace.set('traceId', traceId);
    next();
  });
});

// lets you use HTTP verbs such as PUT or DELETE
// in places where the client doesn't support it
app.use(methodOverride());

// secure apps by setting various HTTP headers
app.use(helmet());

// enable CORS - Cross Origin Resource Sharing
app.use(cors(corsOptions));

// mount api v1 routes
app.use('/v1', routes);
app.use('/payment/', routes);

module.exports = app;
