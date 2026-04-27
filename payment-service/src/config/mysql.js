const mysql = require('mysql2-promise')();
mysql.configure({
  host: process.env.MYSQL_HOST,
  user: process.env.MYSQL_USER,
  password: process.env.MYSQL_PWD,
  database: process.env.MYSQL_DB,
  port: process.env.MYSQL_DB_PORT || 3306
});

module.exports = mysql;
