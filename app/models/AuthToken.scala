package models

import play.api.libs.json.{JsPath, Reads}
import play.api.libs.functional.syntax._

case class AuthToken(grantType: String, clientId: String, clientSecret: String, redirectUri: String, code: String)

object AuthToken {
  implicit val authTokenReads: Reads[AuthToken] = (
    (JsPath \ "grant_type").read[String] and
    (JsPath \ "client_id").read[String] and
    (JsPath \ "client_secret").read[String] and
    (JsPath \ "redirect_uri").read[String] and
    (JsPath \ "code").read[String]
  )(AuthToken.apply _)

  def toFormData(authToken: AuthToken) = {
    Map(
      "grant_type" -> Seq(authToken.grantType),
      "client_id" -> Seq(authToken.clientId),
      "client_secret" -> Seq(authToken.clientSecret),
      "redirect_uri" -> Seq(authToken.redirectUri),
      "code" -> Seq(authToken.code)
    )
  }
}
