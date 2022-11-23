Smithy Tooling
==============

Some tooling for [Smithy](https://awslabs.github.io/smithy/2.0/index.html).

Quick-Start
-----------

To play with code-generation from Smithy models and get a sense of what you can
do with it

 1. Check out this repository
 2. Build everything once - `mvn -Dmaven.test.skip=true install`
 3. In your IDE (or wherever), open the project `test/maven-plugin-test`
 4. In that project, open `src/main/smithy/Playground.smithy`
 5. Try changing properties, adding or removing things, adding new structures or types
 6. After making a change, build, and examine the generated code (also try building Javadoc for it)
 7. If you want to examine the JSON representation of your code, create a class in the
    main sources with a main method that creates a Jackson `ObjectMapper` and prints
    out an object as JSON (there is an example class that tests union types already there)

There are three Smithy IDL files in this project:

 * `Playground.smithy` - contains structures for a people and meetings, to demonstrate
   some things you can do with Smithy.  It also shows the use of constraints (both our
   own *and* built-in ones)
 * `Blog.smithy` - a Smithy sketch of a simple blog engine with entries and comments
 * `Sample.smithy` - for internal use - contains a boatload of shapes to verify that
   generation works correctly

What you will notice:

 * We not only generate POJO types - we generate JUnit 5 unit tests for them!  The tests
   verify that Java serialization and JSON serialization both give you back the an
   identical object to what you serialized, that constraints are enforced correctly
   and getters and other methods do what they are supposed to
   * *Caveat:* If you define a `string` type that uses `@pattern` to enforce a regular
     expression, and want tests for it, you need to add our `@samples` annotation with
     valid and invalid examples for tests to use - we do not (currently?) synthesize
     valid and invalid input by parsing regular expressions (there are a few libraries
     out there that do do that, but those I've tried are capable of going into an
     endless loop on some input)
   * Test generation is less, uh, tested, than the rest
 * Pojo types that are wrappers for single values - numbers, strings, etc. - all use
   Jackson's `@JsonValue` for serialization - so the wrapper type is serialized as the
   plain value - a wrapper around, say, a `String` serializes as `"the string"`, not
   `{ value : "the string" } - so the on-the-wire format for objects from generated code
   is exactly the JSON format you would expect from reading the schema
 * Smithy `list`, `set` (really a `list` with the `@uniqueItems` trait - `set` is deprecated)
   and `map` traits all result in an implementation of AbstractList / AbstractSet / AbstractMap
   that wrap and proxy an underlying list/set/map and enforce any constraints on the
   target type
 * All types enforce the constraints described in the IDL file - so it is impossible to
   create invalid instances of any type
 * Pojo types are entirely immutable and final
 * Collection types, by their nature, are mutable - but have a method to convert them
   to an instance backed by a private copy of the collections wrapped in an unmodifiable
   list - in the long run, we should use that for inbound wire data
 * Types marked as `@mixin` are generated as Java interfaces that are implemented by
   types that use them
 * Structures marked with the `@builder` trait wind up with a `builder()` method and a
   generated builder class (use `@builder("flat")` to generate fewer
   builder classes at the price of trying to build an invalid object being a runtime
   instead of compile-time error)

If `<debug>true</code>` is set in the configuration section for the smithy-maven-plugin
then the generated code will contain line-comments that show the class, source file and
line that caused the next line of code to be generated - this is useful when tracking
down why some code is the way it is.

Please examine the generated code for anything that looks incorrect - bear in mind,
*complexity* is fine in generated code - generated code should be efficient, and is an
appropriate place for optimizations you wouldn't write by hand (but would benefit from
if you did).  But incorrectness is failure.

#### Caveats and Smithy Quirks

We are using the parser for Smithy models from the Smithy project, which has some quirks:

 * Type names can be lower-cased when defining a simple type like
   `string Foo` - but the same type names must be capitalized when used in a structure
   *member* like `structure MyStruct { foo : String }`

The `test/` project is not currently in the build of the main pom - an issue with the
plugin not running in a Maven multi-module build, to be diagnosed.


What's here?
============

Embryonic Smithy tooling, specifically:

 * `smithy-generators` - a generic framework for smithy generation with
   settings, sessions, and ways to look up generators that support specific
   languages, language versions and generation targets
 * `smithy-maven-plugin` - Maven plugin that calls `smithy-generators`, adapts
   maven configuration to generator configuration, etc.
 * `simple-smithy-extensions` - Defines a few custom traits we use in Java code
   (and possibly eventually elsewhere):
   * `@builder` - mark a structure as wanting a builder class (and `builder()`
      method generated for it
   * `@identity` - if this appears on one or more structure members, then only
      those members are considered in `equals()` and `hashCode()` methods
   * `@samples` - allows string (and eventually other) members or types to list
      valid and invalid examples - we use these both in documentation, and to
      generate unit tests that prove that code generation and JSON and Java
      serialization work as advertised - if a string type or string property
      specifies the `@pattern` trait, then we need examples that do and do not
      match the pattern, so tests can ensure that validation works correctly.
 * `simple-smithy-extensions-java` - Uses the structure generation SPI in
   `smithy-java-generators` to inject contributors to code-generation to
   replace the `equals()` and `hashCode()` generators for structures using
   `@identity`, inject constructor and constructor argument annotations, and
   javadoc contributions for them.
 * `smithy-java-generators` - The motherlode - Java code generators for Smithy
   model classes - things that aren't server-specific shapes such as operations,
   but result in generic pojos.  Covers
   * Generating validating wrapper types for all of the basic smithy types
   * Generating validating wrapper types for lists, maps and sets
   * Generating structure types that aggregate primitive or model-defined types
   * Support for *tagged union* types (aka "one-of" types, where the value can be
     one of several different types) with correct JSON serialization and deserialization
   * (Experimental) Generating unit tests of the generated types - useful to
     ensure that code generation works correctly.  All tests ensure that Java
     and Jackson-based-JSON serialization functions correctly, along with any
     constraints.
 * `test/maven-plugin-test` - a project that uses the Maven plugin and contains
   a couple of smithy source files that it generates code, builders and tests
   from.  The `pom.xml` file is useful as a template for using the plugin.
 * `smithy-java-http-extensions` - defines a custom exception type in order for
   server code to in order to differentiate validation problems as bad requests;
   eventually will contain generic APIs for a few other things to avoid code
   generation from being tied to a specific server framework.
* `smithy-openapi-wrapper` - if included in the `<dependencies>` section where
   using the Maven plugin, will generate Swagger documentation from your smithy model
 * `smithy-simple-server-generator` - generates an HTTP server

Other Stuff
-----------

 * `smithy-antlr` - a quick and dirty Antlr grammar for Smithy IDL files
 * `smithy-netbeans-plugin` - a NetBeans plugin for Smithy files that uses
   that grammar.  Requires 
   [the Antlr language-support modules to be installed](https://github.com/timboudreau/ANTLR4-Plugins-for-NetBeans).


Obsolete Stuff Still Being Mined for Code
-----------------------------------------

 * `smithy-pojo-generator` - the pojo and complete server and client generator
   demo prototype.
 * `smithy-extensions` - extensions used in the prototype demo - generic ones
   already moved to `simple-smithy-extensions` - http-specific ones pending
 * `demo` - has subprojects from the original prototype - most empty, since the
   code was nearly entirely generated

Using The Maven Generation Plugin
==================================

The plugin takes a few settings that are important:

```xml
<languages>java</languages>
```

The only language supported currently (and the default if unspecified).

```xml
<targets>model,modeltest</targets>
```

A generation run is to generate a set of *generation-targets* - what kind of code
to generate - 

 * model (pojos)
 * modeltest (tests of pojos)
 * server
 * client

These are adhoc strings, so you can define your own, but you need some 
`SmithyGenerator` to agree that it generates code for that target.

```xml
<namespaces>my.test.ns,sample.blog</namespaces>
```

Smithy files have a namespace which translates (loosely) to a Java package
name (we append suffixes such as "model" and "client" so that these can be
in separate projects without resulting in a Java package split across JARs).

This is where you list the namespaces you want to build (since there may be
visible Smithy files that are consumed by your model, but already have classes
generated in the JAR file that contains the Smithy file - you don't want to
generate every shape in sight).

### Dependencies of *the plugin*

What generators and extensions are actually run as part of a generation
pass also depend on what JARs are on the *classpath of the plugin* - you
don't want to lug around dependencies on a bunch of code-generators at
runtime, so code generators should not be direct dependencies of your
project - they should be specified as dependencies in the `<plugin>` section
for the `smithy-maven-plugin`.

See `test/maven-plugin-test` for an example of a complete configuration
that works.

To-Do
=====

 * Support for `Blob` (byte array, possibly base64 encoded) types - the input
   is likely to be framework dependent (could be `byte[]`, `InputStream`, `ByteBuffer`,
   or Netty's `ByteBuf`)
 * Support for `Document` types (analogous to *any javascript object type*) - the
   best way to model it would be Jackson's internal JsonNode tree, but forcing a
   dependency on Jackson internals is unacceptable.  Possibly a similar approach
   to what we do for union types would work.
 * Add an `@unsigned` trait for numbers and generate appropriate wrapper types?
 * Default values are only possible for structure members - but it would be possible
   to add our own trait for applying them to simple structures
 * There is a *very* granular API for *structure* generation that allows extensions
   to inject things like individual constructor argument annotations (that is how
   we construct the annotations used by builder generation).  There is currently
   no similar support for wrapper types and collection types.  Extending that to be
   used for all types would facilitate moving Jackson support to a separate extension,
   rather than requiring it as a dependency.

Extensibility and API
=====================

All pluggable stuff uses the *Java extension mechanism* (`META-INF/services` files)
to register things on the classpath.

Current extension points:

 * `ModelExtensions` - allows a library to register its own smithy file and custom
   traits so they can be `use`d from smithy files being built
 * `SmithyGenerator` - allows a library to register a code-generator factory that will
   be passed all of the shapes in the smithy model, to generate some code from any of
   them it wants - it is passed the generation target and language being generated
   as well as the model, to use in deciding if and what to generate
 * `StructureExtensions` - allows a library to contribute to or replace Java code-generation
   for Java classes at both a coarse and an extremely granular level, such as injecting
   parameter annotations for constructor arguments, annotations for other class members,
   contributing to equals(), toString() and hashCode() computation, enhancing javadoc,
   and more.  `BuilderExtensionsJava` and `StructureIdentityExtensionsJava` in the
   `simple-smithy-extensions-java` project are both examples of usage.


Useful Custom Traits
====================

We define a few traits in `simple-smithy-extensions` that fill holes and allow us to
generate easier-to-use model classes:

 * `@fuzzyNameMatching` - applicable to enum types, allows JSON containing lower-cased
   enum member names and/or substituting '-' for '_'
 * `@identity` - tells the code generation infrastructure that `equals()` and `hashCode()`
   should only use the so-annotated members for equality tests - this is useful for types
   that have one field which is a primary key in the database
 * `@builder` - generated code should contain a builder for the type
 * `@samples` - provide samples of valid and invalid values - these are used both for
   documentation and for generating unit tests of pojo classes. *If you use the `@pattern`
   annotation for string members and want to generate unit tests for your POJOS, you **must** 
   include samples* - we do not reverse-engineer valid and invalid values from regular 
   expressions.  Example: `@samples(valid : ["xx", "yy"], invalid : ["x", "y"])`.
 * `@units` - mark an enum as being a *unit* - each member must have `@units` followed by
   a number, and *one of the members must have the value `1`*.  This results in two things:
   The generated Java enum will have conversion methods that take a number and a unit
   (following the same pattern as the JDK's `TimeUnit`'s `convert()` method), **and**
   any structure type which consists of an instance of the enum and a number - the amount 
   pattern - will *also get conversion methods* - so you will get methods that let you
   do things like `someDistance.to(MILES)` for free.
 * `@authenticated` - mark an operation as requiring authentication - authentication
   can be optional (imagine a blog engine where the blog owner can see unmoderated comments,
   but all users can make the same call but not see them), and takes an ad-hoc string describing
   the kind of authentication.  Using this trait will result in an additional interface you
   need to implement in your server project, one for each distinct string.  If unspecified,
   `"basic"` is assumed, for HTTP basic authentication (but you can implement the interface
   to do whatever you want)
