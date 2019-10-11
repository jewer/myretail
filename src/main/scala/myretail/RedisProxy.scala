package myretail

import com.redis._
import com.typesafe.config.ConfigFactory

class RedisProxy extends IRedisProxy {
  lazy val config = ConfigFactory.load()
  lazy val client = new RedisClient(config.getString("database.target-product-pricing.uri"), config.getInt("database.target-product-pricing.port"))

  override def getValues(key: String): Option[Map[String, String]] = client.hgetall(key)

  override def setValue(key: String, subKey: String, value: Any): Unit = client.hmset(key, Vector((subKey, value)))

  override def setValues(key: String, values: Iterable[(String, Any)]): Unit = client.hmset(key, values)
}

trait IRedisProxy {
  def getValues(key: String): Option[Map[String, String]]

  def setValue(key: String, subKey: String, value: Any)

  def setValues(key: String, values: Iterable[(String, Any)])
}
