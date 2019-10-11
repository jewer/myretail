package myretail

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatchers.LongNumber
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.config.Config
import play.api.libs.json.{JsLookupResult, Json}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

trait ProductJsonSupport extends DefaultJsonProtocol {
  implicit val priceFormat: RootJsonFormat[Price] = jsonFormat2(Price.apply)
  implicit val productFormat: RootJsonFormat[Product] = jsonFormat3(Product.apply)
  implicit val errorFormat: RootJsonFormat[Error] = jsonFormat1(Error.apply)
}

trait ProductServices extends ProductJsonSupport {
  implicit val system: ActorSystem

  implicit def executor: ExecutionContextExecutor

  implicit val materializer: Materializer

  def config: Config

  val logger: LoggingAdapter

  val redis: IRedisProxy

  val apiProxy: IApiProxy

  val routes: Route = {
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
                updateProductPrice(id, price).map(_ => getProduct(id)) //probably wouldn't do this in a prod setting but good for demo?
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
    getProductName(id).zip(getProductPrice(id)).map[ToResponseMarshallable] { // probably wouldn't do a zip in prod. short-circuit if no product found?
      case (Right(name), Right(price)) => Product(id, name, price)
      case (Left(error), _) => BadRequest -> Error(error)
      case (_, Left(error)) => BadRequest -> Error(error)
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
    apiProxy.singleRequest(HttpRequest(uri = s"${config.getString("services.target-product-api.scheme")}://${config.getString("services.target-product-api.host")}/v2/pdp/tcin/$id?excludes=taxonomy,price,promotion,bulk_ship,rating_and_review_reviews,rating_and_review_statistics,question_answer_statistics"))
      .flatMap(response => Unmarshal(response.entity).to[String])
      .map(json => extractProductName(json))
  }

  /*
  Sets the product price in redis
   */
  def updateProductPrice(id: Long, updatedPrice: Price): Future[Unit] = {
    Future {
      val newValues = Vector(("currency_code", updatedPrice.currency_code), ("value", updatedPrice.value))
      redis.setValues(s"product:$id:price", newValues)
    }
  }

  /*
  Gets the product pricing information from redis, if exists.
   */
  def getProductPrice(id: Long): Future[Either[String, Price]] = {
    Future {
      redis.getValues(s"product:$id:price").map(values => {
        (values.get("value").map(_.toDouble), values.get("currency_code")) match {
          case (Some(v), Some(c)) => Right(Price(v, c))
          case _ => Left(s"Unable to find price for product: $id")
        }
      }).getOrElse(Left(s"Unable to find price for product: $id"))
    }
  }

  /*
  Reads the json from the Target API then decides if it's valid or is in error (since the Target API always returns 200) ;-)
   */
  def extractProductName(json: String): Either[String, String] = {
    val parsed = Json.parse(json)
    val item: Try[JsLookupResult] = Try(parsed \ "product" \ "item")
    item.map(i => extractTitle(i)).getOrElse(Left("Unknown Parsing Error finding item"))
  }


  private def extractTitle(item: JsLookupResult): Either[String, String] = {
    Try(item \ "product_description" \ "title") match {
      case Failure(exception) => Left(s"Unknown Parsing Error finding title: $exception")
      case Success(value) => Right(value.as[String])
    }
  }
}
