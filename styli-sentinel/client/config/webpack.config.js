const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const webpack = require('webpack');
const getClientEnvironment = require('./env');
const paths = require('./paths');

const env = getClientEnvironment(paths.publicUrlOrPath.slice(0, -1));

module.exports = {
  entry: './src/index.js',
  output: {
    path: path.resolve(__dirname, '../build'),
    filename: '[name].[contenthash:8].js',
    clean: true,
    publicPath: paths.publicUrlOrPath
  },
  module: {
    rules: [
      {
        test: /\.(js|jsx)$/,
        exclude: /node_modules/,
        use: [
          {
            loader: 'babel-loader',
            options: {
              presets: ['@babel/preset-env', '@babel/preset-react'],
              plugins: ['jsx-control-statements']
            }
          }
        ]
      },
      {
        test: /\.css$/,
        use: [process.env.NODE_ENV === 'production' ? MiniCssExtractPlugin.loader : 'style-loader', 'css-loader']
      },
      {
        test: /\.scss$/,
        use: [
          process.env.NODE_ENV === 'production' ? MiniCssExtractPlugin.loader : 'style-loader',
          {
            loader: 'css-loader',
            options: {
              url: {
                filter: (url, resourcePath) => {
                  // Return true to handle the url normally if it starts with /assets
                  return url.startsWith('public/assets');
                }
              }
            }
          },
          'sass-loader'
        ]
      },
      {
        test: /\.(png|jpe?g|gif|svg|webp)$/i, // Add file types you want to handle
        type: 'asset/resource', // Use asset/resource module type for handling files
        generator: {
          filename: 'assets/[name][ext][query]' // Customize the output path if needed
        }
      }
    ]
  },
  resolve: {
    alias: {
      '@assets': path.resolve(__dirname, '../public/assets'), // Points to the correct location
      '/assets': path.resolve(__dirname, '../public/assets') // This line adds explicit support for /assets in SCSS
    },
    extensions: ['.js', '.jsx', '.scss']
  },
  plugins: [
    new HtmlWebpackPlugin({
      template: './public/index.html',
      favicon: './public/favicon.ico'
    }),
    new CopyWebpackPlugin({
      patterns: [
        {
          from: path.resolve(__dirname, '../public/assets'), // Copy from the actual public/assets directory
          to: path.resolve(__dirname, '../build/assets') // Ensure it lands in the correct build folder
        }
      ]
    }),
    new webpack.DefinePlugin({
      'process.env.NODE_ENV': JSON.stringify(process.env.NODE_ENV || 'development'),
      'process.env.API_URL': JSON.stringify(process.env.API_URL || '')
    }),
    new MiniCssExtractPlugin({
      filename: 'static/css/[name].[contenthash:8].css',
      chunkFilename: 'static/css/[name].[contenthash:8].chunk.css'
    }),
    new webpack.DefinePlugin(env.stringified)
  ],
  mode: process.env.NODE_ENV == 'production' ? process.env.NODE_ENV : 'development',
  stats: {
    errorDetails: true // This will show detailed error messages
  },
  optimization: {
    splitChunks: {
      chunks: 'all', // Splits vendor and app code for better caching
    },
    runtimeChunk: 'single',
  },
};
