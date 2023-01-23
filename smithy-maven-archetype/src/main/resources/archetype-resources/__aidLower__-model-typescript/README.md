#set ($nameCaps = $artifactId.substring(0, 1).toUpperCase() + $artifactId.substring(1))
$nameCaps Typescript Model and Client Library
=============================================

This project contains the following:

* Generated Typescript model classes for all of the data-modeling types defined in the
  Smithy model in the `\${artifactId}-model` project that is a sibling to this one,
  generated into `src/model/\${nameCaps}ServiceModel.ts` when that project is built
* A Typescript client library which uses the model classes, generated into
  `src/model/\${nameCaps}ServiceClient.ts`
* Generated unit tests of the typescript data model classes, which prove that they
    * Can be translated into and out of JSON losslessly
    * Implement validation, to enforce constraints from the Smithy model such as
      `@length` or `@pattern` or `@range` correctly

Typescript is converted into Javascript via `npm run build`, which runs `tsc`, the
typescript compiler.

The contents of the `src/` directory are wiped by code generation (and that directory
is included in `.gitignore`).


Build And Run
=============

By default, building the adjacent model project will cause `npm run build` to be run by
the `smithy-maven-plugin` if `npm` can be found on the path.  It will also perform the
initial `npm install` to download dev-time NodeJS libraries if no `node_modules/` folder
is present.


### Other Build Targets

The `package.json` adds a few other build targets, also invokable using `npm run`:

* `build` - build generated sources in `src/`
* `dist` - use `webpack` to build the javascript compiled by `tsc` into optimized form
  (which can be configured by editing `webpack.config.cjs`) into `dist/`.
* `clean` - clean previous build products
* `clean-all` - clean build products *and wipe the source directory of generated sources*
* `test-build` - run `tsc` to build generated test sources in `test-src/generated` into
  Javascript


Project Layout
==============

* `src/` - destination for generated sources, wiped on code generation
* `test-src/` - contains the base `test.ts` source file, which imports and sets up
  the tests that are generated into `test-src/generated`
* `target/` - destination for compiled Javascript files
* `dist/` - destination for optimized Javascript files derived from the compiled ones by webpack
* `test/` - destination for test code compiled into Javascript

Other files in the project root:

* `package.json` - NodeJS project configuration file, used to build your code with `npm`
* `copy-markup` - Used by the build targets to copy html and css to build directories
* `tsconfig.json` - Typescript compiler configuration
* `tsconfig-test.json` - A separate typescript compiler configuration for compiling test
  sources into `test/` so they don't wind up mixed into the code you would deploy
* `webpack.config.cjs` - Webpack configuration (needs the `.cjs` file extension, as we
  are using ES6 modules for our sources, and Webpack insists on NodeJS-style imports)
* `rebuild` - clean and build

Dependencies
============

The generated code has *no external dependencies* - generated code should not impose library
choices unless it is absolutely necessary.

There are a few *development time* dependencies defined in `package.json` - those libraries
used by the build tools:

* `rimraf` - cross-platform `rm -Rf`
* `typescript` - the Typescript to Javascript compiler
* `webpack` - the Javascript optimizer/munger/merger
  * `webpack-cli` - also required for webpack
* `xmlhttprequest` - NodeJS implementation of the browser's XmlHttpRequest API so that you
  can write tests that call a live server and run as if in a browser as part of the build
  (no such tests are currently generated)


Generated Tests
---------------

Tests of the generated model classes, which prove that validation and JSON serialization work
as advertised, are generated into `test-src/generated`.  You can freely edit `test-src/test.ts`
to add additional tests.

Sample code for invalid values used in those tests, to deliberately create invalid data,
is taken from the `@samples` trait if present on string shapes (invalidity tests are only
generated if the framework knows for sure it can really generate an invalid value).  For
strings with the `@pattern` regular expression test, the generation framework will use
the [Xeger](https://github.com/Telenav/smithy/tree/main/xeger) library to reverse engineer
matching and non-matching strings from the regular expression.

