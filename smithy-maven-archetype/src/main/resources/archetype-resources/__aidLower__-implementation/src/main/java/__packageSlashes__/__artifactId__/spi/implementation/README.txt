Implementation package

This package is where you implement the interfaces defined in in
${artifactId}-generated-business-logic-spi.

This project is depended on by the generated application projects, which then
need their launcher code modified to bind your implementation.

Business logic can be implemented piece-by-piece - all of the interfaces use
Guice's `@ImplementedBy` to create a default binding to an implementation that
simply reports that it has not been implemented.

