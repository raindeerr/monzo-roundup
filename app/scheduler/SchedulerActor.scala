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
      val hourOfDay = DateTime.now().getHourOfDay
      if (hourOfDay > 4 && hourOfDay < 6) {
        moneyboxRepository.findAllForRoundup.map { forRoundUp =>
          forRoundUp.foreach { roundUp =>
            val poundSign = "\u00a3"

            val decryptedMoneyboxDetails = roundUp.decrypt

            val balanceToSave = decryptedMoneyboxDetails.roundUpBalance.setScale(0, RoundingMode.DOWN)

            if (balanceToSave == BigDecimal(0)) {
              Logger.info(s"[SchedulerActor][CheckForRoundUps] - not enough to do a top up")
            } else {
              Logger.info(s"[SchedulerActor][CheckForRoundUps] - doing a top up")
              val headers = Seq("AppId" -> "8cb2237d0679ca88db6464", "AppVersion" -> "1.0.13", "Authorization" -> s"Bearer ${decryptedMoneyboxDetails.bearerToken}")

              Logger.info(
                s"""[SchedulerActor][CheckForRoundUps] - balance before top up: $poundSign${decryptedMoneyboxDetails.roundUpBalance} - topping up: $poundSign$balanceToSave
                   |  for account: ${decryptedMoneyboxDetails.monzoAccountId} - user: ${decryptedMoneyboxDetails.emailAddress}""".stripMargin)

              val body = Json.obj(
                "Currency" -> "GBP",
                "UserId" -> decryptedMoneyboxDetails.userId,
                "Amount" -> balanceToSave
              )

              ws.url("https://api.moneyboxapp.com/payments").withHeaders(headers: _*).post(body).map {
                case response if response.status == 200 =>
                  Logger.info(s"[SchedulerActor][CheckForRoundUps] - successful top up - ${response.status} - ${response.body}")
                  Logger.info(s"[SchedulerActor][CheckForRoundUps] - Successfully topped up ${decryptedMoneyboxDetails.userId} with $poundSign$balanceToSave")
                  moneyboxRepository.updateBalance(decryptedMoneyboxDetails.monzoAccountId, a => a - balanceToSave).map { _ =>
                    monzoRepository.findByAccountId(decryptedMoneyboxDetails.monzoAccountId).map { monzoAccount =>
                      sendMoneyboxTopUpFeedItem(
                        monzoAccount.map(_.decrypt.accessToken).getOrElse(""),
                        monzoAccount.map(_.decrypt.accountId).getOrElse(""),
                        balanceToSave
                      ).map {
                        case feedResponse if feedResponse.status == 200 =>
                          Logger.info(s"[SchedulerActor][CheckForRoundUps] - Posting to monzo feed - status: ${feedResponse.status}")
                        case feedResponse if feedResponse.status == 401 =>
                          Logger.error(s"[SchedulerActor][CheckForRoundUps] - Posting to monzo feed - status: ${feedResponse.status}")
                          monzoAccount.map { account =>
                            Logger.info(s"[SchedulerActor][CheckForRoundUps] - Found monzo account - account: $account")
                            AuthHelpers.refreshAccess(account.decrypt){ newAuth =>
                              sendMoneyboxTopUpFeedItem(
                                newAuth.map(_.decrypt.accessToken).getOrElse(""),
                                newAuth.map(_.decrypt.accountId).getOrElse(""),
                                balanceToSave
                              )
                            }

                          }
                        case feedResponse =>
                          Logger.error(s"[SchedulerActor][CheckForRoundUps] - Posting to monzo feed - status: ${feedResponse.status} - body: ${feedResponse.body}")
                      }
                    }
                  }
                case response if response.status == 401 =>
                  Logger.error(s"[SchedulerActor][CheckForRoundUps] - Need to re auth moneybox for ${decryptedMoneyboxDetails.emailAddress}")
                  val dc = decryptedMoneyboxDetails
                  MoneyboxAuthHelpers.moneyboxLogin(dc.emailAddress, dc.password, dc.monzoAccountId).map { response =>
                    Logger.info(s"[SchedulerActor][CheckForRoundUps] - Logged back into moneybox")
                    moneyboxRepository.findByAccountId(dc.monzoAccountId).map { a =>
                      val newHeaders = Seq("AppId" -> "8cb2237d0679ca88db6464", "AppVersion" -> "1.0.13", "Authorization" -> s"Bearer ${a.map(_.bearerToken).getOrElse("")}")
                      ws.url("https://api.moneyboxapp.com/payments").withHeaders(newHeaders: _*).post(body).map {
                        case reauthedMoneyboxResponse if reauthedMoneyboxResponse.status == 200 =>
                          moneyboxRepository.updateBalance(a.map(_.monzoAccountId).getOrElse(""), a => a - balanceToSave).map { _ =>
                            monzoRepository.findByAccountId(decryptedMoneyboxDetails.monzoAccountId).map { monzoAccount =>
                              sendMoneyboxTopUpFeedItem(
                                monzoAccount.map(_.decrypt.accessToken).getOrElse(""),
                                monzoAccount.map(_.decrypt.accountId).getOrElse(""),
                                balanceToSave
                              ).map {
                                case feedResponse if feedResponse.status == 200 =>
                                  Logger.info(s"[SchedulerActor][CheckForRoundUps] - Posting to monzo feed - status: ${feedResponse.status} - body: ${feedResponse.body}")
                                case feedResponse if feedResponse.status == 401 =>
                                  Logger.error(s"[SchedulerActor][CheckForRoundUps] - Posting to monzo feed - status: ${feedResponse.status} - body: ${feedResponse.body}")
                                  monzoAccount.map { account =>
                                    Logger.info(s"[SchedulerActor][CheckForRoundUps] - Found monzo account - account: $account")
                                    AuthHelpers.refreshAccess(account.decrypt) { newAuth =>
                                      sendMoneyboxTopUpFeedItem(
                                        newAuth.map(_.decrypt.accessToken).getOrElse(""),
                                        newAuth.map(_.decrypt.accountId).getOrElse(""),
                                        balanceToSave
                                      )
                                    }

                                  }
                                case feedResponse =>
                                  Logger.error(s"[SchedulerActor][CheckForRoundUps] - Posting to monzo feed - status: ${feedResponse.status} - body: ${feedResponse.body}")
                              }
                            }
                          }
                        case reauthedMoneyboxResponse if reauthedMoneyboxResponse.status == 401 =>
                          Logger.error(s"[SchedulerActor][CheckForRoundUps] - reauth failed - aborting top up for user ${decryptedMoneyboxDetails.emailAddress} - ${decryptedMoneyboxDetails.monzoAccountId}")
                      }
                    }
                  }
                case e => Logger.error(s"[SchedulerActor][CheckForRoundUps] - Call failed:  ${e.status} -- ${e.body}")

              }


            }


          }


        }
      }

  }

  def sendMoneyboxTopUpFeedItem(accessToken: String, accountId: String, amountToppedUp: BigDecimal): Future[WSResponse] = {
    val poundSign = "\u00a3"
    val formattedRoundUp = amountToppedUp.setScale(2)

    val formData = Map(
      "account_id" -> Seq(accountId),
      "type" -> Seq("basic"),
      "url" -> Seq(""),
      "params[title]" -> Seq(s"$poundSign$formattedRoundUp sent to Moneybox"),
      "params[body]" -> Seq(s"We've just topped up $poundSign$formattedRoundUp to your Moneybox account."),
      "params[image_url]" -> Seq("https://www.oxcp.com/content/uploads/2016/07/Moneybox-small-440x390.png")
    )

    ws.url("https://api.monzo.com/feed").withHeaders(("Authorization", s"Bearer $accessToken")).post(formData).map {
      response =>
        Logger.info(s"[WebhookController][sendRoundUpFeedItem] - response status:  ${response.status}")
        Logger.info(s"[WebhookController][sendRoundUpFeedItem] - response body:  ${response.body}")
        response
    }
  }


}
