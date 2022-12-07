Smithy Tooling
==============

Some tooling for generating Java-based servers using 
[Smithy](https://awslabs.github.io/smithy/2.0/index.html), an Interface Definition
Language for defining network APIs and generating servers and clients from
Amazon.  Amazon uses it internally quite a bit, but the only public, open-source
code generator available is a generator for NodeJS server.

At a high level, what Smithy allows you to do is:

 * Define the web/network API for a service in a simple, high-level language that
   lets you specify details about the service, network operations it supports,
   and arbitrarily complex nested JSON-like data types ("shapes", in Smithy's terminology)
   that are the input and output of each operation
 * Generate all the data types and everything but the business logic of both servers
   and clients of that API
   * Input "shapes" may specify that some fields are derived from HTTP headers,
     some from query parameters or path elements, some from the HTTP payload
     in the case of PUT or POST requests.

The grounding idea behind it is that you do development API-first - so your API
cannot change by accident, and the server cannot fail to implement it;  a pleasant
side effect of that is that the task of writing a server is boiled down to implementing
a few business-logic interfaces.


What's Here
-----------

The tooling here is under development, but the basics work well - it is initially
aimed at Java code generation, but other languages are planned (clients first).
Currently there is:

 * Model type generation (POJOs) - with an emphasis on generating code that
is expressive and pleasant to work with
   * Wrapper types for numbers implement Number; wrapper types for Strings
     implement CharSequence;  list types extend AbstractList and validate
     their contents, and similar for Set and Map types
   * All validation expressions from Smithy are enforced by generated types - an
     invalid instance of a generated model type cannot exist
   * All non-collection model types are immutable
   * The `@builder` trait defined here can be used to simplify model object
     creaton by generating a "builder" class for the type
   * Generated model classes have **no dependencies** outside of the JDK and
     Jackson annotations
   * Generated types return valid JSON from `toString()` with no serialization
     framework needed; and all generated types correctly implement `equals()`,
     `hashCode()`, and, where applicable (numbers, timestamps, strings) `Comparable`.
   * Convenience constructors taking `int` or `double` are provided for types
     that use `short`, `byte` and `float` internally (and the inbound values are
     range-checked)
   * Certain code patterns are recognized in combination with traits that imply
     them
   * The generated code endeavors to be human-readable, and to be, essentially,
     the code you would write for them if you had infinite time and patience.
     Generated code should be beautiful code.
   * Javadoc is thorough and includes both documentation from the smithy model
     and information about any constraints or other information that plays a
     role in code generation and class usage.
 * Server generation (Acteur) - generates a configurable HTTP server using the
Netty-based Acteur server framework
 * Server generation (Vertx) - generates a configurable HTTP server using the
Netty-based Vertx server framework
 * Client generation - generates an HTTP SDK library which uses the same 
generated model classes and provides a straightforward, easy-to-use API for
interacting with the generated server
 * Swagger (OpenAPI) generation - Swagger documentation can be generated from
the model; Acteur-based servers can optionally serve it.
 * Unit test generation for model classes - while mostly a sanity check of the
generation code itself, the generated JUnit 5 tests also serve to prove that
all generated types can be converted to JSON and back, returning an object which
`equals()` the original, which is useful for confidence that the generated code
does what it is supposed to.

Server code generation will generate a set of interfaces for you to implement,
one for each `OperationShape` in your Smithy model (and additional interfaces
to perform authentication if needed).  So you get an SPI (Service Provider Interface)
that lets you plug in the business logic that services requests.

All you need to do to create a working server and clients, once you have a Smithy model,
is implement the SPI interfaces and write a launcher to start the server.

Server code generation also generates a Guice module which allows you
to bind those implementations types and configure and start a server.  The
launcher for the blog-demo project looks like:

```java
    public static void main(String[] args) {
        new BlogService()
                .withReadBlogResponder(ReadBlogResponderImpl.class)
                .authenticateWithAuthUserUsing(AuthImpl.class)
                .withListBlogsResponder(ListBlogsResponderImpl.class)
                .withListCommentsResponder(ListCommentsResponderImpl.class)
                .installing(binder -> {
                    // bind the blog data back end to a folder containing
                    // blog entries
                    binder.bind(Path.class)
                            .annotatedWith(Names.named("blogDir"))
                            .toInstance(Paths.get("/tmp/blog-demo"));
                })
                .start(8123);
    }
```

### Division into projects

Since you generate model classes that are shared between both client and
server, obviously you don't want to ship a server inside your client library -
the model classes should be in their own library, the server code in another
and the client in another.

In addition to that, we can generate server code for multiple frameworks, and
it wouldn't make much sense to mix those (at least for projects here, which
generate more than one set of server code).

So in general, given one Smithy model file in a project, a good division
of code is to split into projects as follows:

 * model - contains the Smithy model file, and is the code-generation destination
for the `model` generation target (and optionally the `modeltest` unit tests).
No human-edited code other than the Smithy model.
 * server-spi - SPI interfaces for implementing business logic - no human-edited code
 * server - generated server code; contains the Guice module for launching the
server, which will have the same name as the `service` the Smithy model defines
 * business-logic - where you will implement the SPI interfaces - you could also
just put this in your server-application project, but it can help keep your code
testable if the business logic does not live in the same place as the details of
how you set up, say, database configuration, etc. so that code does not inadvertently
make assumptions about how it's being run that won't be true for all tests
 * server-application - depends on the business-logic and the server project and
contains code to configure and start the server (say, setting up database bindings,
configuring the server from configuration files and things like that)
 * client - if you are generating a client SDK library

You are not *required* to do all of this stuff initially - in fact, this library
is quite useful if you just want to describe some complex types in Smithy and
generate pleasant-to-use, bulletproof POJOs you don't have to worry about bugs in
and do what you want with them.  And it will work to just generate model, spi and
server all into the same project when building a proof-of-concept.

Do note that *all child directories* of code generation destination folders are
**deleted** when before code generation writes anything.  You do not want to put
code you want to keep under those folders (by default, the plugin generates into
`target/generated-sources/smithy` so you can do what you want in `src/main/java`).


### Smithy Java HTTP Extensions

The business logic interfaces you implement have no direct dependencies on the
web framework you are using.  It *does* use a small library 
called `smithy-java-http-extensions` to abstract out requests, responses, etc.,
so you get called with a thin wrapper over the real request object
(Vertx's `HttpServerRequest` or Acteur's `HttpEvent`), plus a thin wrapper over
a JDK `CompletableFuture` to put your response data in.

If you prefer to use the raw framework type, you can always call, e.g.
`SmithyRequest.unwrap(HttpServerRequest.class)`;  in the case of Vertx, `SmithyRequest`
adds a few niceties, such as the ability to parse common HTTP headers into
appopriate types (dates, cache-control, x-frame-options and more), which 
Vertx does not come with out-of-the-box.


### How It All Gets Built

The smithy-maven-plugin project runs code generation - you start with a Maven
project which contains a Smithy IDL file (typically in `src/main/smithy`) and
which uses the Maven plugin.  There are separate code generators for clients and
various types of servers.  To use them, you need both to set up the a
`<build><plugins>` section in the model project's `pom.xml` and *configure the
plugin's dependencies to include the generators you want to use*.  The sample
project `server-test/blog-service-model` provides an example.

The Maven plugin - and the `smithy-generation` library it uses - has a concept
of *generation targets* - a way of saying what it is you want to build and where
you want to put it - along with the languages you want to generate.  The
`<configuration><destinations>` section of the plugin configuration is where
you direct the destination of source generation by generation target:

```xml
<namespaces>com.telenav.blog</namespaces>

<targets>model,modeltest,server,server-spi,client,docs,vertx-server</targets>

<destinations>
    <client>${basedir}/../blog-client-demo/src/main/java</client>
    <docs>${basedir}/../blog-server-generated-impl/src/main/resources</docs>
    <server>${basedir}/../blog-server-generated-impl/src/main/java</server>
    <server-spi>${basedir}/../blog-server-spi/src/main/java/</server-spi>
    <vertx-server>${basedir}/../blog-server-vertx/src/main/java/</vertx-server>
</destinations>
```

It is also an option to configure each project with the Maven plugin to point
to the model file in whatever project it lives in.


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
   *member* like `structure MyStruct { foo : String }` or the Smithy parser will fail

Smithy has a few limitations which it would be nice to relax - some are design choices
Amazon made based on the kind of services Amazon writes, which are not the kind of services
*everybody* writes:

 * HTTP authentication is all-or-nothing at the service level (use our `@authenticated`
   trait for per-operation authentication which can be optional
 * Traits like `@httpLabel` or `@httpQuery` or `@httpHeader` which mark members of
   input shapes cannot be inherited or indirect - i.e. you cannot have a child-object
   one of whose fields is populated from an HTTP query param, or similar.  It would
   be simple enough to support this sort of thing in code generation.
 * You cannot use a List, Map or Set shape directly as the output of an operation.
   Amazon really, really wants you to paginate results and return a pagination
   token in a fixed length page of results. While that actually *is* good advice in
   many cases, consider cases such as streaming live log records.  The reality is,
   in an async server using an async database driver, assuming you configure your cursor
   with a small batch size, you can serve infinitely large sets of results using
   a finite, and more importantly, *calculable* amount of memory.  Forcing output
   types to always be a container makes such scenarios pointlessly difficult.


Specifics of What's Here
========================

Smithy tooling, specifically:

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
