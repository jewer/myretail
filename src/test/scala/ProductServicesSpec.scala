package myretail

import akka.event.NoLogging
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.{HttpResponse, HttpRequest}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.Flow
import org.scalatest._

class ProductServicesSpec extends FlatSpec with Matchers with ScalatestRouteTest with ProductServices {
  override def testConfigSource = "akka.loglevel = WARNING"
  override def config = testConfig
  override val logger = NoLogging

  "ProductServices" should "extract name from target json" in {
    val json = """{"product_composite_response":{"request_attributes":[{"name":"product_id","value":"13860428"},{"name":"key","value":"43cJWpLjH8Z8oR18KdrZDBKAgLLQKJjz"},{"name":"id_type","value":"TCIN"},{"name":"fields","value":"descriptions"}],"items":[{"identifier":[{"id_type":"DPCI","id":"058-34-0436","is_primary":null,"source":"Online and Store"},{"id_type":"TCIN","id":"13860428","is_primary":null,"source":"Online"}],"relation":"TAC","relation_description":"Title Authority Child","data_page_link":"http://www.target.com/p/the-big-lebowski-blu-ray/-/A-13860428","imn_identifier":12244586,"parent_id":"46767107","is_orderable":true,"is_sellable":true,"general_description":"Blu-ray BIG LEBOWSKI, THE Movies","is_circular_publish":true,"business_process_status":[{"process_status":{"is_ready":true,"operation_description":"assortment ready","operation_code":"PAAP"}},{"process_status":{"is_ready":false,"operation_description":"import ready","operation_code":"PIPT"}},{"process_status":{"is_ready":true,"operation_description":"order ready","operation_code":"PORD"}},{"process_status":{"is_ready":true,"operation_description":"presentation ready","operation_code":"PPRS"}},{"process_status":{"is_ready":true,"operation_description":"project ready","operation_code":"PCMT"}},{"process_status":{"is_ready":true,"operation_description":"replenishment ready","operation_code":"PRPL"}},{"process_status":{"is_ready":false,"operation_description":"scale ready","operation_code":"PSCL"}},{"process_status":{"is_ready":true,"operation_description":"target.com ready","operation_code":"PTGT"}}],"dpci":"058-34-0436","department_id":58,"class_id":34,"item_id":436,"online_description":{"value":"The Big Lebowski (Blu-ray)","type":"GENL"},"store_description":{"value":"Blu-ray BIG LEBOWSKI, THE Movies","type":"GENL"},"alternate_description":[{"type":"GENL","value":"Blu-ray BIG LEBOWSKI, THE Movies","type_description":"General Desc"},{"type":"VEND","value":"BIG LEBOWSKI BD","type_description":"Vendor Description"},{"type":"SHLF","value":"BIG LEBOWSKI, THE UNIV","type_description":"Shelf Desc"},{"type":"POS","value":"BLU-RAY","type_description":"POS Desc"},{"type":"ADSG","value":"The Big Lebowski:<\/Blu-ray Disc","type_description":"Ad Signage Description"}],"features":[{"feature":"Movie Genre: Comedy"},{"feature":"Software Format: Blu-ray"},{"feature":"Movie Studio: Universal Studios"}]}]}}"""
    val parsed = extractProductName(json)

    parsed shouldBe "The Big Lebowski (Blu-ray)"
  }
}
