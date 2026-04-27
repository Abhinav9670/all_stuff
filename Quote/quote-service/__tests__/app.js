require("dotenv-expand")(
  require("dotenv").config({
    path: require("find-config")(
      ".env.test.local"
    ),
  })
);
const express = require('express');
const audit = require('express-requests-logger');
const bodyParser = require('body-parser');
// const { initConsul } = require('./config/consul');
// const { startKafkaProducer } = require('./helpers/kafka/producer');

const { validateJwt } = require('../helpers/jwt');
const mysql = require('mysql2');

// initConsul();
// startKafkaProducer();
// const { kafkaConsumers } = require('./consumers')
// kafkaConsumers()

const app = express();

app.disable('x-powered-by');

app.use(bodyParser.json());
app.use(bodyParser.urlencoded({ extended: true }));

//Import all routes

app.use(express.json());
app.use(express.urlencoded({ extended: true }));

app.use(
    audit({
      // logger: logger, // Existing bunyan logger
      excludeURLs: ["health", "metrics"], // Exclude paths which enclude 'health' & 'metrics'
      request: {
        maskBody: ["password"], // Mask 'password' field in incoming requests
        // excludeHeaders: ['authorization'], // Exclude 'authorization' header from requests
        excludeBody: ["creditCard"], // Exclude 'creditCard' field from requests body
        // maskHeaders: ['header1'], // Mask 'header1' header in incoming requests
        // maxBodyLength: 50 // limit length to 50 chars + '...'
      },
      response: {
        // maskBody: ['session_token'], // Mask 'session_token' field in response body
        // excludeHeaders: ['*'], // Exclude all headers from responses,
        // excludeBody: ['*'], // Exclude all body from responses
        // maskHeaders: ['header1'], // Mask 'header1' header in incoming requests
        // maxBodyLength: 50 // limit length to 50 chars + '...'
      },
    })
  );
  const connection = mysql.createConnection({
    host: process.env.MYSQL_HOST,
    user: process.env.MYSQL_USER,
    password: process.env.MYSQL_PWD,
    database: process.env.MYSQL_DB,
    port: process.env.MYSQL_DB_PORT || 3306,
  });
  connection.end();
  const routes = require("../routes/api.js");
  
  app.use(
    "/",
    (req, res, next) => {
      res.setHeader('Content-Type', 'application/json');
      if (
        req?.headers?.origin?.indexOf("stylishop") !== -1 ||
        req?.headers?.origin?.indexOf("stylifashion") !== -1
      ) {
        res.header("Access-Control-Allow-Origin", req.headers.origin);
      }
      res.setHeader(
        "Access-Control-Allow-Headers",
        "authorization,content-type,x-auth-token,token,x-source,x-client-version,x-header-token"
      );
      res.setHeader(
        'Cache-Control', 'no-cache, no-store, max-age=0'
      )
      res.setHeader('Access-Control-Allow-Methods', '*');
    
      if (req.method === "OPTIONS") {
        res.status(200);
        return res.send("ALLOWED");
      }
      if (
        req.path !== "/rest/quote/health-check" &&
        req.path !== "/rest/quote/couch-health-check" &&
        req.path !== "/rest/quote/customer-health-check" &&
        req.path !== "/rest/quote/address-health-check" &&
        req.path !== "/rest/quote/auth/v5/view/metadata" &&
        req.path !== "/rest/quote/auth/v5/view/validate" &&
        req.path !== "/rest/quote/auth/v5/getNew" &&
        req.path !== "/rest/quote/webhook/price-drop" &&
        req.path !== "/rest/quote/webhook/free-shipping" &&
        !req.path.includes("rest/quote/docs/") &&
        req.method !== "OPTIONS"
      )
      {
        validateJwt(req, res);
      } 
      if (!(res.statusCode === 500 || res.statusCode === 400 || res.statusCode === 401)) next();
    },
    routes
  );


  const mongoUtil = require('../config/mongoInit.js');
  mongoUtil.connectToServer((err, client) => {
    if (err) {
      console.log('Error Connecting to Mongo @connectToServer', err);
    }
    app.db = client;
  });
module.exports = app;
