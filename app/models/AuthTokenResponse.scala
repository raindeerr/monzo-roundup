package models

import crypto.CryptoHelpers
import play.api.libs.json.{JsPath, Json, Reads}
import play.api.libs.functional.syntax._

case class AuthTokenResponse(accessToken: String, clientId: String, expiresIn: Long, refreshToken: String, tokenType: String, userId: String, accountId: String) {

  def toMap: Map[String, String] = {
    Map(
      "accessToken" -> accessToken,
      "clientId" -> clientId,
      "expiresIn" -> expiresIn.toString,
      "refreshToken" -> refreshToken,
      "tokenType" -> tokenType,
      "userId" -> userId,
      "accountId" -> accountId
    )
  }

  def encrypt: EncryptedAuthTokenResponse = {
    def enc(toEncrypt: String) = CryptoHelpers.encrypt(toEncrypt)
    EncryptedAuthTokenResponse(
      accessToken = enc(accessToken),
      clientId = enc(clientId),
      expiresIn = enc(expiresIn.toString),
      refreshToken = enc(refreshToken),
      tokenType = enc(tokenType),
      userId = enc(userId),
      accountId = enc(accountId)
    )
  }

}

object AuthTokenResponse {

  implicit val authTokenResponseReads: Reads[AuthTokenResponse] = (
    (JsPath \ "access_token").read[String] and
    (JsPath \ "client_id").read[String] and
    (JsPath \ "expires_in").read[Long] and
    (JsPath \ "refresh_token").read[String] and
    (JsPath \ "token_type").read[String] and
    (JsPath \ "user_id").read[String] and
    (JsPath \ "account_id").readNullable[String].map(_.getOrElse("")) // shouldn't be returned from the JSON
  )(AuthTokenResponse.apply _)

  implicit def authTokenResponseWrites = Json.writes[AuthTokenResponse]
}

case class EncryptedAuthTokenResponse(accessToken: String, clientId: String, expiresIn: String, refreshToken: String, tokenType: String, userId: String, accountId: String) {

  def toMap: Map[String, String] = {
    Map(
      "accessToken" -> accessToken,
      "clientId" -> clientId,
      "expiresIn" -> expiresIn,
      "refreshToken" -> refreshToken,
      "tokenType" -> tokenType,
      "userId" -> userId,
      "accountId" -> accountId
    )
  }

  def decrypt: AuthTokenResponse = {
    def dec(toDecrypt: String) = CryptoHelpers.decrypt(toDecrypt)
    AuthTokenResponse(
      accessToken = dec(accessToken),
      clientId = dec(clientId),
      expiresIn = dec(expiresIn).toLong,
      refreshToken = dec(refreshToken),
      tokenType = dec(tokenType),
      userId = dec(userId),
      accountId = dec(accountId)
    )
  }

}

object EncryptedAuthTokenResponse {

  implicit def formats = Json.format[EncryptedAuthTokenResponse]

  def fromMap(sessionMap: Map[String, String]): EncryptedAuthTokenResponse = {
    EncryptedAuthTokenResponse(
      accessToken = sessionMap.getOrElse("accessToken", ""),
      clientId = sessionMap.getOrElse("clientId", ""),
      expiresIn = sessionMap.getOrElse("expiresIn", ""),
      refreshToken = sessionMap.getOrElse("refreshToken", ""),
      tokenType = sessionMap.getOrElse("tokenType", ""),
      userId = sessionMap.getOrElse("userId", ""),
      accountId = sessionMap.getOrElse("accountId", "")
    )
  }


}
