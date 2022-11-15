$version: "2"
namespace example.weather

/// Provides weather forecasts for cities, given a city id.
@paginated(inputToken: "nextToken", outputToken: "nextToken",
           pageSize: "pageSize")
service Weather {
    version: "1.0"
    resources: [City]
}

/// A city
resource City {
    identifiers: { cityId: CityId }
    properties: { coordinates: CityCoordinates }
    read: GetCity
    resources: [Forecast]
}

@pattern("^[A-Za-z0-9 ]+$")
/// Identifies one city
string CityId

/// A weather forecast
resource Forecast {
    identifiers: { cityId: CityId }
    properties: { chanceOfRain: Float }
    read: GetForecast
}

/// Retrieves a weather forecast
@readonly
operation GetForecast {
    input: GetForecastInput
    output: GetForecastOutput
}

/// "cityId" provides the only identifier for the resource since
/// a Forecast doesn't have its own.
@input
structure GetForecastInput {
    /// Identifier for a city
    @required
    cityId: CityId
}

/// Output returned by a forecast
@output
structure GetForecastOutput {
    chanceOfRain: Float
}

/// Get information about one city
@readonly
operation GetCity {
    /// The input
    input: GetCityInput
    output: GetCityOutput
    errors: [NoSuchResource]
}

/// Required input for getting information about one city
@input
structure GetCityInput for City {
    // "cityId" provides the identifier for the resource and
    // has to be marked as required.
    @required
    $cityId
}

/// The output of a request to get a city
@output
structure GetCityOutput {
    // "required" is used on output to indicate if the service
    // will always provide a value for the member.
    @required
    @notProperty
    name: String

    @required
    coordinates: CityCoordinates
}

/// Coordinates of a city
structure CityCoordinates {

    /// The latitude of a city.
    @required
    latitude: Float

    /// The longitude of a city.
    @required
    longitude: Float
}

/// "error" is a trait that is used to specialize
/// a structure as an error.
@error("client")
structure NoSuchResource {
    @required
    /// The resource which was not found
    resourceType: String
}


// The paginated trait indicates that the operation may
// return truncated results. Applying this trait to the service
// sets default pagination configuration settings on each operation.
@paginated(items: "items")
@readonly
operation ListCities {
    input: ListCitiesInput
    output: ListCitiesOutput
}

@input
/// Input for listing cities, including pagination information
structure ListCitiesInput {
    nextToken: String
    pageSize: Integer
}

/// Output for listing cities, including pagination information
@output
structure ListCitiesOutput {
    nextToken: String

    @required
    items: CitySummaries
}

/// CitySummaries is a list of CitySummary structures.
list CitySummaries {
    member: CitySummary
}

/// CitySummary contains a reference to a City.
@references([{resource: City}])
structure CitySummary {
    @required
    cityId: CityId

    @required
    name: String
}
