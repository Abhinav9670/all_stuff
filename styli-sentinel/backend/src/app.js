require('./config/apm');
const express = require('express');
const bodyParser = require('body-parser');
const xss = require('xss-clean');
const cors = require('cors');
const helmet = require('helmet');
const mongoSanitize = require('express-mongo-sanitize');
const compression = require('compression');
const cookieParser = require('cookie-parser');

const routes = require('./routes/index');

const { ValidationError } = require('express-validation');
const { getApigeeProxyPath } = require('./helper/utils');

const app = express();

app.use(express.json({ limit: '5mb' }));
app.use(cookieParser());

// set security HTTP headers
app.use(helmet());

// parse json request body
app.use(express.json());
app.use(bodyParser.json());
app.use(bodyParser.urlencoded({ extended: false }));
// parse urlencoded request body
app.use(express.urlencoded({ extended: true }));

// sanitize request data
app.use(xss());
app.use(mongoSanitize());

// gzip compression
app.use(compression());

// enable cors
app.use(cors());
app.options('*', cors());

// Middleware to set response content type as JSON globally
app.use((req, res, next) => {
  res.setHeader('Content-Type', 'application/json');
  next();
});

app.use((req, res, next) => {
  const startTime = Date.now();
  const originalSend = res.send;
  res.send = function (body) {
    console.log(
      `sentinel: ${JSON.stringify({
        method: req.method,
        url: req.originalUrl,
        duration: `${Date.now() - startTime}ms`,
        request: req.body,
        response: body
      })}`
    );
    originalSend.apply(res, arguments);
  };
  next();
});
function registerRoutes(app, basePath, router) {
  let routePrefix = getApigeeProxyPath();
  app.use(routePrefix + basePath, router);
  app.use(basePath, router);
}
registerRoutes(app, '/', routes);

//express-validation
app.use(function (err, req, res, next) {
  if (err instanceof ValidationError) {
    return res.status(err.statusCode).json(err);
  }
  return res.status(500).json(err);
});

module.exports = app;
