package models

import play.api.libs.json.{JsPath, Json, Reads}
import play.api.libs.functional.syntax._

case class AuthTokenResponse(accessToken: String, clientId: String, expiresIn: Long, refreshToken: String, tokenType: String, userId: String)

object AuthTokenResponse {
  implicit val authTokenResponseReads: Reads[AuthTokenResponse] = (
    (JsPath \ "access_token").read[String] and
    (JsPath \ "client_id").read[String] and
    (JsPath \ "expires_in").read[Long] and
    (JsPath \ "refresh_token").read[String] and
    (JsPath \ "token_type").read[String] and
    (JsPath \ "user_id").read[String]
  )(AuthTokenResponse.apply _)
}
