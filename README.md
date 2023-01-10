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
    <client>${basedir}/../blog-generated-client-sdk/src/main/java</client>
    <docs>${basedir}/../blog-server-generated-impl/src/main/resources</docs>
    <server>${basedir}/../blog-generated-acteur-server/src/main/java</server>
    <server-spi>${basedir}/../blog-generated-business-logic-spi/src/main/java/</server-spi>
    <vertx-server>${basedir}/../blog-generated-vertx-server/src/main/java/</vertx-server>
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
   and resources, but result in generic pojos.  Covers
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
 * `smithy-server-generation-common` - shared code for processing a model into generated
   HTTP operation implementations which is used by both the Acteur and VertX server
   generation libraries
 * `smithy-simple-server-generator` - generates an HTTP server using the Acteur framework
 * `smithy-vertx-server-generator` - generates an HTTP server using the Vertx framework
 * `smithy-ts-generator` - generates Smithy model classes and an SDK library
    for Typescript and Javascript (typically you generate typescript code into an adjacent `npm`
    managed project which uses `tsc` to compile Typescript and `webpack` to convert that
    into a single javascript source);  if present when building, the generated code will
    be zipped and included and made servable by any generated server projects.  Optionally,
    by including `<generate-test-ui>true</generate-test-ui>` in the `<settings>` section
    of your `smithy-maven-plugin` configuration, it can generate a *minimal* but functional
    HTML web UI with forms for calling each operation the model defines.
 * `typescript-vogon` - low level code generation library for generating Typescript
   code, with a similar API to that of `com.mastfrog:java-vogon` which we use for Java
   code generation.
 * `blog-example` - subprojects that utilize all of the above to generate an API
   for a blog engine, implements it, and contains both Acteur and VertX server
   applications (projects in **`bold`**, listed first, are ones containing user-editable
   code)
    * **`blog-service-model`** - Contains the Smithy model for the blog web API in
      `src/main/smithy/BlogService.smithy`.  The classes that model data for the blog
      model are generated into `src/main/java` here; generated tests for those classes
      are generated into `src/test/java` here.
    * **`blog-acteur-application`** - Application launcher which binds the business
      logic SPI implementation in `blog-spi-over-demo-backend` for the Acteur server
    * **`blog-vertx-application`** - Application launcher which binds the business
      logic SPI implementation in `blog-spi-over-demo-backend` for the VertX server
    * **`blog-demo-backend`** - A simple back-end that serves blog entries from a
      symlink farm of local files;  contains a starter set of blog entries which are
      unpacked into a temporary directory on first launch so the demo server applications
      are usable immediately.
    * **`blog-spi-over-demo-backend`** - Implements the generated server SPI interfaces
      for responding to HTTP requests (used by both server implementations) that is
      generated into `blog-generated-business-logic-spi` to call `blog-demo-backend` to
      retrieve data.
    * `blog-generated-business-logic-spi` - single-method SPI interfaces you implement and
      bind in your server launcher to implement the business logic of each Operation (HTTP
      request type) defined in the Smithy model.
    * `blog-generated-acteur-server` - a generated server application, which is launchable
      by itself, which implements and HTTP server using the Acteur framework, for the blog
      web API.  Contains a Guice module called `BlogService` which you can call methods on
      to bind SPI implementation classes and launch the server;  the application projects
      simply do that.
    * `blog-generated-vertx-server` - a generated server application, which is launchable
      by itself, which implements and HTTP server using the VertX framework, for the blog
      web API.  Contains a Guice module called `BlogService` which you can call methods on
      to bind SPI implementation classes and launch the server;  the application projects
      simply do that.
    * `blog-generated-client-sdk` - A generated Java SDK library which can call any server
      that implements the web API described in `BlogService.smithy`. using the JDK's built-in
      HTTP client

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

Generated Code Style
====================

A mini FAQ, anticipating a few questions:

Q: Why are all the generated classes final? I want to subclass things!
A: First, this is concurrent programming - async programming always is. Immutable objects are thread-safe.
Second, `final` is the most powerful tool in the Java language for making entire *classes* of bug into
impossibilities.  Whenever you code a non-final field in Java, you should feel like you're walking around
with your zipper down and your shoes untied - **because you are**.  Third, generated types are work-animals,
not pets - if they could be subclassed, that creates a whole new category of ways for things to break when
you change your model.  It's not worth it.

