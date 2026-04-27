require('dotenv').config({
  path: require('find-config')(`.env${process.env.NODE_ENV === 'development' ? '.development.local' : ''}`)
});

module.exports = {
  env: process.env.NODE_ENV,
  port: process.env.PORT || 5001,
  logs: 'combined'
};
