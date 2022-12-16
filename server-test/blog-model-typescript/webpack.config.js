const path = require('path');

module.exports = {
  mode: 'none',
  entry: path.resolve(__dirname, 'target/model/testit.js'),
  output: {
    path: path.resolve(__dirname, 'dist'),
    filename: 'testit.js',
  },
};

