package myretail


import akka.actor.ActorSystem
import spray.json.DefaultJsonProtocol
import play.api.libs.json._
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatchers.LongNumber
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.{ExecutionContextExecutor, Future}

trait ProductJsonSupport extends DefaultJsonProtocol {
  implicit val priceFormat = jsonFormat2(Price.apply)
  implicit val productFormat = jsonFormat3(Product.apply)
}

trait ProductServices extends ProductJsonSupport {
  implicit val system: ActorSystem

  implicit def executor: ExecutionContextExecutor

  implicit val materializer: Materializer

  def config: Config

  val logger: LoggingAdapter

  val redis: IRedisProxy = new RedisProxy

  val routes = {
    logRequestResult("myretail") {
      pathPrefix("product") {
        (get & path(LongNumber)) { id =>
          complete {
            getProduct(id)
          }
        } ~
        (put & path(LongNumber)) { id =>
          entity(as[Price]) { price =>
            complete {
              updateProductPrice(id, price).map(_ => getProduct(id))
            }
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
    Http().singleRequest(HttpRequest(uri = s"${config.getString("services.target-product-api.scheme")}://${config.getString("services.target-product-api.host")}/products/v3/$id?fields=descriptions&id_type=TCIN&key=43cJWpLjH8Z8oR18KdrZDBKAgLLQKJjz"))
      .flatMap(response => Unmarshal(response.entity).to[String])
      .map(json => extractProductName(json))
      .map(Right(_))
  }

  def updateProductPrice(id: Long, updatedPrice: Price): Future[Unit] = {
    Future {
      val newValues = Vector(("currency", updatedPrice.currency), ("value", updatedPrice.value))
      redis.setValues(s"product:$id:price", newValues)
    }
  }

  def getProductPrice(id: Long): Future[Either[String, Price]] = {
    Future {
      redis.getHashMap(s"product:$id:price")
        .map(values => new Price(values.get("value").map(_.toDouble).getOrElse(0.0), values.get("currency").getOrElse("USD")))
        .map(Right(_))
        .getOrElse(Left(s"Unable to find price for product: $id"))
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