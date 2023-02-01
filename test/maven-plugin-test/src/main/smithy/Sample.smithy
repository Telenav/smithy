$version: "2"

namespace my.test.ns

use com.telenav.smithy#identity
use com.telenav.smithy#builder
use com.telenav.smithy#samples
use com.telenav.smithy#blobEncoding

service MyService {
    version : "1.0"
    resources : [MyResource]
}

resource MyResource {
    read : ReadSomething
}

@readonly
@http(method:"POST", uri:"/meetings", code: 200)
operation ReadSomething {
    input : ReadSomethingInput
    output : ReadSomethingOutput
}

structure ReadSomethingInput {
    value : Integer
}

structure ReadSomethingOutput {
    value : Integer
}

/// A thing, which is identified solely by its id.
@builder("default")
structure ThingWithId {

    /// The unique ID of this thing
    @identity
    @required
    id : WuggleId

    @range(min: 0)
    @identity
    @required
    sequenceId : Short

    /// The message of this thing.
    message : String

    /// When this thing was sent.
    when : Timestamp
}

@pattern("^[a-z]+$")
@length(min: 5, max : 10)
@samples(
    valid : ["helloz", "foober", "abcde", "abcedef", "hello", "blorgs",
             "abcdefg", "abcdefgh", "abcdefghi", "abcdefghij"]
    invalid : ["", "999", "moo", "ab-cdef", "abcdefghijk", "abcdefghijkl", "gobsmackers"]
)
string WuggleId

//@range(min:0)
timestamp Foozle

/// A iug, which is apparently a number.
@range(min:1, max : 12750)
integer Iug

/// A wug, which is apparently a smaller number.
@range(min:1, max : 350)
short Wug

@range(min:-5, max : 5)
byte Bug

@range(min:-12129023, max : 4823429489)
long Lug

@range(min:-1, max : 1)
double Dug

@range(min:-1, max : 1)
float Fug

boolean Boogle

/// A BigDecimal value
@range(min:-1, max : 93289238923892389352)
bigDecimal Deccy

@range(min:1221212, max : 93289238923892389231)
bigInteger Biggy

bigInteger Wiggy

timestamp Ts

@length(min:1, max:25)
list ListOfStrings {

    @length(min:3, max:12)
    @pattern("^[a-z]+\\/\\S+$")
    @samples(
        valid : ["abcd/HEYA", "this/THAT", "fnood/1234", "a/BCDE"]
        invalid : ["a-d/HEYA", "th-is/THAT", "x", "", "fnood3/1234", "poog/HEY ", "b/c", "mugu/HUUUUUUUUUUUUUUUUUG"]
    )
    member : String
}

list ListOfInts {
    @range(min : 1, max : 12)
    member : Integer
}

@length(min:3, max:23)
@sparse
list ListOfUserType {
    @length(min:1)
    member : WuggleId,
}

/// This is a hoober
union Hoober {
    /// An inty hoober
    i32 : Integer,

    /// A stringy hoober
    @length(min:1, max:20)
    string : String,

    /// A Wuggy hoober
    time : Wug
}

// A set of ints
@uniqueItems
@length(min : 1, max : 24)
list SetOfInts {
    /// I have docs!
    @range(min : 1, max : 12)
    member : Integer
}

@uniqueItems
@length(min : 1, max : 24)
list SetOfStrings {
    /// I have docs!
    @length(min : 1, max : 12)
    @pattern("^[a-zA-Z0-9]+$")
    member : String
}

@uniqueItems
list SetOfUnremarkableShorts {
    member : Short
}

@uniqueItems
list SetOfUserIntegerType {
    member : Iug
}

@uniqueItems
list SetOfUserStringType {
    @length(min:2, max : 10)
    member : WuggleId
}

@uniqueItems
@length(min:1, max :52)
list SetOfWrappedTimestamps {
    member : Ts
}

@length(min:1, max:12)
map MapOfTwoUserTypes {
    @length(min:5, max : 5)
    key : WuggleId,
    @length(min : 2)
    value : SetOfWrappedTimestamps
}

@length(min:3, max:23)
map MapWithKeyLengthAndValueRange {
    @length(min: 5, max : 40)
    @pattern("^[a-z]+\/[a-z]+$")
    key : String,

    @range(min:0, max : 1)
    value : Double
}

