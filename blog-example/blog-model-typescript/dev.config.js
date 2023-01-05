const path = require('path');
//import * as path from 'path';
//import * as webpack from 'webpack';

/*
export var mode = 'development';
export var entry = './target/testit.js';
export var output = {
    path: path.resolve(__dirname, './dist'),
    filename: 'testit.bundle.js',
}
*/


module.exports = {
  mode: 'development',
  entry: path.resolve(__dirname, 'target/testit.js'),
  output: {
    path: path.resolve(__dirname, 'dist'),
    filename: 'testit.js',
  },
};

