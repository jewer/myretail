package myretail

import akka.actor.ActorSystem
import akka.event.LoggingAdapter

import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.server.PathMatchers.LongNumber
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import com.typesafe.config.Config
import play.api.libs.json.Json
import spray.json.DefaultJsonProtocol

import scala.concurrent.{ExecutionContextExecutor, Future}

trait ProductJsonSupport extends DefaultJsonProtocol {
  implicit val priceFormat = jsonFormat2(Price.apply)
  implicit val productFormat = jsonFormat3(Product.apply)
  implicit val errorFormat = jsonFormat1(Error.apply)
}

trait ProductServices extends ProductJsonSupport {
  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: Materializer

  def config: Config

  val logger: LoggingAdapter

  val redis: IRedisProxy

  val apiProxy: IApiProxy

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

  /*
  Composes futures of 1) get product name and 2) get pricing to create result or error, if either have failed.
   */
  def getProduct(id: Long): Future[ToResponseMarshallable] = {
    getProductName(id).zip(getProductPrice(id)).map[ToResponseMarshallable] {
      case (Right(name), Right(price)) => Product(id, name, price)
      case (Left(error), _) => BadRequest -> new Error(error)
      case (_, Left(error)) => BadRequest -> new Error(error)
    }
  }

  lazy val nameServiceFlow: Flow[HttpRequest, HttpResponse, Any] =
    Http().outgoingConnection(config.getString("services.target-api.host"), config.getInt("services.target-api.port"))

  def targetApiRequest(request: HttpRequest): Future[HttpResponse] =
    Source.single(request).via(nameServiceFlow).runWith(Sink.head)

  /*
  Calls the target API in order to get product name information.
   */
  def getProductName(id: Long): Future[Either[String, String]] = {
    apiProxy.singleRequest(HttpRequest(uri = s"${config.getString("services.target-product-api.scheme")}://${config.getString("services.target-product-api.host")}/products/v3/$id?fields=descriptions&id_type=TCIN&key=43cJWpLjH8Z8oR18KdrZDBKAgLLQKJjz"))
      .flatMap(response => Unmarshal(response.entity).to[String])
      .map(json => extractProductName(json))
  }

  /*
  Sets the product price in redis
   */
  def updateProductPrice(id: Long, updatedPrice: Price): Future[Unit] = {
    Future {
      val newValues = Vector(("currency", updatedPrice.currency), ("value", updatedPrice.value))
      redis.setValues(s"product:$id:price", newValues)
    }
  }

  /*
  Gets the product pricing information from redis, if exists.
   */
  def getProductPrice(id: Long): Future[Either[String, Price]] = {
    Future {
      redis.getValues(s"product:$id:price").map(values => {
        (values.get("value").map(_.toDouble), values.get("currency")) match {
          case (Some(v), Some(c)) => Right(Price(v, c))
          case _ => Left(s"Unable to find price for product: $id")
        }
      }).getOrElse(Left(s"Unable to find price for product: $id"))
    }
  }

  /*
  Reads the json from the Target API then decides if it's valid or is in error (since the Target API always returns 200)
   */
  def extractProductName(json: String): Either[String, String] = {
    val parsed = Json.parse(json)
    val errors = parsed \\ "errors"
    if(errors.size > 0){
      Left(errors.map(e => (e \\ "message").map(_.as[String]).mkString(",")).mkString(","))
    }else{
      val items = Json.parse(json) \\ "items"
      Right((items.head.head \ "online_description" \ "value").as[String])
    }
  }
}
