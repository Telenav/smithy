#set($dollarsVersion = "$" + "version")
$dollarsVersion: "2"

#set ($gidLower = $groupId.toLowerCase())
#set ($aidLower = $artifactId.toLowerCase())

namespace $gidLower.$aidLower

use com.telenav.smithy#identity
use com.telenav.smithy#builder
use com.telenav.smithy#samples
use com.telenav.smithy#units
use com.telenav.smithy#fuzzyNameMatch
use com.telenav.smithy#genericRestProtocol

#set ($nameCaps = $artifactId.substring(0, 1).toUpperCase() + $artifactId.substring(1))

// @genericRestProtocol is needed to use Amazon's OpenAPI/Swagger generator,
// which insists on a "protocol" definition, but provides only AWS-specific ones.
@genericRestProtocol
@cors(
    additionalAllowedHeaders: 
        ["x-tn-req-id", "if-none-match", "if-modified-since", "if-match", 
         "if-unmodified-since", "accept", "authorization", "content-type",
         "x-requested-with"], 
    additionalExposedHeaders: 
        ["x-tn-req-id", "if-none-match", "if-modified-since", "if-match", 
         "if-unmodified-since", "accept", "authorization", "content-type",
         "x-requested-with"])
service ${nameCaps}Service {
    version: "${version}",
    resources: [${nameCaps}Meetings]
}

/// Below here is some sample content for getting started with Smithy

resource ${nameCaps}Meetings {
    identifiers: { id: UUID }
    list: List${nameCaps}Meetings,
    read: Read${nameCaps}Meeting,
}

@readonly
@http(method:"POST", uri:"/meetings", code: 200)
operation List${nameCaps}Meetings {
    input: List${nameCaps}MeetingsInput,
    output: List${nameCaps}MeetingsOutput
}

@readonly
@http(method:"GET", uri:"/meetings/{id}", code: 200)
operation Read${nameCaps}Meeting {
    input: Read${nameCaps}MeetingInput,
    output: Read${nameCaps}MeetingOutput,
}

@input
structure Read${nameCaps}MeetingInput {
    @required
    @httpLabel
    id: UUID
}

@output
structure Read${nameCaps}MeetingOutput {
    @required
    @httpPayload
    meeting: Meeting
}

@input
structure List${nameCaps}MeetingsInput {
    @required
    @httpPayload
    period: TimePeriod
}

@output
structure List${nameCaps}MeetingsOutput {
    @required
    @httpPayload
    meetings: Meetings
}

@uniqueItems
list Meetings {
    member: Meeting
}

/// A string shape with length and pattern.
@pattern("^[0-9a-f]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$")
@length(min:36, max:36)
@samples( // Samples allow unit tests to be generated
    valid: ["3dbc1975-5911-46bb-a313-93e2c0fa566e",
            "a443e161-12e6-4e38-855c-543a45b13763",
            "7fa152ba-ed2e-4171-a96a-4f9f8ae1db4b"], 
    invalid: ["x", "notlike-this-uuid-will-work0fa566e", "f00d", "", "no", ",,"]
)
string UUID

@range(min: 0, max: 140)
short Age

timestamp BirthDate

/// A person
@builder
structure Person {
    /// Unique id of a person
    @identity
    @required
    id : UUID

    @required
    age : Age

    dateOfBirth : BirthDate

    @required
    name : String
}

@length(min: 2, max: 10000)
list Attendees {
    member : Person
}

@fuzzyNameMatch
enum TimeLengthUnits {
    @units(1)
    MILLISECONDS = "Milliseconds",
    @units(1000)
    SECONDS = "Seconds",
    @units(60000)
    MINUTES = "Minutes",
    @units(3600000)
    HOURS = "Hours",
    @units(86400000)
    DAYS = "Days"
}

@builder
structure LengthOfTime {
    @range(min: 1)
    @required
    amount : Integer

    unit : TimeLengthUnits = "Minutes"
}

@builder
structure StartEndTimePeriod {
    @required
    start: Timestamp
    @required
    end: Timestamp
}

@builder
structure DurationTimePeriod {
    @required
    start: Timestamp,
    @required
    duration: LengthOfTime,
}

union TimePeriod {
    pointsInTime: StartEndTimePeriod,
    period: DurationTimePeriod,
}

@builder
structure Meeting {
    @required
    @identity
    id: UUID,

    @required
    attendees : Attendees
    @required
    at : Timestamp

    @required
    organizer : Person
    title : String

    duration : LengthOfTime
}
