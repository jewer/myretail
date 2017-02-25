package myretail

import akka.actor.ActorSystem
import akka.event.NoLogging
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalamock.scalatest.MockFactory
import org.scalatest._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.stream.Materializer

import scala.concurrent.Future

class ProductServicesSpec extends FlatSpec with Matchers with ScalatestRouteTest
  with OneInstancePerTest with MockFactory with EitherValues
  with ProductServices {
  override def testConfigSource = "akka.loglevel = WARNING"
  override def config = testConfig
  override val logger = NoLogging

  val mockApiProxy = mock[ApiProxy]
  override val apiProxy = mockApiProxy

  val mockRedis = mock[RedisProxy]
  override val redis = mockRedis

  val validProductJson = """{"product_composite_response":{"request_attributes":[{"name":"product_id","value":"13860428"},{"name":"key","value":"43cJWpLjH8Z8oR18KdrZDBKAgLLQKJjz"},{"name":"id_type","value":"TCIN"},{"name":"fields","value":"descriptions"}],"items":[{"identifier":[{"id_type":"DPCI","id":"058-34-0436","is_primary":null,"source":"Online and Store"},{"id_type":"TCIN","id":"13860428","is_primary":null,"source":"Online"}],"relation":"TAC","relation_description":"Title Authority Child","data_page_link":"http://www.target.com/p/the-big-lebowski-blu-ray/-/A-13860428","imn_identifier":12244586,"parent_id":"46767107","is_orderable":true,"is_sellable":true,"general_description":"Blu-ray BIG LEBOWSKI, THE Movies","is_circular_publish":true,"business_process_status":[{"process_status":{"is_ready":true,"operation_description":"assortment ready","operation_code":"PAAP"}},{"process_status":{"is_ready":false,"operation_description":"import ready","operation_code":"PIPT"}},{"process_status":{"is_ready":true,"operation_description":"order ready","operation_code":"PORD"}},{"process_status":{"is_ready":true,"operation_description":"presentation ready","operation_code":"PPRS"}},{"process_status":{"is_ready":true,"operation_description":"project ready","operation_code":"PCMT"}},{"process_status":{"is_ready":true,"operation_description":"replenishment ready","operation_code":"PRPL"}},{"process_status":{"is_ready":false,"operation_description":"scale ready","operation_code":"PSCL"}},{"process_status":{"is_ready":true,"operation_description":"target.com ready","operation_code":"PTGT"}}],"dpci":"058-34-0436","department_id":58,"class_id":34,"item_id":436,"online_description":{"value":"The Big Lebowski (Blu-ray)","type":"GENL"},"store_description":{"value":"Blu-ray BIG LEBOWSKI, THE Movies","type":"GENL"},"alternate_description":[{"type":"GENL","value":"Blu-ray BIG LEBOWSKI, THE Movies","type_description":"General Desc"},{"type":"VEND","value":"BIG LEBOWSKI BD","type_description":"Vendor Description"},{"type":"SHLF","value":"BIG LEBOWSKI, THE UNIV","type_description":"Shelf Desc"},{"type":"POS","value":"BLU-RAY","type_description":"POS Desc"},{"type":"ADSG","value":"The Big Lebowski:<\/Blu-ray Disc","type_description":"Ad Signage Description"}],"features":[{"feature":"Movie Genre: Comedy"},{"feature":"Software Format: Blu-ray"},{"feature":"Movie Studio: Universal Studios"}]}]}}"""
  val invalidProductJson = """{"product_composite_response":{"items":[{"errors":[{"message":"Whoops!"}]}]}}"""

  val validProductResponse = HttpResponse(200, entity=validProductJson)
  val invalidProductResponse = HttpResponse(200, entity=invalidProductJson)

  "ProductServices" should "extract name from target json" in {
    val parsed = extractProductName(validProductJson)
    parsed.right.value should be ("The Big Lebowski (Blu-ray)")
  }

  it should "return failure info if not a valid product in json" in {
    val parsed = extractProductName(invalidProductJson)
    parsed.left.value should be ("Whoops!")
  }

  it should "get a product and price from dependent services" in {
    val expectedProduct = Product(123, "The Big Lebowski (Blu-ray)", Price(100, "USD"))

    (mockRedis.getValues _).expects("product:123:price").returning(Some(Map("value" -> "100", "currency" -> "USD")))
    (mockApiProxy.singleRequest(_: HttpRequest)(_: ActorSystem, _: Materializer)).expects(*, *, *).returning(Future{validProductResponse})

    Get("/product/123") ~> routes ~> check {
      status shouldBe OK
      responseAs[Product] shouldBe expectedProduct
    }
  }

  it should "return bad request if product does not exist in API" in {
    (mockRedis.getValues _).expects("product:123:price").returning(Some(Map("value" -> "100", "currency" -> "USD")))
    (mockApiProxy.singleRequest(_: HttpRequest)(_: ActorSystem, _: Materializer)).expects(*, *, *).returning(Future{invalidProductResponse})

    Get("/product/123") ~> routes ~> check {
      status shouldBe BadRequest
      responseAs[Error] shouldBe Error("Whoops!")
    }
  }

  it should "return bad request if price does not exist in db" in {
    (mockRedis.getValues _).expects("product:123:price").returning(None)
    (mockApiProxy.singleRequest(_: HttpRequest)(_: ActorSystem, _: Materializer)).expects(*, *, *).returning(Future{validProductResponse})

    Get("/product/123") ~> routes ~> check {
      status shouldBe BadRequest
      responseAs[Error].message should be ("Unable to find price for product: 123")
    }
  }

  it should "return bad request if price value does not exist in db" in {
    (mockRedis.getValues _).expects("product:123:price").returning(Some(Map("currency" -> "USD")))
    (mockApiProxy.singleRequest(_: HttpRequest)(_: ActorSystem, _: Materializer)).expects(*, *, *).returning(Future{validProductResponse})

    Get("/product/123") ~> routes ~> check {
      status shouldBe BadRequest
      responseAs[Error].message should be ("Unable to find price for product: 123")
    }
  }

  it should "update a product price" in {
    val expectedProduct = Product(123, "The Big Lebowski (Blu-ray)", Price(100, "USD"))

    (mockRedis.getValues _).expects("product:123:price").returning(Some(Map("value" -> "100", "currency" -> "USD")))
    (mockRedis.setValues _).expects("product:123:price", Vector(("currency", "MXP"), ("value", 200)))
    (mockApiProxy.singleRequest(_: HttpRequest)(_: ActorSystem, _: Materializer)).expects(*, *, *).returning(Future{HttpResponse(200, entity=validProductJson)})

    Put("/product/123", Price(200, "MXP")) ~> routes ~> check {
      status shouldBe OK
      responseAs[Product] shouldBe expectedProduct
    }
  }
}
