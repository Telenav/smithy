const path = require('path');
const fs = require('fs');

let uiTestFile = path.resolve(__dirname, 'target/model/testit.js');
let filename = 'testit.js';
if (!fs.existsSync(uiTestFile)) {
  uiTestFile = path.resolve(__dirname, 'target/model/BlogServiceClient.js');
  filename = 'BlogServiceClient.js';
}

module.exports = {
  mode: 'none',
  entry: uiTestFile,
  output: {
    path: path.resolve(__dirname, 'dist'),
    filename: filename,
  },
};

