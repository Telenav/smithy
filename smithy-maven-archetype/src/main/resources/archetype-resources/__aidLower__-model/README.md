#set ($nameCaps = $artifactId.substring(0, 1).toUpperCase() + $artifactId.substring(1))
$nameCaps Model
===============

This project contains the Smithy model file for the $nameCaps service - it is the only
user-editable source file here.

On build, this project generates code into its own source directories, and
a number of its sibling projects.  Model - data-binding classes are generated
into `target/generated-sources/smithy` in this project, and if enabled,
unit tests for those projects are generated into `src/test/java`.

Edit the Smithy model and rebuild this project and its siblings to test your
changes.


Generated Tests
---------------

If the `typescript.modeltest` destination is enabled in the model project, then
tests of model types will be generated into `test-src/generated` in this project.

The tests are run with NodeJS, and prove that the generated model types, by
invoking `npm run test`.  If any output file is passed as an argument, a report
will be written to the passed file if there are any failues.

* Serialize and deserialize from JSON to an identical object as the input (since
the generator code uses wrapper types to provide default values and validation,
this is worth testing)
* Fields are set correctly
* Any `@jsonName` smithy traits applied to structure members are used correctly
(e.g. the field name might be an escaped keyword, or some other name from the model,
but the JSON looks like it is supposed to)

To build the tests (once the code has been generated), run `npm run test-build`.

This will generate test code into `test-src/generated`, which is called by
`test-src/test.ts`.  The built, Javascript tests, will be in `test`, which is
gitignored.

To run them, run `npm run test`. If there are any failures, a synopsis will be
printed to the console, and a complete report containing input and output and
problems will be written to `test/test-report.json` (this destination can be
overridden by passing a file path as a final argument to `npm run test`).

The `test-src/test.ts` file runs the tests, and can be added to if desired (instructions
in a comment in the file).  The test framework is a trivial test runner generated
into `test-src/generated/test-support.ts`, so as not to impose any particular
dependencies on the generated code.


### What Generated Tests Are Good For

While the main reason generated tests were developed was as a sanity check of the
code generation tooling itself, it can also be useful for you, as the end user of
these types, to gain confidence that they work as intended.
