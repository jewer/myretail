package myretail

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.{ActorMaterializer, Materializer}

import scala.concurrent.Future

trait IApiProxy {
  def singleRequest(request:HttpRequest)(implicit actorSystem: ActorSystem, materializer: Materializer): Future[HttpResponse]
}

class ApiProxy extends IApiProxy {
  override def singleRequest(request: HttpRequest)(implicit actorSystem: ActorSystem, materializer: Materializer): Future[HttpResponse] = Http().singleRequest(request)
}
