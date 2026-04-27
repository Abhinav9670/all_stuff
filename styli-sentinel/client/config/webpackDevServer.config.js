const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const { merge } = require('webpack-merge');
const webpackConfig = require('./webpack.config.js');

module.exports = merge(webpackConfig, {
  devServer: {
    static: {
      directory: path.join(__dirname, 'public'),  // Replaces contentBase
    },
    compress: true,  // Enable gzip compression
    port: 3000,      // Set your development server port
    hot: true,       // Enable hot module replacement
    open: true,      // Automatically opens the browser
    historyApiFallback: {
      disableDotRule: true,  // Similar to Webpack 4 setup for SPA routing
    },
    client: {
      overlay: false,  // Disable error overlay in the browser
      logging: 'none', // Equivalent to quiet
      webSocketURL: {  // Replaces sockHost, sockPath, and sockPort
        hostname: 'localhost',
        pathname: '/ws',  // Customize WebSocket path if needed
        port: 3000,
      },
    },
    proxy: [
      // {
      //   context: ['/api'],
      //   target: 'http://localhost:3000',
      //   pathRewrite: { '^/api': '' },
      // },
    ],
    watchFiles: ['src/**/*'],  // Watch your source files
  },
});