Q: You could generate supertypes for your classes and share some logic, or use *library X* that has
fabulous support for *Y*.
A: The goal of the generated code - particularly model types - is **clarity** - as in, you can look at
it and immediately see exactly what it's doing, with as little indirection as possible.  Introducing
dependencies - local or library - makes that harder to do, and introduces new ways things can break.
Yes, a little memory for bytecode could be saved, but bytecode size in not where memory usage
problems come from.


Code Generation Walk-through, Blow By Blow
==========================================

There is nothing magical to code generation - it's just

 * Load the smithy model(s)
 * Find all the generators
 * Pass shapes to them
 * Run the model element generators you got back
 * Write the code they generated to disk
 * Run any post-generation tasks (e.g. zipping generated files, stuff like that)

Since what all is happening under the hood when you build a smithy model into code, might
not be obvious, here is a more detailed walk-through of what happens under the hood:

1. The Maven plugin runs, because it is configured to in the `pom.xml` file of a project.  The
actual Smithy generation code does not care about Maven - but the `smithy-generators` project itself
does not actually contain any code generators - it just defines an API for plugging in generators
for specific languages and phases, and looks them up on the classpath via
[the Java Extension Mechanism](https://docs.oracle.com/javase/tutorial/ext/) - aka the JDK's `ServiceLoader`.
And plugins may look for configuration that applies to them, which the Maven plugin allows us to
configure in the `pom.xml`.  So the actual generators to run are specified as `<dependencies>` of
*the plugin* (not the project!) - that determines what's on the classpath when generation runs.
What actually happens is
   1. The plugin creates a `SmithyGenerationSettings` from the `<configuration>` section for it in the `pom.xml`.
      The configuration tells it things like what languages and generation targets (e.g. "server" or "model")
      are being generated - and in particular, the configuration tells the generation engine *where to output*
      files for different combinations of language generation target (i.e. your server files go in
      a *server* project; your data modeling sources go in a *model* project that is a shared library
      used by both your server and your generated client SDK - it is common for different targets to
      have different destinations in projects that depend on each other.
   2. It finds the `.smithy` files in the folder(s) it was configured to generate from, and filters
      them based on the Smithy *namespaces* (like Java packages) it was configured to generate code for,
      and calls Amazon's Smithy parser to create `Model`s for them (sort of a universe of defined types).
   3. It creates a `SmithyGenerationSession` for running code generators
   4. The session looks up all of the `SmithyGenerator` instances registered on the classpath,
      and filters them to ones that say they support at least one of the set of languages being generated
   5. The session creates a `SmithyGenerationContext` to pass to the generators - which provides
      access to the session and settings, and *provides a way for code generators to communicate
      with each other* (for example, the typescript plugin may register "markup" files, and any
      server generation project can pick them up and use them somehow - and it is also the way that,
      by default, generated Java code gets configured to use `InvalidInputException` for constraint
      violation exceptions).
   6. It iterates, nested, each *language*, each *generation-target* and each `SmithyGenerator`,
      and if the `SmithyGenerator`, calls its `prepare` method (which allows it to register anything
      other plugins might look for in the `SmithyGenerationContext` before any generation code is run.
   7. It iterates, nested, each *language*, each *generation-target* and each `SmithyGenerator`,
      and if the `SmithyGenerator` says it wants to generate code for that combination, then
      it iterates all `Shape`s in the Smithy `Model` and calls each generator's
      `Collection<? extends ModelElementGenerator> generatorsFor(Shape shape, Model model, Path destSourceRoot,
      GenerationTarget targets, LanguageWithVersion language, SmithyGenerationSettings settings, SmithyGenerationLogger logger)`,
      which will examine the `Shape`, instantiate zero or more appropriate `ModelElementGenerator`s which
      will be used to generate some code for that `Shape` and return them all in a collection.  Typically
      `ModelElementGenerator`s are specialized to handle a specific `ShapeType` - which might be numbers,
      strings, booleans, complex structures, or services or operations - so a generation plugin involves
      writing a code generator for each type of shape a Smithy model can contain (or at least the subset
      you immediately care about).
   8. It runs each generator's
      `Collection<? extends GeneratedCode> generate(SmithyGenerationContext ctx, SmithyGenerationLogger log)`
      method, collecting any `GeneratedCode` objects (which typically represents a file that can be written
      to a specific place on disk).  Note that nothing has been written to disk yet - if any code generator
      throws an exception, we do not want to produce partial output - generation should either succeed,
      or fail before anything has changed.
   9. Once all relevant generators have been run for all models, the session runs all of the `GeneratedCode`
      instances, actually writing files to disk.
   10. The session runs any `PostGenerateTask`s that were registered by `ModelElementGenerators` - these
      are code that needs to run only after all generated code has been committed to disk - for example,
      the `smithy-openapi-wrapper` plugin generates Swagger documentation to disk, which server generation
      plugins such as `smithy-vertx-server-generator` register a `PostGenerationTask` which looks in the
      `SmithyGenerationContext` it's running in to see if any (generated) files have been registered
      into a category called "markup", and if so, creates a zip file from them and generates some code
      for the server to unpack those files to `$TMPDIR` and serve them.
2. One `SmithyGenerator` is called for a language and generation task it has acknowledged it supports.
   For this case, let's say pick a simple, but not-too-simple type - our `Shape` is a `TimestampShape` named "Created" in the model,
   the language is Java, the generation target is "model", and the destination is `target/generated-sources/smithy`
   under the Maven project that contains the model file (the default if the pom file does not specify
   a different destination for that `language +/- target` combo).  The `smithy-java-generators` model
   generation plugin is on the classpath.  What happens is:
   1. `SmithyJavaGenerators` is found and called with the shape named "Created".  It sees that it is of
   `ShapeType.Timestamp`, so it includes a
   [`TimestampModelGenerator`](https://github.com/Telenav/smithy/blob/main/smithy-java-generators/src/main/java/com/telenav/smithy/java/generators/builtin/TimestampModelGenerator.java)
   instance in its results.
   2. `TimestampModelGenerator.generate()` is called.  That creates a `ClassBuilder` (from
   [Java Vogon](https://github.com/timboudreau/annotation-tools) - but it could use any templating
   language to generate code - the framework doesn't care - a `GeneratedCode` is just a thing that
   writes some bytes to a known location); it populates the class name from the `TypeId` of the shape,
   escaping it to ensure a legal Java identifier, and uses a Java package name based on the `namespace` from
   the Smithy model, also ensuring that is a legal Java identifier.  What the generator will do is create
   a Java class which
      * Wraps a single immutable field of type `Instant`
      * Implements `Temporal` delegating to that field so it is easy to use
      * Can be instantiated from a JSON String in ISO 8601 format, or from an Instant
      * Returns a quoted JSON (ISO 8601) string from its `toString()` method
      * Correctly and effiently implements `equals()`, `hashCode()` and `compareTo()`
      * Has conversion methods for common types callers may need (`Date`, `long` for epoch millis)

   3. `generate()` then calls a bunch of instance methods on itself, whose names are fairly self-explanatory:
   `generateDefaultConstructor, generateEpochMillisConstructor, generateDateConstructor,
   generateToString, generateSupplierImplementation, generateHashCode, generateEquals,
   generateToDateConversionMethod, generateToEpochMilliConversionMethod, generateToEpochSecondsConversionMethod,
   generateComparableImplementation, generateAgeConversionMethod, generateDateTimeConversionMethods,
   generateIsAfterAndBefore, generateWithMillis, generateImplementsTemporal`.  Typically, these
   methods are fairly simple - e.g. converting a `Created` into a `ZonedDateTime` by taking
   a (Java SDK) `ZoneId` argument:
```java
    void generateDateTimeConversionMethods(ClassBuilder<String> cb) {
        cb.importing(ZonedDateTime.class, ZoneId.class);
        cb.method("toZonedDateTime", mth -> {
            mth.withModifier(PUBLIC)
                    .docComment("Convert the timestamp represented by this " + cb.className()
                            + " to a ZonedDateTime in the specified time zone."
                            + "\n@param timeZone the time zone"
                            + "\n@return a ZonedDateTime")
                    .addArgument("ZoneId", "timeZone")
                    .returning("ZonedDateTime")
                    .body(bb -> {
                        bb.returningInvocationOf("ofInstant")
                                .withArgumentFromField("value")
                                .ofThis()
                                .withArgument("timeZone")
                                .on("ZonedDateTime");
                    });
        });
    }
```

Complex types - such as structures, which may contain other model-defined types, is
a bit more complicated - particularly because `smithy-java-generators` supports other
plugins *contributing or replacing* code generation for individual class members, down
to the level of adding annotations to individual constructor arguments.  But ultimately
it is fairly straightforward - a code generator for a structure that contains some other
model-defined type simply *assumes the type exists* and generates code as if it does, and
if the type for the member shape is not in the same namespace, includes a Java `import`
statement for it - it might be generated into a separate shared library (like client classes
using model classes).

Each code generator instance is responsible only for its own domain; if it needs to communicate
something to other code generators, it can use its `prepare()` method to add objects of types
known to both it and whatever it needs to communicate with, to the `SmithyGenerationContext`,
which those generators can find (in practice this is rare).  That is also helpful when
writing new code generators - you don't need to "boil the ocean" and support every possible
type a Smithy model could contain to have something useful - you can pick off types as
needed.


Generation Plugin Entry Points
==============================

All extensions are found using [the Java Extension Mechanism](https://docs.oracle.com/javase/tutorial/ext/)
which uses flat files in `META-INF/services` named by the fully-qualified name of the interface
or class being implemented, which contain a list of types extending the type named by the file.

You can create that file manually, or use an annotation processor to generate such files - the
libraries here use `com.mastfrog:annotation-processors` `@ServiceProvider` annotation, e.g.

```java
@ServiceProvider(SmithyGenerator.class)
public class SmithyJavaGenerators implements SmithyGenerator {
```

Main entry points from the framework are as follows; plugins my define their own service
provider interfaces and types you can contribute as well.

### SmithyGenerator

Adds an implementation of `SmithyGenerator` to the set of those the code-generation infrastructure
can see and will use.  Generators can be - and usually are - tied to a specific language
and one or more "generation targets" (kinds of code being generated, typically associated with
different output locations).

Occasionally, you may write a generator that doesn't actually generate any code, but just
puts something into the `SmithyGenerationContext` that alters the behavior of other code
generators - for example, in `smithy-java-generators` there is an interface that lets the
code that throws an exception when a model's constraints are violated be plugged in; the
http extensions project in turn simply sets this up to throw a particular type the generated
code knows how to handle.

### ModelExtensions

Plugins that define Smithy types (such as custom traits) that have an associated Smithy
source file (so the Smithy parser can find and instantiate objects for those traits,
validate them and report errors, etc.) will need to implement and register a
`ModelExtensions` class - before parsing a `.smithy` model file, all of these are
found and invoked, so that the model can be parsed.  They are quite simple, and
contain convenience methods such as `addSmithyResource()`, used below, which looks
up and reads and emits a file relative to the calling class on the classpath:


```
@ServiceProvider(ModelExtensions.class)
public final class SimpleGenerationExtensions extends ModelExtensions {
    @Override
    protected void prepareModelAssembler(ModelAssembler ma) throws IOException {
        addSmithyResource("prelude.smithy", ma);
    }
}
```

### StructureExtensions

This interface is defined by `smithy-java-generators`, not the core generation framework.
It allows micro-level intervention into how java model classes for `StructureShape`s
are generated;  while the interface is quite complex, all methods have do-nothing default
implementations, so you just override what you need.

An example usage is [BuilderExtensionsJava](https://github.com/Telenav/smithy/blob/main/simple-smithy-extensions-java/src/main/java/com/telenav/smithy/extensions/java/BuilderExtensionJava.java)
whose project defines the trait `@builder` that can be used on structures in a
Smithy model (the project also contains a `ModelExtensions` to register the Smithy
definition of those traits).

What it does is allow "builder" classes to be generated for complex structure types,
making it easy for users of the generated API to create instances of those structures.
It does this, not by generating code directly, but rather, using an 
[existing Java annotation processor library](https://github.com/timboudreau/builder-builder)
to generate those builders - it just adds annotations to the constructor and its
arguments so that the generated builder will be generated, and will know about default
values, and any constraints on those values that should cause invalid values to be
rejected rather than create an invalid object.

(Note that Maven does *not*, by default, run annotation processors against sources
generated into `target/generated-sources/*` and some intervention in the `pom.xml`
is likely to be needed - 
[here is an example of using the maven-processor-plugin](https://github.com/Telenav/smithy/blob/main/blog-example/blog-service-model/pom.xml#L157) to do that).

### LoginOperationFinder

This interface is defined by the typescript generation package, and is only used if
you instruct it to generate  the optional (and expermental, and crude) web UI - in
the case of operations that require a login, the browser's `XmlHttpRequest` will not
automatically pop up a credentials dialog when encountering a `www-authenticate` header,
so the generated UI needs a URL it can open in a 1-pixel IFrame to cause the browser
to collect those credentials, so that `XmlHttpRequest`s can succeed. This interface
allows the code generator for the web UI to identify some model-defined Operation
which can be used for that purpose.  There is a default implementation that simply
looks for a `GET` operation named `login` or `loginoperation` when its name is 
lower-cased.
