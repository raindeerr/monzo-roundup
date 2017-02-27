package auth

import models.{AuthToken, AuthTokenResponse, EncryptedAuthTokenResponse}
import play.api.Play
import play.api.libs.ws.WSClient
import play.api.mvc.Request
import repositories.MonzoRepository

import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits.global

object AuthHelpers {

  val ws: WSClient = Play.current.injector.instanceOf[WSClient]

  val monzoRepository = MonzoRepository

  def exchangeAuthForAccessToken(authorisationToken: String): Future[AuthTokenResponse] = {
    val authTokenRequest = AuthToken(
      grantType = "authorization_code",
      clientId = MonzoAuth.clientId,
      clientSecret = MonzoAuth.clientSecret,
      redirectUri = MonzoAuth.redirectUri,
      code = authorisationToken
    )

    ws.url("https://api.monzo.com/oauth2/token").post(AuthToken.toFormData(authTokenRequest)).map {
      response =>
        response.json.as[AuthTokenResponse]
    }

  }

  def getAccountId(authToken: String)(implicit request: Request[_]): Future[String] = {
    ws.url("https://api.monzo.com/accounts").withHeaders(("Authorization", s"Bearer $authToken")).get.map {
      response =>
        (response.json \\ "id").headOption.map(_.as[String]).getOrElse("")
    }
  }

  def refreshAccess(authData: AuthTokenResponse)(actionToRetry: Option[EncryptedAuthTokenResponse] => Any): Future[Any] = {
    val formData  = Map(
      "grant_type" -> Seq("refresh_token"),
      "client_id" -> Seq(MonzoAuth.clientId),
      "client_secret" -> Seq(MonzoAuth.clientSecret),
      "refresh_token" -> Seq(authData.refreshToken)
    )

    ws.url("https://api.monzo.com/oauth2/token").post(formData).flatMap {
      refreshResponse =>
        val blah = refreshResponse.json.as[AuthTokenResponse].copy(accountId = authData.accountId)

        monzoRepository.save(blah.encrypt).flatMap { wr =>
          monzoRepository.findByAccountId(blah.accountId).map {
            a =>
              actionToRetry(a)
          }
        }
    }
  }

}
