$version: "2"

namespace com.telenav.safety

use smithy.api#mediaType
use com.mastfrog.smithy.extensions#httpCacheHeaders
use com.mastfrog.smithy.extensions#batch

service SafetyService {
    version: "1.0"
    resources: [ParkingIncidentsInCircleResource,
                DrivingIncidentsInCircleResource,
                ParkingIncidentsInRectangleResource,
                DrivingIncidentsInRectangleResource,
                ParkingScores]
}

/// Gets parking incidents with a circle and radius for input
resource ParkingIncidentsInCircleResource {
    read: ParkingIncidentsInCircle
}

/// Gets parking incidents with a rectangle defined by two corners for input
resource ParkingIncidentsInRectangleResource {
    read: ParkingIncidentsInRectangle
}

resource DrivingIncidentsInCircleResource {
    read: DrivingIncidentsInCircle
}

resource DrivingIncidentsInRectangleResource {
    read: DrivingIncidentsInRectangle
}

resource ParkingScores {
    read: ParkingScore
}

@readonly
operation ParkingScore {
    input: ParkingSafetyScoreInput
    output: ParkingSafetyScoreOutput
}

/// Read operation for parking incidents within a circle
@readonly
//@httpCacheHeaders
//@batch
//@mediaType("application/json+incidents;charset=utf-8")

//         mount(v1_0, "driving/incidents-on-route", POST, DrivingSafetyIncidentsOnRouteRequest.class);


@http(method:"POST", uri:"/parking/incidents-in-circle", code: 200)
operation ParkingIncidentsInCircle {
    input : ParkingIncidentsInCircleInput
    output : IncidentsResponse
}

@readonly
@http(method:"POST", uri:"/parking/incidents-in-rectangle", code: 200)
operation ParkingIncidentsInRectangle {
    input : ParkingIncidentsInRectangleInput
    output : IncidentsResponse
}

@readonly
@http(method:"POST", uri:"/driving/incidents-in-circle", code: 200)
operation DrivingIncidentsInCircle {
    input : DrivingIncidentsInCircleInput
    output : IncidentsResponse
}

@readonly
@http(method:"POST", uri:"/driving/incidents-in-rectangle", code: 200)
operation DrivingIncidentsInRectangle {
    input : DrivingIncidentsInRectangleInput
    output : IncidentsResponse
}

@readonly
@http(method:"POST", uri:"/driving/incidents-on-route", code: 200)
operation DrivingIncidentsOnRoute {
    input : DrivingIncidentsOnRouteInput
    output : IncidentsResponse
}

/// Required input for driving incidents
structure DrivingIncidentsOnRouteInput {

    /// An hours of week span for which data is requestsd
    @required
    hoursOfWeek : HoursOfWeek

    /// The route for which data is requested
    @required
    route : Route

    /// The maximum number of incidents to return
    @required
    @range(min: 1)
    maximumIncidents : Integer

    /// The length of time to look in the past for incidents
    @required
    past : Duration

    /// The type of incident requested
    @required
    type : IncidentType
}

structure Route {
    /// A route identifier
    @required
    id : RouteIdentifier

    /// The type of identifier in this object
    @required
    type : RouteIdentifierType
}

structure DrivingSafetyScore {
    @required
    routeIdentifier : String

    @required
    score : Score
}

//@uniqueItems
list Locations {
    member : Location
}

structure ParkingSafetyScore {
    @required
    count : Integer

    @required
    risk : RiskLevel

    @required
    type : IncidentType
}

structure ParkingSafetyScoreInput {
    @required
    past : Duration

    @required
    locations : Locations
}

structure ParkingSafetyScoreLocation {
    @required
    routeIdentifier : RouteIdentifier

    @required
    location : Location
}

list RouteIdentifierList {
    member : RouteIdentifier
}

list ParkingSafetyScoreList {
    member : ParkingSafetyScore
}

structure ParkingSafetyScoreOutput {
    @required
    scoreLists : ParkingSafetyScoreList
}

/// A driving or parking incident
structure Incident {

    /// Unique identifier for the incident
    @required
    id : IncidentIdentifier

    /// The type of incident
    @required
    type : IncidentType

    /// The location where the incident occurred
    @required
    location : IncidentLocation

    /// A description of the incident
    description : String

    /// The time at which the incident occurred
    @required
    reportedAt : IncidentTime

    /// A way identifier
    @required
    wayIdentifier : WayIdentifier
}

