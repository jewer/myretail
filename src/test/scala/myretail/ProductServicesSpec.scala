package myretail

import akka.actor.ActorSystem
import akka.event.NoLogging
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.Materializer
import com.typesafe.config.Config
import org.scalamock.scalatest.MockFactory
import org.scalatest._

import scala.concurrent.Future

class ProductServicesSpec extends FlatSpec with Matchers with ScalatestRouteTest
  with OneInstancePerTest with MockFactory with EitherValues
  with ProductServices {
  override def testConfigSource = "akka.loglevel = WARNING"

  override def config: Config = testConfig

  override val logger: NoLogging.type = NoLogging

  val mockApiProxy: ApiProxy = mock[ApiProxy]
  override val apiProxy: ApiProxy = mockApiProxy

  val mockRedis: RedisProxy = mock[RedisProxy]
  override val redis: RedisProxy = mockRedis

  val validProductJson = """{"product":{"deep_red_labels":{"total_count":2,"labels":[{"id":"twbl94","name":"Movies","type":"merchandise type","priority":0,"count":1},{"id":"rv3fdu","name":"SA","type":"relationship type","priority":0,"count":1}]},"available_to_promise_network":{"product_id":"13860428","id_type":"TCIN","available_to_promise_quantity":67.0,"street_date":"2011-11-15T06:00:00.000Z","availability":"AVAILABLE","online_available_to_promise_quantity":67.0,"stores_available_to_promise_quantity":0.0,"availability_status":"IN_STOCK","multichannel_options":[],"is_infinite_inventory":false,"loyalty_availability_status":"IN_STOCK","loyalty_purchase_start_date_time":"1970-01-01T00:00:00.000Z","is_loyalty_purchase_enabled":false,"is_out_of_stock_in_all_store_locations":false,"is_out_of_stock_in_all_online_locations":false},"item":{"tcin":"13860428","bundle_components":{},"dpci":"058-34-0436","upc":"025192110306","product_description":{"title":"The Big Lebowski (Blu-ray)","bullet_description":["<B>Movie Studio:</B> Universal Studios","<B>Movie Genre:</B> Comedy","<B>Software Format:</B> Blu-ray"]},"buy_url":"https://www.target.com/p/the-big-lebowski-blu-ray/-/A-13860428","enrichment":{"images":[{"base_url":"https://target.scene7.com/is/image/Target/","primary":"GUEST_44aeda52-8c28-4090-85f1-aef7307ee20e","content_labels":[{"image_url":"GUEST_44aeda52-8c28-4090-85f1-aef7307ee20e"}]}],"sales_classification_nodes":[{"node_id":"hp0vg"},{"node_id":"5xswx"},{"node_id":"g7ito"},{"node_id":"5on"},{"node_id":"1s0rs"}]},"return_method":"This item can be returned to any Target store or Target.com.","handling":{},"recall_compliance":{"is_product_recalled":false},"tax_category":{"tax_class":"G","tax_code_id":99999,"tax_code":"99999"},"display_option":{"is_size_chart":false},"fulfillment":{"is_po_box_prohibited":true,"po_box_prohibited_message":"We regret that this item cannot be shipped to PO Boxes.","box_percent_filled_by_volume":0.27,"box_percent_filled_by_weight":0.43,"box_percent_filled_display":0.43},"package_dimensions":{"weight":"0.18","weight_unit_of_measure":"POUND","width":"5.33","depth":"6.65","height":"0.46","dimension_unit_of_measure":"INCH"},"environmental_segmentation":{"is_lead_disclosure":false},"manufacturer":{},"product_vendors":[{"id":"1984811","manufacturer_style":"025192110306","vendor_name":"Ingram Entertainment"},{"id":"4667999","manufacturer_style":"61119422","vendor_name":"UNIVERSAL HOME VIDEO"},{"id":"1979650","manufacturer_style":"61119422","vendor_name":"Universal Home Ent PFS"}],"product_classification":{"product_type":"542","product_type_name":"ELECTRONICS","item_type_name":"Movies","item_type":{"category_type":"Item Type: MMBV","type":300752,"name":"movies"}},"product_brand":{"brand":"Universal Home Video","manufacturer_brand":"Universal Home Video","facet_id":"55zki"},"item_state":"READY_FOR_LAUNCH","specifications":[],"attributes":{"gift_wrapable":"N","has_prop65":"N","is_hazmat":"N","manufacturing_brand":"Universal Home Video","max_order_qty":10,"street_date":"2011-11-15","media_format":"Blu-ray","merch_class":"MOVIES","merch_classid":58,"merch_subclass":34,"return_method":"This item can be returned to any Target store or Target.com.","ship_to_restriction":"United States Minor Outlying Islands,American Samoa (see also separate entry under AS),Puerto Rico (see also separate entry under PR),Northern Mariana Islands,Virgin Islands, U.S.,APO/FPO,Guam (see also separate entry under GU)"},"country_of_origin":"US","relationship_type_code":"Stand Alone","subscription_eligible":false,"ribbons":[],"tags":[],"ship_to_restriction":"This item cannot be shipped to the following locations: United States Minor Outlying Islands, American Samoa, Puerto Rico, Northern Mariana Islands, Virgin Islands, U.S., APO/FPO, Guam","estore_item_status_code":"A","is_proposition_65":false,"return_policies":{"user":"Regular Guest","policyDays":"30","guestMessage":"This item must be returned within 30 days of the ship date. See return policy for details."},"gifting_enabled":false,"packaging":{"is_retail_ticketed":false}},"circle_offers":{"universal_offer_exists":false,"non_universal_offer_exists":true}}}"""
  val invalidProductJson = """{"product_composite_response":{"items":[{"errors":[{"message":"Whoops!"}]}]}}"""

  val validProductResponse = HttpResponse(200, entity = validProductJson)
  val invalidProductResponse = HttpResponse(200, entity = invalidProductJson)

  "ProductServices" should "extract name from target json" in {
    val parsed = extractProductName(validProductJson)
    parsed.right.value should be("The Big Lebowski (Blu-ray)")
  }

  it should "return failure info if not a valid product in json" in {
    val parsed = extractProductName(invalidProductJson)
    parsed.left.value should not be (null)
  }

  it should "get a product and price from dependent services" in {
    val expectedProduct = Product(123, "The Big Lebowski (Blu-ray)", Price(100, "USD"))

    (mockRedis.getValues _).expects("product:123:price").returning(Some(Map("value" -> "100", "currency_code" -> "USD")))
    (mockApiProxy.singleRequest(_: HttpRequest)(_: ActorSystem, _: Materializer)).expects(*, *, *).returning(Future {
      validProductResponse
    })

    Get("/product/123") ~> routes ~> check {
      status shouldBe OK
      responseAs[Product] shouldBe expectedProduct
    }
  }

  it should "return bad request if product does not exist in API" in {
    (mockRedis.getValues _).expects("product:123:price").returning(Some(Map("value" -> "100", "currency_code" -> "USD")))
    (mockApiProxy.singleRequest(_: HttpRequest)(_: ActorSystem, _: Materializer)).expects(*, *, *).returning(Future {
      invalidProductResponse
    })

    Get("/product/123") ~> routes ~> check {
      status shouldBe BadRequest
      responseAs[Error] should not be(null)
    }
  }

  it should "return bad request if price does not exist in db" in {
    (mockRedis.getValues _).expects("product:123:price").returning(None)
    (mockApiProxy.singleRequest(_: HttpRequest)(_: ActorSystem, _: Materializer)).expects(*, *, *).returning(Future {
      validProductResponse
    })

    Get("/product/123") ~> routes ~> check {
      status shouldBe BadRequest
      responseAs[Error].message should be("Unable to find price for product: 123")
    }
  }

  it should "return bad request if price value does not exist in db" in {
    (mockRedis.getValues _).expects("product:123:price").returning(Some(Map("currency_code" -> "USD")))
    (mockApiProxy.singleRequest(_: HttpRequest)(_: ActorSystem, _: Materializer)).expects(*, *, *).returning(Future {
      validProductResponse
    })

    Get("/product/123") ~> routes ~> check {
      status shouldBe BadRequest
      responseAs[Error].message should be("Unable to find price for product: 123")
    }
  }

  it should "update a product price" in {
    val expectedProduct = Product(123, "The Big Lebowski (Blu-ray)", Price(100, "USD"))

    (mockRedis.getValues _).expects("product:123:price").returning(Some(Map("value" -> "100", "currency_code" -> "USD")))
    (mockRedis.setValues _).expects("product:123:price", Vector(("currency_code", "MXP"), ("value", 200)))
    (mockApiProxy.singleRequest(_: HttpRequest)(_: ActorSystem, _: Materializer)).expects(*, *, *).returning(Future {
      HttpResponse(200, entity = validProductJson)
    })

    Put("/product/123", Price(200, "MXP")) ~> routes ~> check {
      status shouldBe OK
      responseAs[Product] shouldBe expectedProduct
    }
  }
}