map MapOfStringsWithPatterns {

//    @length(min: 5, max : 40)
    @pattern("^[a-z]+\/[a-z]+$")
    key : String,

    @pattern("^.*?::\\S*$")
    value : String
}

@length(max: 5)
map MapOfNamedTypeToDoubleWithRanges {

    @length(min:1)
    key : WuggleId,

    @range(min : -22, max : 22)
    value : Double
}

map UnconstrainedMap {
    key: String
    value : Byte
}

@uniqueItems
list SetOfSizeUnit {
    member : SizeUnit
}

structure UsesKeywordsAsFieldNames {
    void : String
    if : Integer
    else : Byte
    case : Wug,

}

/// Three dimensional coordinates for something.
///
/// They do things.  Like dance around.
@builder("default")
structure XYZCoordinatesFloat {
    /// The X coordinate
    @required
    @range(min: -1000, max : 1000)
    x : Float,
    /// The Y coordinate
    @required
    @range(min: -1000, max : 1000)
    y : Float,
    /// The Z coordinate
    @required
    @range(min: -1000, max : 1000)
    z : Float,
}

@builder("default")
structure XYZCoordinatesDouble {
    @required
    x : Double,
    @required
    y : Double,
    @required
    z : Double,
}

@builder("flat")
structure XYZCoordinatesInt {
    x : Integer = 1,
    y : Integer = 2,
    z : Integer = 3,
}

@builder("flat")
structure XYZCoordinatesIntZeroed {
    @default(0)
    x : Integer,
    @default(0)
    y : Integer,
    @default(0)
    z : Integer,
}


structure XYZCoordinatesLong {
//    @required
    x : Long,
//    @required
    y : Long,
//    @required
    z : Long,
}

structure XYZCoordinatesShort {
    x : Short = 10,
    y : Short = 20,
    z : Short = 30,
}

@builder
structure XYZCoordinatesByte {
    x : Byte = 15,
    y : Byte = 25,
    z : Byte = 35,
}

structure XYZCoordinatesBigInt {
    x : BigInteger = 40,
    y : BigInteger = 50,
    z : BigInteger = 60,
}

structure XYZCoordinatesBigIntOptional {
    x : BigInteger,
    y : BigInteger,
    z : BigInteger,
}

structure XYZCoordinatesBigDec {
    x : BigDecimal = 70,
    y : BigDecimal = 80,
    z : BigDecimal = 90,
}

structure XYZCoordinatesBigDecOptional {
    x : BigDecimal,
    y : BigDecimal,
    z : BigDecimal,
}

/// A document.  It is whatever it wants to be - boolean, number, list, map.
document Doxen

@builder
structure XYZCoordinatesBigIntWithRange {
    @range(min: -1000000000000000000, max: 1000000000000000000)
    x : BigInteger = 40,
    @range(min: -1000000000000000000, max: 1000000000000000000)
    y : BigInteger = 50,
    @range(min: -1000000000000000000, max: 1000000000000000000)
    z : BigInteger = 60,
}

structure XYZCoordinatesBigDecWithRange {
    @range(min: -1000000000000000000, max: 1000000000000000000)
    x : BigDecimal = 70,
    @range(min: -1000000000000000000, max: 1000000000000000000)
    y : BigDecimal = 80,
    @range(min: -1000000000000000000, max: 1000000000000000000)
    z : BigDecimal = 90,
}

bigInteger XCoordinate
bigInteger YCoordinate
bigInteger ZCoordinate

structure XYZCoordinatesBigIntWrappered {
    @required
    x : XCoordinate,
    @required
    y : YCoordinate,
    @required
    z : ZCoordinate,
}

structure XYZCoordinatesBigIntWrapperedOptional {
    x : XCoordinate,
    y : YCoordinate,
    z : ZCoordinate,
}


structure XYZCoordinatesBigIntWrapperedDefaulted {
    x : XCoordinate = 11,
    y : YCoordinate = 21,
    z : ZCoordinate = 31,
}

structure ThingWithOtherDefaultedThing {
    @required
    coords : XYZCoordinatesBigIntWrapperedDefaulted

    @required
    moreCoords : XYZCoordinatesBigDec

    message : String = "Hello"
}

enum StrangeEnum {
    FIRST = "hello",
    SECOND,
    THIRD = "blah"
}

enum NonMixedEnum {
    /// The first thing
    FIRST = "first",
    /// The second thing
    SECOND = "second",
    /// The third thing
    THIRD = "third"
}



