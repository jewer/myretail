package myretail


import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.{ActorMaterializer}
import com.typesafe.config.ConfigFactory

object MyRetailService extends App with ProductServices {

  override implicit val system = ActorSystem("myRetail")
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorMaterializer()

  override val config = ConfigFactory.load()
  override val logger = Logging(system, getClass)
  override val redis = new RedisProxy()
  override val apiProxy = new ApiProxy()

  Http().bindAndHandle(routes, config.getString("http.bindInterface"), config.getInt("http.bindPort"))
}