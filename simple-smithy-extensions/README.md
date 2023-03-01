simple-smithy-extensions
========================

This project defines a few custom Smithy traits that are used if present by the code
generators here:

* `@builder` - applicable to `struct` shapes, if present, the generated code will include
annotations from [builder-builder](https://github.com/timboudreau/builder-builder) which
cause builders to be generated for the generated type and a `builder()` method to be added.
Note, the annotation processors from builder-builder must be run as part of the model-build
if you use this trait.
* `@identity` - applicable to a single member of `struct` shapes, this trait instructs the
code generators that that single field subsumes object identity, and the generated code should
report itself as equal to another object of the same type if that member matches.  In Java,
this means that `equals(Object)` tests only on this field and `hashCode()` hashes only that
field.  This can be useful if a single field represents an ID or primary key for an entity.
* `@samples` - this is used by test- and documentation-generation - it allows for both valid
and invalid examples of a value to be provided, and generated unit tests will validate that
the generated code actually respects both.
* `@span` - applicable to `struct` shapes, this allows two fields of the same type to be marked
as being a span or value range, and that one value must be greater (or greater than or equal to)
the other - it imposes a *relative-value* constraint on these fields.  An example would be
a shape which expresses a duration of time - the end time must be greater than or equal to the
start time.  If present, generated code will enforce this relation.
* `@units` - applicable to `enum` shapes, if present on every member of an enum shape, marks
that enum shape as representing units of something; if the code generators encounter a `struct`
shape containing a single number field, conversion methods will be generated (example below).
The result is pleasant, easy-to-use unit conversion code.  Note: *one enum member **must**
be marked with `@units(1)` - the base unit - or the build will be failed.*

Also a couple of housekeeping traits to overcome limits built into Smithy are present:

`@authenticated` - Smithy's built in support for authentication is all-or-nothing, owing to its
origin in Amazon's services where all requests are authenticated for all services using the
same ID. Real world services not within such a walled garden may well have some operations
requiring authentication, some not, and sometimes multiple operations requiring different
logic for it (consider the demo blog engine, which has admin users, ordinary users and not
logged in users).  The `@authenticated` trait makes as few assumptions as possible about what
authentication means - you specify a `mechanism` which is simply a string;  when your server
is generated, there will be an enum with the mechanisms specified, and generated interfaces
for each one which can generically perform authentication (and potentially attach information
about the authenticated user for use by the operation implementation).  What any given
authentication mechanism means is up to you.
`@genericRestProtocol` - This is needed for Swagger generation.  The Swagger generator wraps
Amazon's Swagger generation code, which requires that a "protocol trait" be specified - and
the only one that Smithy comes with out of the box is Amazon's internal protocol.  This
is simply a "generic REST" protocol that allows Swagger generation to work, but the generator
needs to see the service is annotated with it.


### Units example

Given a struct like this:

```
structure Distance {
    /// The size of the distance.
    @required
    @range(min : 0)
    @httpQuery("dimension")
    dimension : Double

    /// The unit of distance.
    @httpQuery("unit")
    unit : SizeUnit = "NANODEGRESS_OF_LATITUDE"
}
```

and an enum where each member has `@units`

```
enum SizeUnit {
    @units(1.0)
    MILLIMETERS,

    @units(25.4)
    INCHES,

    @units(100.0)
    CENTIMETERS,

    @units(304.80)
    FEET,

    @units(914.4)
    YARDS,

    @units(1000.0)
    METERS,

    @units(1609000.00)
    MILES,

    @units(1852000.00)
    NAUTICAL_MILES,

    @units(110574000.00)
    DEGREES_OF_LATITUDE,

    @units(0.110574)
    NANODEGRESS_OF_LATITUDE,

    @units(1000000.00)
    KILOMETERS,
}
```

the result is a `Distance` type you can call `Distance.to(SizeUnit.METERS).dimension()`.

