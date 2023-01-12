Smithy Maven Archetype
======================

Archetype which generates a parent Maven module and Maven submodules
for a Smithy service.

Example usage:

```sh
mvn archetype:generate \
  -DarchetypeGroupId=com.telenav.smithy \
  -DarchetypeArtifactId=smithy-maven-archetype \
  -DarchetypeVersion=1.0.2 \
  -DgroupId=org.myorg \
  -DartifactId=thingamabob \
  -Dversion=0.0.1
```

(note that the word "Service" is appended to some project and
type names, so including it in the artifact id is likely to get
you names like "MyServiceService").

The archetype will ask to confirm a few properties - simply press
enter - there are a few cases where we use `<requiredProperties>`
to provide a transformed version of the artifact id or group id
globally, to all of the Velocity templates used in the archetype,
and there is no way to avoid the archetype asking for confirmation
of them.


What You Get
------------

The result of running the archetype is several Maven projects,
names all prefixed with the `artifactId` you provided to the
archetype.  Some of them are home entirely to generated code
(which will be regenerated when you build the `-model` project);
others are places for code you edit to implement a web service.

 * `-model` - contains a Smithy model file in `src/main/smithy`
that defines your service and all of the data types.  The model
file is what drives code generation of all of the generated
code.  That code-generation happens when you build it, and where
the various types of code are generated *to* is configured in the
`<plugin>` entry for the `smithy-maven-plugin` of its `pom.xml`.
Data-model classes are generated into the Java sources of this
project, and it acts as a shared library for those.
The Smithy model file comes pre-populated with a simple model
for a read-only calendar service - you are expected to replace
this with the design for whatever service you want to create.
 * `-generated-business-logic-spi` - contains one interface for
each Smithy *operation* (http uri path/method...) in your service
 * `-implementation` - a place to put *your implementations* of
those interfaces
 * `-generated-$FRAMEWORK-server` - generated server code that
takes care of wiring up HTTP requests to those interfaces
 * `-$FRAMEWORK-application` - a launcher for each server
application, where, once you've written them, you edit the
skeleton launcher
 * `-generated-client-sdk` - a Java client library for calling
your service
 * `-model-typescript` - an `npm` managed project that compiles
the generated typescript model classes into Javascript and implements
a typescript/Javascript SDK for calling your service

Any project with `-generated-` in the name gets its sources regenerated
whenever the `-model` project is rebuilt.  The others are ones where
you will put your code to implement business logic, configure the
server, etc.


npm
---

The generated typescript project, which contains an easy-to-use SDK
for calling your service, expects `nodejs` and `npm` to be installed
and on `$PATH`.

The typescript code generator will attempt, at the end of the code-generation
phase, to clean and build that project using `npm run` (so generated
javascript / markup can be bundled into the generated server).

The build will not *fail* if npm is not available, but will print a
warning.

If you do not want typescript support, simply delete the dependency on
`smithy-ts-generator` from the `<plugin>` section for `smithy-maven-plugin`
in the `pom.xml`.
