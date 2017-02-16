package controllers

import java.util.UUID
import javax.inject._

import models._
import play.api._
import play.api.cache.CacheApi
import play.api.mvc._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.ws.WSClient
import repositories.{MoneyboxRepository, MonzoRepository}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class HomeController @Inject()(val messagesApi: MessagesApi, ws: WSClient, cache: CacheApi) extends Controller with I18nSupport {

  val moneyboxRepository = MoneyboxRepository

  val monzoRepository = MonzoRepository

  def index = Action {
    Ok(views.html.index())
  }

  def startAuth = Action { implicit request =>
    Redirect(MonzoAuth.authUrl)
  }

}

object AuthHelpers {

  val ws: WSClient = Play.current.injector.instanceOf[WSClient]

  val monzoRepository = MonzoRepository

  def exchangeAuthForAccessToken(authorisationToken: String) = {
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

object MonzoAuth {

  val clientId: String = Play.current.configuration.getString("monzo.clientId").getOrElse("")

  val clientSecret: String = Play.current.configuration.getString("monzo.clientSecret").getOrElse("")

  val redirectBaseUri: String = Play.current.configuration.getString("monzo.redirectUri").getOrElse("http://localhost:9500")
  val redirectUri: String = redirectBaseUri + "/oauth/callback"

  def stateToken: String = UUID.randomUUID().toString

  val authUrl: String =
    s"""https://auth.getmondo.co.uk/?client_id=$clientId&redirect_uri=$redirectUri&response_type=code&state=$stateToken"""

}
