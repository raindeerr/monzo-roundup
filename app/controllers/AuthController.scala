package controllers

import javax.inject.Inject

import play.api.libs.ws.WSClient
import play.api.mvc.{Action, Controller, Session}
import repositories.MonzoRepository
import play.api.{Configuration, Logger}

import scala.concurrent.ExecutionContext.Implicits.global

class AuthController @Inject() (ws: WSClient, configuration: Configuration) extends Controller {

  val monzoRepository = MonzoRepository

  def oauthCallback(code: String, state: String) = Action.async { implicit request =>
    AuthHelpers.exchangeAuthForAccessToken(code).flatMap { authStuff =>

      val authDataFuture = AuthHelpers.getAccountId(authStuff.accessToken).map { accountId =>
        authStuff.copy(accountId = accountId)
      }

      authDataFuture.map { authData =>
        monzoRepository.save(authData.encrypt).map {
          a =>
            if (a.nModified == 0) {
              registerForWebhook(authData.accountId, authData.accessToken)
            }
        }

        Redirect(controllers.routes.MoneyboxController.enterDetails)
          .withSession(Session(authData.toMap))
      }
    }
  }

  def registerForWebhook(accountId: String, accessToken: String) = {
    val webhookUrl = configuration.getString("webhook.callback.url").getOrElse("http://localhost:9500")//"https://monzo-roundup.herokuapp.com/callback/transaction"

    val formData = Map(
      "account_id" -> Seq(accountId),
      "url" -> Seq(webhookUrl + "/callback/transaction")
    )

    ws.url("https://api.monzo.com/webhooks").withHeaders(("Authorization", s"Bearer $accessToken")).post(formData).map {
      response =>
        Logger.info(s"[AuthController][registerForWebhook] - Status code: ${response.status}")
        Logger.info(s"[AuthController][registerForWebhook] - Response body: ${response.body}")
        Logger.info(s"[AuthController][registerForWebhook] - created webhook for $accountId at $webhookUrl")
        Ok
    }
  }


}
