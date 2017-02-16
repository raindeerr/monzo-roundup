package controllers

import javax.inject.Inject

import models.{EncryptedAuthTokenResponse, Months, Transaction, Transactions}
import play.api.Logger
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, Controller, Session}

import scala.collection.immutable.ListMap
import scala.math.BigDecimal.RoundingMode
import scala.concurrent.ExecutionContext.Implicits.global

class TransactionsController @Inject() (ws: WSClient) extends Controller {

  /*def showTransactions = Action.async {implicit request =>
    val decryptedSession = EncryptedMoneyboxAuth (request.session.get ("moneyboxUserId").getOrElse (""), request.session.get ("moneyboxBearerToken").getOrElse ("") ).decrypt

    val session = request.session.data

    //val accessToken = Crypto.decryptAES(session.getOrElse("accessToken", ""))
    val accessToken = session.getOrElse ("accessToken", "")

    println (s"----- firstAccessToken:  $accessToken")

    AuthHelpers.getAccountId (accessToken).flatMap {accountId =>
      ws.url (s"https://api.monzo.com/transactions?account_id=$accountId").withHeaders (("Authorization", s"Bearer $accessToken") ).get.map {
        response =>
          Logger.info(s"[TransactionsController][showTransaction] - GET /transaction - ${response.body}")

          val transactions = response.json.as[Transactions]

            println (s"-------  ${transactions.transactions.find (_.id == "tx_000096vkfkkiqeYET8JoMT")}")

            val data = Map (
              "metadata[roundedUp]" -> Seq ("")
            )

            ws.url ("https://api.monzo.com/transactions/tx_000096vkfkkiqeYET8JoMT").withHeaders (("Authorization", s"Bearer $accessToken") ).patch (data).map {
              response =>
                //println(s"----------- response:  ${response.body}")
            }

            val groupedByMonth = groupTransactionsByMonth(transactions)

            val b = calculateRoundupsByMonth(accountId, transactions) (accessToken)

            val sessionData = EncryptedAuthTokenResponse.fromMap (request.session.data) //.decrypt
            println (s"----- accountId:  ${accountId}")
            val newSessionData = sessionData.copy (accountId = accountId)
            println (s"----- newSessionData object:  $newSessionData")
            //println(s"sessionData:   ${EncryptedAuthTokenResponse.fromMap(newSessionData/*.encrypt*/.toMap).decrypt}")
            Ok (views.html.transactions (b, groupedByMonth) ).withSession (Session.apply (newSessionData /*.encrypt*/ .toMap) )
      }
    }
  }*/

  def groupTransactionsByMonth(transactions: Transactions): Seq[((Int, Int), Seq[Transaction])] = {
    val transactionsWithoutTopUps = transactions.transactions.filterNot(_.isLoad)
    val groupedAndSortedByMonth = transactionsWithoutTopUps.groupBy(a => (a.created.getMonthOfYear, a.created.getYear)).toSeq.sortWith(_._1._1 > _._1._1).sortWith(_._1._2 > _._1._2)
    groupedAndSortedByMonth
  }

  def calculateRoundupsByMonth(accountId: String, transactions: Transactions)(accessToken: String): Map[String, BigDecimal] = {
    val byMonth = ListMap(transactions.transactions.filterNot(_.isLoad).groupBy(a => (a.created.getMonthOfYear, a.created.getYear)).toSeq.sortWith(_._1._1 > _._1._1).sortWith(_._1._2 > _._1._2): _*)

    byMonth.map {
      month =>

        val roundUps = month._2.map { eachMonth =>
          val roundedValue = (eachMonth.amount / 100 setScale(0, RoundingMode.UP)).abs
          val rawValue = (eachMonth.amount / 100).abs

          val roundUp = roundedValue - rawValue

          if (roundUp.equals(BigDecimal(0))) BigDecimal(1) // £1 roundups when transaction amount is whole number
          else roundUp
        }.foldLeft(BigDecimal(0))(_ + _)




        val formData = Map(
          "account_id" -> Seq(accountId),
          "type" -> Seq("basic"),
          "url" -> Seq(""),
          "params[title]" -> Seq(s"Round Up for ${Months(month._1._1)} ${month._1._2} - £$roundUps"),
          "params[body]" -> Seq(s"Round ups for ${Months(month._1._1)} ${month._1._2} - £$roundUps"),
          "params[image_url]" -> Seq("https://scontent-lht6-1.xx.fbcdn.net/v/t1.0-9/15871922_10212040156182063_1392533991348799017_n.jpg?oh=4669484d186b91d9b07911255a8d09d3&oe=5940244F")
        )

        //ws.url("https://api.monzo.com/feed").withHeaders(("Authorization", s"Bearer $accessToken")).post(formData)

        (s"${Months(month._1._1)} ${month._1._2}", roundUps)
    }
  }

}
