const path = require('path');
const fs = require('fs');

#set ($nameCaps = $artifactId.substring(0, 1).toUpperCase() + $artifactId.substring(1))

// If configured to generate the generic testing UI, there will be
// a testit.js file in src, translated to javascript in target/

let uiTestFile = path.resolve(__dirname, 'target/model/testit.js');
let filename = 'testit.js';

// If there is no testit.js, then we are just generating the javascript client
// library

if (!fs.existsSync(uiTestFile)) {
  uiTestFile = path.resolve(__dirname, 'target/model/${nameCaps}ServiceClient.js');
  filename = '${nameCaps}ServiceClient.js';
}

// Start with mode: 'none' - for production you will want mode: 'production', but
// readable javascript is preferable during development.

module.exports = {
  mode: 'none',
  entry: uiTestFile,
  output: {
    path: path.resolve(__dirname, 'dist'),
    filename: filename,
  },
};
