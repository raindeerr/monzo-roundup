package controllers

import java.util.UUID
import javax.inject._

import models.{AuthToken, AuthTokenResponse, Months, Transactions}
import play.api._
import play.api.cache.CacheApi
import play.api.mvc._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.ws.WSClient

import scala.collection.immutable.ListMap
import scala.concurrent.Future
import scala.math.BigDecimal.RoundingMode

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class HomeController @Inject()(val messagesApi: MessagesApi, ws: WSClient, cache: CacheApi) extends Controller with I18nSupport {

  def index = Action {
    Ok(views.html.index())
  }

  def startAuth = Action { implicit request =>
    Redirect(MonzoAuth.authUrl)
  }

  def oauthCallback(code: String, state: String) = Action.async { implicit request =>
    exchangeAuthForAccessToken(code).map { authStuff =>
      cache.set("1", authStuff)
      Redirect(controllers.routes.HomeController.showTransactions)
    }
  }

  def showTransactions = Action.async { implicit request =>
    cache.get[AuthTokenResponse]("1").map { authToken =>
      getAccountId(authToken).flatMap { accountId =>
        ws.url(s"https://api.monzo.com/transactions?account_id=$accountId").withHeaders(("Authorization", s"Bearer ${authToken.accessToken}")).get.map {
          response =>
            cache.set("1", authToken)

            val transactions = response.json.as[Transactions]

            val b = calculateRoundupsByMonth(accountId, transactions)(authToken.accessToken)

            Ok(views.html.transactions(b))
        }
      }
    }.getOrElse(Future.successful(NotFound))
  }

  def getAccountId(authToken: AuthTokenResponse): Future[String] = {
    ws.url("https://api.monzo.com/accounts").withHeaders(("Authorization", s"Bearer ${authToken.accessToken}")).get.map {
      response =>
        (response.json \\ "id").headOption.map(_.as[String]).getOrElse("")
    }
  }

  def calculateRoundupsByMonth(accountId: String, transactions: Transactions)(accessToken: String): Map[String, BigDecimal] = {
    val byMonth = ListMap(transactions.transactions.filterNot(_.isLoad).groupBy(a => (a.created.getMonthOfYear, a.created.getYear)).toSeq.sortWith(_._1._1 > _._1._1).sortWith(_._1._2 > _._1._2): _*)

    val b: Map[String, BigDecimal] = byMonth.map {
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
    println(s"total to round up:  ${b.values.toSeq.foldLeft(BigDecimal(0))(_ + _)}")

    b
  }

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



}

object MonzoAuth {

  val clientId: String = Play.current.configuration.getString("monzo.clientId").getOrElse("")

  val clientSecret: String = Play.current.configuration.getString("monzo.clientSecret").getOrElse("")

  val redirectUri: String = "http://localhost:9000/oauth/callback"

  def stateToken: String = UUID.randomUUID().toString

  val authUrl: String =
    s"""https://auth.getmondo.co.uk/?client_id=$clientId&redirect_uri=$redirectUri&response_type=code&state=$stateToken"""

}
