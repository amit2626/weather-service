package xyz.example.model

import spray.json.{DefaultJsonProtocol, JsString, JsValue, RootJsonFormat}
import xyz.example.model.response.CurrentWeatherResponse

// Json Formats to be used with spray.json
object JsonFormat extends DefaultJsonProtocol {

  // service-http-response json formats
  implicit val currentWeatherResponseJsonFormat
      : RootJsonFormat[CurrentWeatherResponse] =
    jsonFormat3(CurrentWeatherResponse)

  // service-model json formats
  implicit val cloudsJsonFormat: RootJsonFormat[Clouds] = jsonFormat1(Clouds)
  implicit val coordinatesJsonFormat: RootJsonFormat[Coordinates] = jsonFormat2(
    Coordinates)
  implicit val mainJsonFormat: RootJsonFormat[Main] = jsonFormat6(Main)
  implicit val sysJsonFormat: RootJsonFormat[Sys] = jsonFormat5(Sys)
  implicit val weatherConditionJsonFormat: RootJsonFormat[WeatherCondition] =
    new RootJsonFormat[WeatherCondition] {
      override def write(obj: WeatherCondition): JsValue = JsString(
        obj.toString)

      override def read(json: JsValue): WeatherCondition =
        WeatherCondition.fromString(json.asInstanceOf[JsString].value) match {
          case Some(value) => value
          case None        => throw new Exception(s"Not a valid category $json")
        }
    }
  implicit val weatherJsonFormat: RootJsonFormat[Weather] = jsonFormat4(Weather)
  implicit val windJsonFormat: RootJsonFormat[Wind] = jsonFormat2(Wind)
  implicit val weatherDataJsonFormat: RootJsonFormat[WeatherData] =
    jsonFormat13(WeatherData)
}