/// Contains information about the time an incident occurred
structure IncidentTime {

    /// The timestamp of the incident
    @required
    reportedAt : Timestamp

    /// The time zone the incident was reported in
    @required
    reportedAtTimeZone : TimeZone
}

structure IncidentLocation {
    @required
    location : Location

    state : State

    address : String

    city : String

    neighborhood : String

    @required
    zipCode : ZipCode
}

structure ParkingIncidentsInCircleInput {
    @required
    bounds : Circle

    @required
    when : TimePeriod

    @required
    type : IncidentType
}

list Incidents {
    member : Incident
}

structure DrivingIncidentsInCircleInput {
    @required
    bounds : Circle

    @required
    when : TimePeriod

    type : SafetyIncidentType = "ALL"
}

structure ParkingIncidentsInRectangleInput {
    @required
    bounds : Rectangle

    @required
    when : TimePeriod

    type : SafetyIncidentType = "ALL"
}

structure DrivingIncidentsInRectangleInput {
    @required
    bounds : Rectangle

    @required
    when : TimePeriod

    type : SafetyIncidentType = "ALL"
}

/// A response object consisting of a list of incidents
structure IncidentsResponse {
    @required
    incidents : Incidents
}

/// A time period defined by a start and end
structure TimePeriod {
    @required
    start : Timestamp

    @required
    end : Timestamp
}

/// A circle centered on a location with a radius
structure Circle {
    /// The center point of the circle
    @required
    location : Location

    /// The radius of the circle
    @required
    radius : Distance
}

/// A rectangle defined by bottom-left and top-right corners
structure Rectangle {
    /// The bottom left corner
    @required
    bottomLeft : Location

    /// The top right corner
    @required
    topRight : Location
}

structure Location {
    /// The geographic latitude
    @required
    latitude : Latitude

    /// The geographic longitude
    @required
    longitude : Longitude
}

/// An interval of time defined by an amount and a unit
structure Duration {
    /// The number of units of time represented
    @required
    @range(min : 1)
    amount : Long

    /// The unit of time
    @required
    unit : TimeInterval
}

@pattern("^[A-Za-z0-9]+$")
/// Identifies one incident
string IncidentIdentifier

@range(min: 0, max: 6)
byte DayOfWeek

@range(min: 0, max: 23)
byte HourOfDay

/// A time zone
@pattern("^[A-Za-z/_]+$")
string TimeZone

/// A geographic latitude
@range(min : -85, max : 85)
double Latitude

/// A geographic longitude
@range(min : -180, max : 180)
double Longitude

@pattern("^\\d{5}$")
string ZipCode

@pattern("^[A-Z]{2}$")
string State

/// Identifies a particular hour of a week
@range(min: 0, max : 168)
short HourOfWeek

long WayIdentifier

/// A floating point score between 0.0 and 1.0 inclusive
@range(min : 0.0, max : 1.0)
float Score

/// How severe an incident is
@range(min: 0, max : 100)
byte Severity

@pattern("^[a-zA-Z0-9-./_]+$")
string RouteIdentifier


/// A geographic distance
structure Distance {
    /// The size of the distance
//    @required
    @range(min : 1)
    dimension : Long = 1

    /// The unit of distance
//    @required
    unit : SizeUnit = "MILE"
}

/// An interval of hours of a week
structure HoursOfWeek {
//    @required
    start : HourOfWeek = 1

//    @required
    end : HourOfWeek = 167

    @required
    timeZone : TimeZone
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
    MILE,
    METER,
    KILOMETER,
    LEAGUE,
}

/// Kinds of incidents
enum IncidentType {
    /// A stolen vehicle report
    STOLEN_VEHICLE,
    /// An unknown or invalid incident type
    UNSUPPORTED,
    /// A vehicle burglary
    VEHICLE_BURGLARY,
    /// Any incident type
    ALL,
    /// A parking violation incident
    PARKING_VIOLATION,
    /// An impounded vehicle incident
    VEHICLE_IMPOUNDED,
    /// An totalled vehicle incident
    TOTAL,
}

/// Units of time
enum TimeInterval {
    /// Thousandths of a second
    MILLISECOND,
    /// One second
    SECOND,
    /// One minute
    MINUTE,
    /// One hour
    HOUR,
    /// One day (24 hours)
    DAY,
    /// Seven days
    WEEK,
    /// One month (30 days)
    MONTH,
    /// 365 days
    YEAR,
}

enum SafetyIncidentType {
    /// An accident
    ACCIDENT,
    /// Any accident
    ALL,
}

enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
}
