package myretail

case class Price(value: Double, currency_code: String)
case class Product(id: Long, name: String, price: Price)
case class Error(message: String)
