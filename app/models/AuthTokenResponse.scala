package models

import play.api.libs.json.{JsPath, Json, Reads}
import play.api.libs.functional.syntax._

case class AuthTokenResponse(access_token: String, client_id: String, expires_in: Long, refresh_token: String, token_type: String, user_id: String)

object AuthTokenResponse {
  //implicit val formats = Json.format[AuthTokenResponse]

  implicit val authTokenResponseReads: Reads[AuthTokenResponse] = (
    (JsPath \ "access_token").read[String] and
    (JsPath \ "client_id").read[String] and
    (JsPath \ "expires_in").read[Long] and
    (JsPath \ "refresh_token").read[String] and
    (JsPath \ "token_type").read[String] and
    (JsPath \ "user_id").read[String]
  )(AuthTokenResponse.apply _)
}
