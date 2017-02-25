package myretail

import com.redis._
import com.typesafe.config.ConfigFactory

object RedisProxy {
  private val config = ConfigFactory.load()

  def apply() = new RedisProxy
}

class RedisProxy extends IRedisProxy {
  private val config = RedisProxy.config
  private val client = new RedisClient(config.getString("database.target-product-pricing.uri"), config.getInt("database.target-product-pricing.port"))

  override def getHashMap(key: String): Option[Map[String, String]] = client.hgetall(key)

  override def setValue(key: String, subKey: String, value: Any): Unit = client.hmset(key, Vector((subKey, value)))

  override def setValues(key: String, values: Iterable[(String, Any)]): Unit = client.hmset(key, values)
}

trait IRedisProxy {
  def getHashMap(key: String): Option[Map[String, String]]
  def setValue(key: String, subKey: String, value: Any)
  def setValues(key: String, values: Iterable[(String, Any)])
}
