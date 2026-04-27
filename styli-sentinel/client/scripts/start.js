const webpack = require('webpack');
const WebpackDevServer = require('webpack-dev-server');
const config = require('../config/webpackDevServer.config.js'); 

const compiler = webpack(config);
const server = new WebpackDevServer(config.devServer, compiler);

server.startCallback(() => {
  console.log('Starting server on http://localhost:3000');
});