/// A structure that is a structure.
@builder("flat")
structure AStructure {

    /// A big thing
    @required
    @range(min:1221214, max : 93289238923892389200)
    big : Biggy

    /// A big decimal thing
    @required
//    @range(min: 0, max : 100000)
    dec : Deccy

    /// An optional big integer thing
    @range(min: 1, max : 100000)
    unbig : Wiggy

    /// An optional big decimal thing
    @range(min: 2, max : 10)
    undec : Deccy

    /// A really big thing
    @range(min: -36893488147419103228, max : 27670116110564327421)
    reallyBig : Wiggy

    /// A really big decimal thing
    @required
    @range(min: 0, max : 27670116110564327421.67890)
    reallyDec : Deccy

//    @required
    @pattern("^[0-9]{3}-[0-9]{5}-[a-z]+$")
    @length(min : 10, max : 32)
    poogleHoozerWhatzit : String

    /// Some wugs for me and thee
    @required
    @range(min:10, max:20)
    wug : Wug,

    /// Wuggles are not to be confused with wugs.
    @required
    @length(min : 0, max : 12)
    wuggles : SetOfUserStringType,

    /// A dug, which is sort of like a wug
    @required
    @range(min:0.1, max:0.8)
    dug : Dug,

    @range(min:100, max:200)
    iug : Iug

    @jsonName("foo")
    @required
    ts : Ts,

    @required
    bye : Byte,

    @length(min : 1, max : 8)
    str : String,

    @length(min : 1, max : 6)
    mappy : MapOfNamedTypeToDoubleWithRanges,

    @required
    @range(min:0, max:5)
    temperature : Double,

    @range(min:7, max:13)
    unrequiredTemperature : Double,

    @range(min : 0, max : 1)
    fraction : Float

    @required
    boogle : Boogle

    boogleWithDefault : Boogle = false

    @length(min:1, max:5)
    structList : ListOfOtherStructure
}

list ListOfOtherStructure {
    member : OtherStructure
}

@builder
structure OtherStructure {
    @required
    anInt : Integer

    @required
    aShort : Short

    @required
    aLong : Long

    unrequitedInt : Integer

    unrequitedShort : Short

    unrequitedLong : Long
}

/// Types of identifiers used for routes
intEnum RouteIdentifierType {
    /// Identifiers are way ids that require no translation
    WAY_IDENTIFIER = 1

    /// Identifiers are Telenav edge identifiers where the way identifier is in the top 32 bits */
    TELENAV_HIGH_LOW_EDGE_IDENTIFIER = 2

    /// Identifiers are Telenav OSM edge identifiers where the way identifier is found by dividing by 1E6 */
    TELENAV_OSM_EDGE_IDENTIFIER = 3
}

enum SizeUnit {
    /// This is one mile
    MILE,
    /// This is one meter
    METER,
    /// This is a kilometer
    KILOMETER,
    LEAGUE,
}

/// THis is a blob.
@blobEncoding("raw")
blob RawBlob

/// THis is another blob, with a length constraint.
@length(min:4, max: 32)
blob MyOtherBlob

@blobEncoding("base-64")
blob Base64DefaultBlob

@blobEncoding("base-64-url")
@length(min:11, max: 17)
blob Base64UrlBlob

@blobEncoding("base-64-mime")
@length(min:0, max: 52)
blob Base64MimeBlob

@blobEncoding("hex-lower-case")
@length(min:0, max: 52)
blob HexLowerBlob

@blobEncoding("hex-upper-case")
@length(min:0, max: 52)
blob HexUpperBlob

@blobEncoding("hex-upper-or-lower-case")
@length(min:0, max: 52)
blob HexUpperLowerBlob

/// This is a thing that owns some blobs.
@builder("flat")
structure ThingWithBlobs {

    @required
    h1 : HexLowerBlob

    @required
    h2 : HexUpperBlob

    @required
    h3 : HexUpperLowerBlob

    @required
    a : RawBlob,

    @required
    b : MyOtherBlob,

    @required
    @length(min:10, max: 20)
    justARandomBlob : Blob

    anUnrequitedBlob : Blob

    @required
    defaultBlob : Base64DefaultBlob,

    @required
    urlBlob : Base64UrlBlob,

    @required
    mimeBlob : Base64MimeBlob,
}
