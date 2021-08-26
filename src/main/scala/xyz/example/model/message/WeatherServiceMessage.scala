package xyz.example.model.message

// Messages that we send to WeatherService
sealed trait WeatherServiceMessage extends ServiceMessage

// Query current weather from WeatherService
final case class QueryCurrentWeather(location: String)
    extends WeatherServiceMessage
