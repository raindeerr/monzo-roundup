package models

import org.joda.time.DateTime
import play.api.libs.json._
import play.api.libs.functional.syntax._

case class Transaction(id: String, created: DateTime, description: String, amount: BigDecimal, currency: String, merchant: Option[String], notes: String, metadata: Map[String, String], accountBalance: BigDecimal, attachments: Seq[String], category: String, isLoad: Boolean, settled: String,
                       localAmount: BigDecimal, localCurrency: String, updated: DateTime, accountId: String, scheme: String, dedupeId: String, originator: Boolean, includeInSpending: Boolean)

object Transaction {
  implicit val yourJodaDateReads = Reads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss.SSSZ").orElse(Reads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ssZ"))
  implicit val yourJodaDateWrites = Writes.jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss.SSSZ'")

  implicit val transactionReads: Reads[Transaction] = (
    (JsPath \ "id").read[String] and
    (JsPath \ "created").read[DateTime] and
    (JsPath \ "description").read[String] and
    (JsPath \ "amount").read[BigDecimal] and
    (JsPath \ "currency").read[String] and
    (JsPath \ "merchant").readNullable[String] and
    (JsPath \ "notes").read[String] and
    (JsPath \ "metadata").read[Map[String, String]] and
    (JsPath \ "account_balance").read[BigDecimal] and
    (JsPath \ "attachments").read[Seq[String]] and
    (JsPath \ "category").read[String] and
    (JsPath \ "is_load").read[Boolean] and
    (JsPath \ "settled").read[String] and
    (JsPath \ "local_amount").read[BigDecimal] and
    (JsPath \ "local_currency").read[String] and
    (JsPath \ "updated").read[DateTime] and
    (JsPath \ "account_id").read[String] and
    (JsPath \ "scheme").read[String] and
    (JsPath \ "dedupe_id").read[String] and
    (JsPath \ "originator").read[Boolean] and
    (JsPath \ "include_in_spending").read[Boolean]
  )(Transaction.apply _)

  implicit val formats = Format(transactionReads, Json.writes[Transaction])


}
