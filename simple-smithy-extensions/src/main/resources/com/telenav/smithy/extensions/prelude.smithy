$version: "2.0"

namespace com.telenav.smithy

/// Trait that can be applied to a structure member to
/// indicate that that field participates in supplying the identity
/// of the structure, and only fields annotated with @identity should
/// participate in equality or hashing tests of the generated code
@trait(
    selector: "structure > member",
)
structure identity {}

/// Trait which can be applied to an enum if JSON deserialization
/// should accept permutations of the member name or string value
/// that vary by case or substitute dashes for underscores.
@trait(
    selector: "enum"
)
structure fuzzyNameMatch {}

@trait(
//    selector : "[@trait|enumMember]"
//    selector: "*",
    selector: "enum > member",
//    selector: "[@enumMember:]",
)
double units
//structure units{
//    multiplier : Double,
//}

/// Trait which can be applied to a structure to indicate that
/// a builder should be generated for the generated type in languages
/// where that is appropriate and supported.
///
/// An optional "style" parameter can be passed, which describes what
/// the generated builder should look like (the Java implementation supports
/// "default" and "flat" styles) - whether this is honored by generators is
/// up to them.
@trait(
    selector: "structure",
)
string builder

@internal
list Documents {
  member: Document
}

@trait(
    selector: "blob",
)
string blobEncoding


/// Allows generated serialization tests to construct valid and invalid examples
/// of types so that we do not generate tests that attempt to use invalid data
@trait( selector : "*" )
structure samples {
    @length(min : 1)
    valid : Documents
    @length(min : 1)
    invalid : Documents
}

/// Allows two memberssharing a type of a structure to be marked as indicating a
/// "span" the distance between which must be non-negative
@trait(selector : "structure")
structure span {
    @required
    @length(min:1)
    lesser : String,
    @required
    @length(min:1)
    greater: String,
    emptyAllowed : Boolean = true
}

/// Mark an operation as authenticated, without making assumptions about the
/// mechanism of authentication
@trait(
    selector: "operation",
)
structure authenticated {
    mechanism : String = "basic",
    optional : Boolean = false,
    @idRef
    payload : String = "smithy.api#String"
}

/// An RPC-based protocol that sends JSON payloads. This protocol does not use
/// HTTP binding traits.
@protocolDefinition(
    traits: [
        cors
        endpoint
        hostLabel
        http
        httpError
        httpHeader
        httpLabel
        httpPayload
        httpQuery
        httpQueryParams
        httpResponseCode
        jsonName
        timestampFormat
    ]
)
@trait(
    selector: "service"
)
structure genericRestProtocol {}
