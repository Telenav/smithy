Vertx-Guice
===========

After surveying the available Guice + Vertx implementations on Github, none of them
were terribly adequate, some dangerously naive (really, you're going to deploy something
that can compile any string into Java bytecode and run it in your server?).

So this is ours.

While not offering Acteur-level decomposition and decoupling of ALL of the steps of processing
a request, this at least makes it easy to configure routes and have the handling portion
be injectable.

Largely this library tries to get out of the way and just let you use your types, JDK types
and Vertx types without introducing anything new to learn.  There is one type you might
want to implement is `VertxInitializer` if you need to register custom factories immediately
after the Vertx instance is created.  If you do, simply bind your type as an eager singleton
and it will be called.

Customizable settings objects such as `HttpServerOptions`, `DeploymentOptions`, `VertxOptions` and
things like that can be configured by passing one (or more) `UnaryOperator<$THE_TYPE>` to the
appropriate method on the `VertxGuiceModule` or `VerticleBuilder` - you can pass a lambda,
or if the thing that customizes the object needs dependency injection itself, simply pass the
class of your `UnaryOperator` instead.

Example usage taken from tests:

```java
        // A random value for the test
        String rndString = "Mr. " + Long.toString(ThreadLocalRandom.current().nextLong(), 36);
        int port_a = PORTS.findAvailableServerPort(); // first server port
        int port_b = PORTS.findAvailableServerPort(); // second server port
        VertxGuiceModule mod = new VertxGuiceModule()
                .withModule(
                        (Binder bnd) -> {
                            // bind a few things to test that injection into handlers and configurers works
                            bnd.bind(String.class)
                                    .annotatedWith(named("who")).toInstance(rndString);
                            bnd.bind(Integer.class)
                                    .annotatedWith(named("threads")).toInstance(3);
                        })
                .withVerticle() // gets us a builder for a verticle
                .customizingHttpOptionsWith(opts -> { // configure a few things
                    return opts.setPort(port_a)
                            .setHost("localhost");
                })
                .withPort(port_a) // can also configure the port here
                .customizingRouterWith(rt -> { // we can also customize the router before it is used
                    return rt.allowForward(AllowForwardHeaders.ALL);
                })
                .route().forHttpMethod(HttpMethod.GET) // Configure our first route in this verticle
                .withPath("/hello")
                .handledBy(MyHandler.class) // Will be instantiated and injected
                .route().forHttpMethod(HttpMethod.GET)
                .withPath("/goodbye")
                .handledBy(x -> { // This one we'll do as a lambda
                    x.end("goodbye");
                })
                .bind()
                .withVerticle(CustomVerticle.class); // We can also bind a custom Verticle (second http port)

        // Get the launcher
        VertxLauncher launcher = Guice.createInjector(mod).getInstance(VertxLauncher.class);

        // Start it - we could (and should) also pass a Consumer<List<Future<String>>> and abort
        // or log something if any of the server startup futures fail
        launcher.start();
```

Vertx Pros and Cons
===================

Having written this integration, and also being the author of a very similar framework, some
general thoughts on pro's and con's of vertex:

#### Pros

 * Vertx is (presumably) fairly widely used and supported
 * Good integration with the Vertx async postgres driver (sharing a Netty event loop?) is nice
 * The API is fairly clean and focused

#### Cons

 * While it doesn't have the *I'm the solution to all problems* identity crisis Spring has, IMHO,
   it still tries to do too much
   * Classloader isolation of verticles that pass messages via JsonObjects is really taking saving the user
     from themselves **way** to far.  In general if you're using Java and you're writing a message passing
     mechanism, and you find yourself inventing a `Message` class, you're already on the wrong track - this
     is Java, not Erlang, and you can simply get out of the way and let the user's own types *be* the
     messages, and advise them that they should be immutable - because your framework is for grownups.
   * It's great that you can use a `vertx` to create HTTP servers *or* generic TCP servers *or* UDP
     servers - but these should be extensions, not stuff you get out of the box, like it or not
   * Similarly, clustering support is nice to have, but does not belong in the base framework - I get
     that instant gratification for new users is nice, but every piece of code on a server is a potential
     security hole, and you should be able simply not to have things be there that you do not need.
 * The API for dealing with requests suffers a bit from trying to be protocol-agnostic - in general,
   you should specialize the API for the protocol, not try to do a lowest-common-denominator API that
   will serve *all* protocols, or try to protect your users from knowing that they're writing an HTTP
   server when that is exactly what they are doing.

Compared with Spring, it is still far less of an atrocity, but it could use some further decomposing.
