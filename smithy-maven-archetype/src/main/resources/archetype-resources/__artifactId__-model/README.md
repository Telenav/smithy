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

