const mysql = require("mysql2");
// const { logger } = require('./utils');

// logger.info(`MySQL configuration - Host: ${process.env.MYSQL_HOST}, User: ${process.env.MYSQL_USER}, Database: ${process.env.MYSQL_DB}`);

exports.pool = mysql
  .createPool({
    host: process.env.MYSQL_HOST,
    user: process.env.MYSQL_USER,
    password: process.env.MYSQL_PWD,
    database: process.env.MYSQL_DB,
    port: process.env.MYSQL_DB_PORT || 3306,
    waitForConnections: true,
    connectionLimit: 5,
    queueLimit: 0
  })
  .promise();
