package controllers

import javax.inject.Inject

import auth.{AuthHelpers, MoneyboxAuthHelpers}
import models.{AuthTokenResponse, EncryptedMoneyboxAuth}
import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc.{Action, Controller}
import repositories.{MoneyboxRepository, MonzoRepository}

import scala.math.BigDecimal.RoundingMode
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class WebhookController @Inject() (messagesApi: MessagesApi, ws: WSClient) extends Controller {

  val moneyboxRepository = MoneyboxRepository

  val monzoRepository = MonzoRepository

  def receiveTransactionCreatedEvent = Action { implicit request =>
    request.body.asJson.map { json =>
      val accountId = (json \ "data" \ "account_id").as[String]
      moneyboxRepository.findByAccountId(accountId).map {
        account =>
        account.map { accountDetails =>
          (json \ "data" \ "amount").as[BigDecimal].signum == -1

          if (!(json \ "data" \ "is_load").as[Boolean] && (json \ "data" \ "amount").as[BigDecimal].signum == -1) {

            val amount = (json \ "data" \ "amount").as[BigDecimal]
            val roundedValue = (amount / 100 setScale(0, RoundingMode.UP)).abs
            val rawValue = (amount / 100).abs

            val initialRoundUp = roundedValue - rawValue

            val poundRoundUp = if (initialRoundUp == BigDecimal(0)) BigDecimal(1) else initialRoundUp // £1 round ups

            moneyboxRepository.updateBalance(accountId, a => a + poundRoundUp).map { wr =>
              monzoRepository.findByAccountId(accountId).map { authStuffOption =>
                authStuffOption.map { authStuff =>

                  val decryptedAuthStuff = authStuff.decrypt

                  val data = Map(
                    "metadata[roundedUp]" -> Seq("true")
                  )

                  val transactionId = (json \ "data" \ "id").as[String]

                  def addRoundUpMetadata(accessToken: String) = ws.url(s"https://api.monzo.com/transactions/$transactionId").withHeaders(("Authorization", s"Bearer ${decryptedAuthStuff.accessToken}")).patch(data).map {
                    case response if response.status == 200 =>
                      Logger.info(s"[WebhookController][receiveTransactionCreatedEvent] - sending feed item to $accountId for $transactionId")
                      sendRoundUpFeedItem(accessToken, accountId, poundRoundUp, transactionId)
                    case response if response.status == 401 =>
                      Logger.error(s"[WebhookController][receiveTransactionCreatedEvent] - 401 while sending feed item to $accountId for $transactionId")
                      AuthHelpers.refreshAccess(decryptedAuthStuff) { accessDataOption =>
                        accessDataOption.map { accessData =>
                          sendRoundUpFeedItem(accessData.accessToken, accessData.accountId, poundRoundUp, transactionId)
                        }
                      }
                  }
                  addRoundUpMetadata(decryptedAuthStuff.accessToken)
                }
              }

            }


          } else {
            BadRequest
          }

        }
      }
    }
    Ok
  }

  def sendRoundUpFeedItem(accessToken: String, accountId: String, roundUp: BigDecimal, transactionId: String): Future[WSResponse] = {
    val formattedRoundUp = roundUp.setScale(2)

    val formData = Map(
      "account_id" -> Seq(accountId),
      "type" -> Seq("basic"),
      "url" -> Seq(""),
      "params[title]" -> Seq(s"Rounded up £$formattedRoundUp"),
      "params[body]" -> Seq(s"Rounded up £$formattedRoundUp from transaction $transactionId"),
      "params[image_url]" -> Seq("https://res.cloudinary.com/xsoarphotos/image/upload/v1479747582/Bbr07x0J_400x400_qirewn.jpg")
    )

    ws.url("https://api.monzo.com/feed").withHeaders(("Authorization", s"Bearer $accessToken")).post(formData).map {
      response =>
        Logger.info(s"[WebhookController][sendRoundUpFeedItem] - response status:  ${response.status}")
        Logger.info(s"[WebhookController][sendRoundUpFeedItem] - response body:  ${response.body}")
        response
    }
  }

  def sendRoundUpToMoneybox(accountDetails: EncryptedMoneyboxAuth, monzoAccountId: String) = {
    val decryptAccountDetails = accountDetails.decrypt
    val postData = Json.obj(
      "Currency" -> "GBP",
      "UserId" -> decryptAccountDetails.userId,
      "Amount" -> 1
    )

    ws.url("https://api.moneyboxapp.com/payments").withHeaders("AppId" -> "8cb2237d0679ca88db6464", "AppVersion" -> "1.0.13", "Authorization" -> s"Bearer ${decryptAccountDetails.bearerToken}").post(postData).map {
      case response if response.status == 200 =>
        Logger.info(s"[WebhookController][sendRoundUpToMoneyBox] - moneybox topup response status code:  ${response.status}")
        response
      case response if response.status == 401 =>
        Logger.error(s"[WebhookController][sendRoundUpToMoneyBox] - moneybox topup response status code:  ${response.status}")
        MoneyboxAuthHelpers.moneyboxLogin(decryptAccountDetails.emailAddress, decryptAccountDetails.password, monzoAccountId)
    }

  }

}
