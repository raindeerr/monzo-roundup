package models

import play.api.libs.json.Json

case class Transactions(transactions: Seq[Transaction])

object Transactions {
  implicit val formats = Json.format[Transactions]
}
