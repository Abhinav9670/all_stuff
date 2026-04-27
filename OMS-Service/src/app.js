const { NODE_ENV } = process.env;
const fs = require('fs');
const audit = require('express-requests-logger');

const dotenvFiles = [`.env.${NODE_ENV}.local`, `.env.${NODE_ENV}`].filter(
  Boolean
);

dotenvFiles.forEach(dotenvFile => {
  if (fs.existsSync(dotenvFile)) {
    require('dotenv-expand')(
      require('dotenv').config({
        path: dotenvFile
      })
    );
  }
});

if (process.env.ELASTIC_APM_SERVICE_NAME) {
  try {
    const apm = require('elastic-apm-node').start({
      // Override service name from package.json
      // Allowed characters: a-z, A-Z, 0-9, -, _, and space
      serviceName: `${process.env.ELASTIC_APM_SERVICE_NAME}`,
      secretToken: `${process.env.APMKEY}`,
      // Set custom APM Server URL (default: http://localhost:8200)
      serverUrl: `${process.env.ELASTIC_APM_SERVER_URL}`,
      captureBody: 'all'
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
      'CONSUL_HOST',
      'ELASTIC_HOST'
    ];
    const labels = {};

    envLabels.forEach(l => {
      if (process.env[l]) labels[l] = process.env[l];
    });

    global.logError = (e, custom = {}) => {
      try {
        if (apm) {
          const payload = {
            custom: {
              stack: e?.stack,
              ...custom
            }
          };
          apm.addLabels(labels);
          console.error(e);
          if (e && e.map) {
            e.map(error => {
              apm.captureError(error, { ...payload });
            });
          } else {
            apm.captureError(e, { ...payload });
          }
        } else {
          console.error(e);
        }
      } catch (err) {
        console.error(err);
      }
      console.error(e, custom);
    };
  } catch (e) {
    console.error(e);
  }
} else {
  global.logError = (e, custom = {}) => {
    console.error(e, custom);
  };
}
global.logInfo = (key, value) => {
  logInfo(key, value);
};

const express = require('express');
const helmet = require('helmet');
const xss = require('xss-clean');
const mongoSanitize = require('express-mongo-sanitize');
const compression = require('compression');
const cors = require('cors');
const mongoose = require('mongoose');
const bodyParser = require('body-parser');
const cookieParser = require('cookie-parser');
const ApiError = require('./utils/ApiError');
require('./consul-watch');


const passport = require('passport');
const httpStatus = require('http-status');
const config = require('./config/config');

const morgan = require('./config/morgan');

const { authLimiter } = require('./middlewares/rateLimiter');
const routes = require('./routes/v1');
const { errorConverter, errorHandler } = require('./middlewares/error');

// const admin = require('firebase-admin');
const admin = require('../src/config/firebase-admin-config');


const passportCustom = require('passport-custom');
const { logInfo } = require('./utils');
const { ALLOWED_DOMAINS } = require('./constants');
const CustomStrategy = passportCustom.Strategy;

const app = express();

app.use(
  audit({
    // logger: logger, // Existing bunyan logger
    excludeURLs: ['health', 'metrics', 'generatePDF', 'generateCreditMemoPDF'], // Exclude paths which enclude 'health' & 'metrics'
    request: {
      maskBody: ['password'], // Mask 'password' field in incoming requests
      // excludeHeaders: ['authorization'], // Exclude 'authorization' header from requests
      excludeBody: ['creditCard'] // Exclude 'creditCard' field from requests body
      // maskHeaders: ['header1'], // Mask 'header1' header in incoming requests
      // maxBodyLength: 50 // limit length to 50 chars + '...'
    },
    response: {
      // maskBody: ['session_token'], // Mask 'session_token' field in response body
      // excludeHeaders: ['*'], // Exclude all headers from responses,
      // excludeBody: ['*'], // Exclude all body from responses
      // maskHeaders: ['header1'], // Mask 'header1' header in incoming requests
      // maxBodyLength: 50 // limit length to 50 chars + '...'
    }
  })
);

if (config.env !== 'test') {
  app.use(morgan.successHandler);
  app.use(morgan.errorHandler);
}

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
app.use(cors({ origin: '*' }));
// const alowedDomainsarray = ALLOWED_DOMAINS;

// app.use(cors({ origin: alowedDomainsarray }));

// jwt authentication

app.use(passport.initialize());

passport.use(
  'fb',
  new CustomStrategy(function (req, callback) {
    admin
      .auth()
      .verifyIdToken(req.headers.authorization.split(' ')[1])
      .then(decodedToken => {
        req.decodedToken = decodedToken;
        callback(null, decodedToken);
        // const uid = decodedToken.uid;
        // ...
      })
      .catch(error => {
        global.logError(error);
        callback(error, null);
        // Handle error
      });
    // Do your custom user finding logic here, or set to false based on req object
  })
);

// limit repeated failed requests to auth endpoints
if (config.env === 'production') {
  app.use('/v1/auth', authLimiter);
}

// v1 api routes
app.use('/v1', routes);

// send back a 404 error for any unknown api request
app.use((req, res, next) => {
  next(new ApiError(httpStatus.NOT_FOUND, 'Not found'));
});

// convert error to ApiError, if needed
app.use(errorConverter);

// handle error
app.use(errorHandler);

module.exports = app;
