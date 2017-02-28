package models

import crypto.CryptoHelpers
import play.api.libs.json.{Format, Json}

case class MoneyboxAuth(userId: String, bearerToken: String, emailAddress: String, password: String, roundUpBalance: BigDecimal = BigDecimal(0), monzoAccountId: String, onePoundRoundUps: Boolean = false, topupEnabled: Boolean = true) {

  def encrypt: EncryptedMoneyboxAuth = {
    def enc(toEncrypt: String) = CryptoHelpers.encrypt(toEncrypt)
    EncryptedMoneyboxAuth(
      enc(userId),
      enc(bearerToken),
      enc(emailAddress),
      enc(password),
      roundUpBalance,
      monzoAccountId,
      onePoundRoundUps,
      topupEnabled
    )
  }

}

case class EncryptedMoneyboxAuth(userId: String, bearerToken: String, emailAddress: String, password: String, roundUpBalance: BigDecimal, monzoAccountId: String, onePoundRoundUps: Boolean = false, topupEnabled: Boolean = true) {

  def toMap: Map[String, String] =
    Map(
      "moneyboxUserId" -> userId,
      "moneyboxBearerToken" -> bearerToken
    )

  def decrypt: MoneyboxAuth = {
    def dec(toDecrypt: String) = CryptoHelpers.decrypt(toDecrypt)
    MoneyboxAuth(
      dec(userId),
      dec(bearerToken),
      dec(emailAddress),
      dec(password),
      roundUpBalance,
      monzoAccountId,
      onePoundRoundUps,
      topupEnabled
    )
  }
}

object EncryptedMoneyboxAuth {
  implicit val formats: Format[EncryptedMoneyboxAuth] = Json.format[EncryptedMoneyboxAuth]
}
