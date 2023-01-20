Xeger
=====

Regex, backwards.

A little library to reverse engineer matching and non-matching strings from
regular expressions.

Does not support the full panoply of all the things you could do with regular
expressions, but supports the common cases and tells you when it's in over its
head.

Usage
-----

```java
// Matches a UUID
Xeger xeger = new Xeger("^[0-9a-f]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$");

Random rnd = new Random(812831047810431L);
Set<String> uuids = xeger.emitSet(5, rnd, 5).get(); // Use 5 attempts - we know it will work

uuids.forEach(System.out::println);

// Output is some values matching the regex:
//
// 6cccb5bc-eb22-770a-4cd2-067db40c5638
// 7dcebc8c-43ad-d667-caab-eaad01ec0ce6
// 8b07ebcc-c416-18c8-eea4-b6304acd3b42
// 8bae32b1-aba0-2adc-ece5-214e6a2e84a0
// b256dcda-80e6-54a7-74e7-33aa5eeae3db

// Now generate some strings that definitely DON'T match:
        xeger.confound().ifPresent(confounded -> {
            for (int i = 0; i < 5; i++) {
                System.out.println(confounded.emit(rnd));
            }
//          Output like
//            d.e.b.8.e66b4
//            0ba0...2b.cdc64
//           16.e...ac8807d6
//           dbb0.e6.11..4d7c46a
        });

```

But Why???
----------

1. String members in Smithy models can be constrained by a regular expression.
2. We need to *generate* automated tests that prove that the types that use those
members will accept valid strings and *reject* invalid strings.
3. While we have a `@samples` trait that can be used to provide a set of samples,
if you need to generate a test of a `Set` or `Map` that is constrained to have at
least `n` elements, but you only have up to `n-1` samples, you cannot generate a
test for that.

Limitations
-----------

Certain more exotic constructs are not supported; it is possible to have a regex
with capture groups where there is *a* path through the regular expression that
will generate a matching result, and others that will not, so a number of methods
take a limiting number of attempts to produce a match.

All of the methods on `Xeger` that return an `Optional` will only return a value
if the value *definitely does* match.

Confounding
-----------

To generate strings that **definitely do not match** the original, call 
`confound()` on an instance of `Xeger`, and use the raw `emit()` method.

Random Argument
---------------

The methods to generate strings take a `java.util.Random` - for code generation
you want to be repeatable, the random should be initialized with the same seed
and be in the same state every time.


Credit Where Credit Is Due
--------------------------

Regular expression parsing is done using a patched version of the
[Antlr pcre-parser grammar](https://github.com/bkiers/pcre-parser) - the
modifications are non-sematic, adding Antlr labels and extracting into new
parse tree types a few kinds of sub-element of regular expressions which
need to be easily identifiable by the code that dissects regular expressions -
specifically, names for capture groups (which are discarded) and sub-elements
of the `alternation` tree element which need to be clearly identified (so we
can create alternate or-group branches for patterns such as `(?:yes|maybe|no)`.
