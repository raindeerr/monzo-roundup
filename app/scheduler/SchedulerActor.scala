package scheduler

import javax.inject.Inject

import akka.actor.Actor
import auth.{AuthHelpers, MoneyboxAuthHelpers}
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import repositories.{MoneyboxRepository, MonzoRepository}

import scala.math.BigDecimal.RoundingMode
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case object CheckForRoundUps

class SchedulerActor @Inject() (ws: WSClient) extends Actor {

  val moneyboxRepository = MoneyboxRepository

  val monzoRepository = MonzoRepository

  override def receive = {
    case CheckForRoundUps =>
      Logger.info(s"[SchedulerActor][Tick] - calling scheduler with current time - ${DateTime.now()}")
      val hourOfDay = DateTime.now().getHourOfDay
      if (hourOfDay > 5 && hourOfDay < 8) {
        moneyboxRepository.findAllForRoundup.map { forRoundUp =>
          forRoundUp.foreach { roundUp =>
            val decryptedMoneyboxDetails = roundUp.decrypt

            val balanceToSave = decryptedMoneyboxDetails.roundUpBalance.setScale(0, RoundingMode.DOWN)

            if (balanceToSave == BigDecimal(0)) {
              Logger.info(s"[SchedulerActor][Tick] - not enough to do a top up")
            } else {
              Logger.info(s"[SchedulerActor][Tick] - doing a top up")
              val headers = Seq("AppId" -> "8cb2237d0679ca88db6464", "AppVersion" -> "1.0.13", "Authorization" -> s"Bearer ${decryptedMoneyboxDetails.bearerToken}")

              val body = Json.obj(
                "Currency" -> "GBP",
                "UserId" -> decryptedMoneyboxDetails.userId,
                "Amount" -> balanceToSave
              )

              ws.url("https://api.moneyboxapp.com/payments").withHeaders(headers: _*).post(body).map {
                case response if response.status == 200 =>
                  Logger.info(s"[SchedulerActor][Tick] - Successfully topped up ${decryptedMoneyboxDetails.userId} with $balanceToSave")
                  moneyboxRepository.updateBalance(decryptedMoneyboxDetails.monzoAccountId, a => a - balanceToSave).map { _ =>
                    monzoRepository.findByAccountId(decryptedMoneyboxDetails.monzoAccountId).map { monzoAccount =>
                      sendMoneyboxTopUpFeedItem(
                        monzoAccount.map(_.decrypt.accessToken).getOrElse(""),
                        monzoAccount.map(_.decrypt.accountId).getOrElse(""),
                        balanceToSave
                      ).map {
                        case feedResponse if feedResponse.status == 200 =>
                          Logger.info(s"[SchedulerActor][Tick] - Posting to monzo feed - status: ${feedResponse.status}")
                        case feedResponse if feedResponse.status == 401 =>
                          Logger.error(s"[SchedulerActor][Tick] - Posting to monzo feed - status: ${feedResponse.status}")
                          monzoAccount.map { account =>
                            Logger.info(s"[SchedulerActor][Tick] - Found monzo account - account: $account")
                            AuthHelpers.refreshAccess(account.decrypt){ newAuth =>
                              sendMoneyboxTopUpFeedItem(
                                newAuth.map(_.decrypt.accessToken).getOrElse(""),
                                newAuth.map(_.decrypt.accountId).getOrElse(""),
                                balanceToSave
                              )
                            }

                          }
                        case feedResponse =>
                          Logger.error(s"[SchedulerActor][Tick] - Posting to monzo feed - status: ${feedResponse.status} - body: ${feedResponse.body}")
                      }
                    }
                  }
                case response if response.status == 401 =>
                  Logger.error(s"[SchedulerActor][Tick] - Need to re auth")
                  val dc = decryptedMoneyboxDetails
                  MoneyboxAuthHelpers.moneyboxLogin(dc.emailAddress, dc.password, dc.monzoAccountId).map { response =>
                    Logger.info(s"[SchedulerActor][Tick] - Logged back into moneybox")
                    moneyboxRepository.findByAccountId(dc.monzoAccountId).map { a =>
                      val newHeaders = Seq("AppId" -> "8cb2237d0679ca88db6464", "AppVersion" -> "1.0.13", "Authorization" -> s"Bearer ${a.map(_.bearerToken).getOrElse("")}")
                      ws.url("https://api.moneyboxapp.com/payments").withHeaders(newHeaders: _*).post(body).map { _ =>
                        moneyboxRepository.updateBalance(a.map(_.monzoAccountId).getOrElse(""), a => a - balanceToSave)
                      }
                    }
                  }
                case e => Logger.error(s"[SchedulerActor][Tick] - Call failed:  ${e.status} -- ${e.body}")

              }


            }


          }


        }
      }

  }

  def sendMoneyboxTopUpFeedItem(accessToken: String, accountId: String, amountToppedUp: BigDecimal): Future[WSResponse] = {
    val formattedRoundUp = amountToppedUp.setScale(2)

    val formData = Map(
      "account_id" -> Seq(accountId),
      "type" -> Seq("basic"),
      "url" -> Seq(""),
      "params[title]" -> Seq(s"Topped up $formattedRoundUp to Moneybox for $accountId"),
      "params[body]" -> Seq(s"Topped up $formattedRoundUp to Moneybox for $accountId"),
      "params[image_url]" -> Seq("https://scontent-lht6-1.xx.fbcdn.net/v/t1.0-9/15871922_10212040156182063_1392533991348799017_n.jpg?oh=4669484d186b91d9b07911255a8d09d3&oe=5940244F")
    )

    ws.url("https://api.monzo.com/feed").withHeaders(("Authorization", s"Bearer $accessToken")).post(formData).map {
      response =>
        Logger.info(s"[WebhookController][sendRoundUpFeedItem] - response status:  ${response.status}")
        Logger.info(s"[WebhookController][sendRoundUpFeedItem] - response body:  ${response.body}")
        response
    }
  }


}
