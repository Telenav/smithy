#set ($nameCaps = $artifactId.substring(0, 1).toUpperCase() + $artifactId.substring(1))
$nameCaps Service Model and Implementation
==========================================

This project contains subprojects for modelling and generating servers for the
$nameCaps service.  It contains a mix of subprojects which are purely generated code,
and subprojects in which implementation code is written.

Code - including pojos, server code and client SDKs - is generated from a [Smithy](https://smithy.io/)
model which is located in the `\${artifactId}-model` project in `src/main/smithy/\${artifactId}.smithy`.

That project is configured to generate code into several of its sibling projects, creating
a shared library for types that model data, server projects for one or more Java server
frameworks (both [Acteur](http://github.com/timboudreau/acteur) and [Vert.x](http://vertx.io)
are supported.

Java and (optionally) Typescript/Javascript client SDKs are also generated.


Projects For Implementing The Service
-------------------------------------

The projects containing code you will edit are as follows:

 * `\${artifactId}-model` - the Smithy model file itself, `src/main/smithy/\${artifactId}.smithy` - this
project will also contain generated model code, and optionally, generated unit tests of that code that
prove that all types correctly implement JSON serialization, implement equality and hashing correctly
and more.  This project contains Java sources as well, but they are generated, and deleted and regenerated
on each build.
 * `\${artifactId}-implementation` - each Smithy *operation* (which specifies one HTTP call the server
supports) has a corresponding generated SPI interface with a single method which takes an input object
and an output which can asynchronously be called with the response (or an error).  This project is
where you put your implementations of those interfaces - the business logic of the application.
 * `\${artifactId}-acteur-application` - the generated server code comes with an easy-to-use launcher
class which lets you bind your implementations of the generated SPI interfaces (there are default,
no-op implementations, so you can launch the server without having *everything* implemented during
development).  This project comes with a skeleton launcher which you populate with any configuration,
bindings to other types your implementation needs, etc.
 * `\${artifactId}-vertx-application` - same story as the `\${artifactId}-acteur-application` - a launcher for the
vertx server which you configure with your implementation classes.


Generated Code Projects
-----------------------

The projects containing generated code (any manual code changes will be deleted the next time the model
project is built) are:
 * `\${artifactId}-generated-business-logic-spi` - defines one interface for each `operation` defined in
the Smithy model (and, if you are using the `@authenticated` trait, some authentication interfaces) -
these are what you implement to create the business logic for the server.
 * `\${artifactId}-generated-client-sdk` - contains a generated Java library that makes it simple to
call a running $nameCaps server, using the same model classes and JSON serialization the server uses
 * `\${artifactId}-generated-acteur-server` - contains the generated Acteur server logic - implementing all
HTTP calls defined in the model, and invoking the business logic implementation that was bound on startup
 * `\${artifactId}-generated-vertx-server` - contains the generated Vert.x server logic - implementing all
HTTP calls defined in the model, and invoking the business logic implementation that was bound on startup

Note that this layout *does* mean that when you make a change in the Smithy model you need to rebuild
all of the projects that contain generated code - just clean and build the root `pom.xml` to be confident
that you're really running against what you think it should be.

-------------------------------------------------------

This project originally generated by the [`${project.groupId}:${project.artifactId}` 
Maven Archetype](https://github.com/Telenav/smithy), version *${project.version}*.
