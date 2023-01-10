#set ($nameCaps = $artifactId.substring(0, 1).toUpperCase() + $artifactId.substring(1))
$nameCaps Vert.X Server
=======================

This project contains generated code which impelements and HTTP server for the ${nameCaps}
service using the [Vert.X](https://vertx.io/) Netty-based server framework.

The main entry point to this project is the generated ${nameCaps} Guice module.  Implement
the SPI interfaces defined in the `${artifactId}-generated-business-logic-spi` project
adjacent to this one - on that Guice module there is a binding method for each interface that
takes a `Class` that implements it.

Configure it thusly, and then simply call the `start()` method on it to start your server.

No user-editable code is in this project - the sources are regenerated when the adjacent
`${artifactId}-model` project is rebuilt.
