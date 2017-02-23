package myretail

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.Accept
import spray.json.{DefaultJsonProtocol, JsObject}
//import org.json4s._
//import org.json4s.native.JsonMethods._
import play.api.libs.json._
//import jsonBackends.json4s._
import akka.event.{LoggingAdapter, Logging}
//import play.api.libs.json._
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, HttpRequest}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatchers.LongNumber

import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Flow, Sink, Source}

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.io.IOException

import scala.concurrent.{ExecutionContextExecutor, Future}

case class Price(value: Double, currency: String)
case class Product(id: Long, name: String, price: Price)

trait ProductServices extends DefaultJsonProtocol {
  implicit val priceFormat = jsonFormat2(Price.apply)
  implicit val productFormat = jsonFormat3(Product.apply)

  implicit val system: ActorSystem

  implicit def executor: ExecutionContextExecutor

  implicit val materializer: Materializer

  def config: Config

  val logger: LoggingAdapter

  val routes = {
    logRequestResult("myretail") {
      pathPrefix("product") {
        (get & path(LongNumber)) { id =>
          complete {
            getProduct(id)
          }
        }
      } ~
        pathPrefix("name") {
          (get & path(LongNumber)) { id =>
            complete {
              getProductName(id)
            }
          }
        }
    }
  }

  def getProduct(id: Long): Future[ToResponseMarshallable] = {
    getProductName(id).zip(getProductPrice(id)).map[ToResponseMarshallable] {
      case (Right(name), Right(price)) => Product(id, name, price)
      case (Left(error), _) => BadRequest -> error
      case (_, Left(error)) => BadRequest -> error
    }
  }

  lazy val nameServiceFlow: Flow[HttpRequest, HttpResponse, Any] =
    Http().outgoingConnection(config.getString("services.target-api.host"), config.getInt("services.target-api.port"))

  def targetApiRequest(request: HttpRequest): Future[HttpResponse] =
    Source.single(request).via(nameServiceFlow).runWith(Sink.head)

  def getProductName(id: Long): Future[Either[String, String]] = {
    /*targetApiRequest(RequestBuilding.Get(s"products/v3/$id?fields=descriptions&id_type=TCIN&key=43cJWpLjH8Z8oR18KdrZDBKAgLLQKJjz").withHeaders(Vector(Accept.("application/json")))).flatMap { response => {
      response.status match {
        case OK => Unmarshal(response.entity).to[String].map(json => Right(extractProductName(json)))
        case BadRequest => Future.successful(Left(s"$id: invalid Id"))
        case _ => Unmarshal(response.entity).to[String].flatMap { entity =>
          val error = s"Target API request failed with status ${response.status}:  $entity"
          Future.failed(new IOException(error))
        }}
      }
    }*/

    Http().singleRequest(HttpRequest(uri = s"${config.getString("services.target-api.scheme")}://${config.getString("services.target-api.host")}/products/v3/$id?fields=descriptions&id_type=TCIN&key=43cJWpLjH8Z8oR18KdrZDBKAgLLQKJjz"))
      .flatMap(response => Unmarshal(response.entity).to[String])
      .map(json => extractProductName(json))
      .map(Right(_))
  }

  def getProductPrice(id: Long): Future[Either[String, Price]] = {
    Future {
      Right(Price(101, "USD"))
    }
  }

  def extractProductName(json: String): String = {
    val items = Json.parse(json) \\ "items"
    (items.head.head \ "online_description" \ "value").as[String]
  }
}

object MyRetailService extends App with ProductServices {

  override implicit val system = ActorSystem("myRetail")
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorMaterializer()

  override val config = ConfigFactory.load()
  override val logger = Logging(system, getClass)

  Http().bindAndHandle(routes, config.getString("http.bindInterface"), config.getInt("http.bindPort"))
}