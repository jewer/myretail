package myretail

case class Price(value: Double, currency: String)
case class Product(id: Long, name: String, price: Price)
