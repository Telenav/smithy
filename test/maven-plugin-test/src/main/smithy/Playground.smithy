$version: "2"

namespace smithy.playground

use com.telenav.smithy#identity
use com.telenav.smithy#builder
use com.telenav.smithy#samples

/// A string shape with length and pattern.
@pattern("^[0-9a-f]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$")
@length(min:36, max:36)
@samples(
    valid: ["3dbc1975-5911-46bb-a313-93e2c0fa566e",
            "a443e161-12e6-4e38-855c-543a45b13763",
            "7fa152ba-ed2e-4171-a96a-4f9f8ae1db4b"], 
    invalid: ["x", "notlike-this-uuid-will-work0fa566e", "f00d"]
)
string UUID

@range(min: 0, max: 140)
short Age

/// A person
structure Person {
    /// Unique id of a person
    @required
    id : UUID

    @required
    age : Age

    dateOfBirth : Timestamp

    @required
    name : String
}

@length(min: 2)
list Attendees {
    member : Person
}

enum TimeLengthUnits {
    SECONDS = "Seconds",
    MINUTES = "Minutes",
    HOURS = "Hours"
}

structure LengthOfTime {
    @range(min: 1)
    @required
    amount : Integer

    unit : TimeLengthUnits = "Minutes"
}

structure Meeting {
    @required
    attendees : Attendees
    @required
    at : Timestamp

    @required
    organizer : Person
    title : String

    duration : LengthOfTime
}
