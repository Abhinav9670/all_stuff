'use strict';

const fs = require('fs');
const path = require('path');
const Sequelize = require('sequelize');
const basename = path.basename(__filename);
// const env = process.env.NODE_ENV || 'development';
const config = require('../../../config/config.js')["archive"];

const db = {};

const archiveSquelize = new Sequelize(
  config.database,
      config.username,
      config.password,
      config
);

fs.readdirSync(__dirname)
  .filter(file => {
    return (
      file.indexOf('.') !== 0 &&
      file !== basename &&
      file !== path.basename('index.js') &&
      file.slice(-3) === '.js'
    );
  })
  .forEach(file => {
    const model = require(path.join(__dirname, file))(
      archiveSquelize,
      Sequelize.DataTypes
    );
    db[model.name] = model;
  });

Object.keys(db).forEach(modelName => {
  if (db[modelName].associate) {
    db[modelName].associate(db);
  }
});

db.archiveSquelize = archiveSquelize;
db.archiveSquelize
  .authenticate()
  .then(() => {
    console.log(`${config?.database}Archive Database connection has been established successfully.`);
  })
  .catch(err => {
    console.error('Unable to connect to the database:', err);
  });
module.exports = db;
