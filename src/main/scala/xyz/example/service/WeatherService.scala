package xyz.example.service

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util._

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{OverflowStrategy, QueueOfferResult}
import spray.json._
import xyz.example.config.ApplicationConfig
import xyz.example.model.JsonFormat._
import xyz.example.model.message.{QueryCurrentWeather, WeatherServiceMessage}
import xyz.example.model.response.CurrentWeatherResponse
import xyz.example.model.{WeatherCondition, WeatherData}

object WeatherService {

  def props: Props = Props(new WeatherService)
}

final class WeatherService extends Actor with ActorLogging {

  private implicit val system: ActorSystem = context.system
  private implicit val ec: ExecutionContext = system.dispatcher

  import ApplicationConfig.config.service._

  private lazy val connectionPool =
    Http().cachedHostConnectionPool[Promise[HttpResponse]](openWeather.api)

  private lazy val queue =
    Source
      .queue[(HttpRequest, Promise[HttpResponse])](
        openWeather.requestQueueSize,
        OverflowStrategy.dropNew)
      .via(connectionPool)
      .to(Sink.foreach {
        case (Success(resp), promise)      => promise.success(resp)
        case (Failure(exception), promise) => promise.failure(exception)
      })
      .run()

  override def receive: Receive = {
    case req @ QueryCurrentWeather(location) =>
      log.debug(s"$req received at ${context.self.path}")
      handleQueryCurrentWeather(location)

    case req: WeatherServiceMessage =>
      log.error(s"$req is currently unsupported by ${context.self.path}")
  }

  private def handleQueryCurrentWeather(location: String): Unit = {
    val msgSender: ActorRef = sender()
    queryCurrentWeatherByCity(location)
      .map(currentWeather =>
        CurrentWeatherResponse(
          currentWeather.main.temp,
          currentWeather.main.pressure,
          isUmbrellaRequest(currentWeather.weather.head.main)))
      .onComplete(f => msgSender ! f)
  }

  private def isUmbrellaRequest(weatherCondition: WeatherCondition) =
    weatherCondition match {
      case WeatherCondition.Thunderstorm => true
      case WeatherCondition.Drizzle      => true
      case WeatherCondition.Rain         => true
      case _                             => false
    }

  private def queryCurrentWeatherByCity(city: String): Future[WeatherData] = {
    val request =
      HttpRequest(uri = s"/data/2.5/weather?q=$city&appid=${openWeather.key}")
    queueRequest(request)
      .flatMap(resp => Unmarshal(resp).to[JsValue])
      .map(jsValue =>
        Try(jsValue.convertTo[WeatherData]) match {
          case Success(value) => value
          case Failure(exception) =>
            log.error(s"Error parsing Json \n $jsValue")
            throw exception
        })
  }

  private def queueRequest(request: HttpRequest): Future[HttpResponse] = {
    val promise = Promise[HttpResponse]()
    queue.offer(request -> promise).flatMap {
      case QueueOfferResult.Enqueued => promise.future
      case QueueOfferResult.Dropped =>
        Future.failed(
          new RuntimeException("Queue overflowed. Try again later."))
      case QueueOfferResult.Failure(ex) => Future.failed(ex)
      case QueueOfferResult.QueueClosed =>
        Future.failed(new RuntimeException(
          "Queue was closed (pool shut down) while running the request. Try again later."))
    }
  }
}
