package controllers

import javax.inject.Inject

import crypto.CryptoHelpers
import play.api.Play
import play.api.data._
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{Format, Json}
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, Controller, Session}
import repositories.MoneyboxRepository

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class LoginForm(email: String, password: String)

object LoginForm {
  val form = Form(
    mapping(
      "email" -> text,
      "password" -> text
    )(LoginForm.apply)(LoginForm.unapply)
  )
}

case class MoneyboxAuth(userId: String, bearerToken: String, emailAddress: String, password: String, roundUpBalance: BigDecimal = BigDecimal(0), monzoAccountId: String) {

  def encrypt: EncryptedMoneyboxAuth = {
    def enc(toEncrypt: String) = CryptoHelpers.encrypt(toEncrypt)
    EncryptedMoneyboxAuth(
      enc(userId),
      enc(bearerToken),
      enc(emailAddress),
      enc(password),
      roundUpBalance,
      monzoAccountId
    )
  }

}

case class EncryptedMoneyboxAuth(userId: String, bearerToken: String, emailAddress: String, password: String, roundUpBalance: BigDecimal, monzoAccountId: String) {

  def toMap: Map[String, String] =
    Map(
      "moneyboxUserId" -> userId,
      "moneyboxBearerToken" -> bearerToken
    )

  def decrypt: MoneyboxAuth = {
    def dec(toDecrypt: String) = CryptoHelpers.decrypt(toDecrypt)
    MoneyboxAuth(
      dec(userId),
      dec(bearerToken),
      dec(emailAddress),
      dec(password),
      roundUpBalance,
      monzoAccountId
    )
  }
}

object EncryptedMoneyboxAuth {
  implicit val formats: Format[EncryptedMoneyboxAuth] = Json.format[EncryptedMoneyboxAuth]
}

class MoneyboxController @Inject() (val messagesApi: MessagesApi, ws: WSClient) extends Controller with I18nSupport {

  val moneyboxRepository = MoneyboxRepository

  def enterDetails = Action { implicit request =>
    Ok(views.html.moneybox(LoginForm.form))
  }

  def submitDetails = Action.async { implicit request =>
    LoginForm.form.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(views.html.moneybox(formWithErrors))),
      data => {
        val monzoAccountId = request.session.data.get("accountId").getOrElse("")

        MoneyboxAuthHelpers.moneyboxLogin(data.email, data.password, monzoAccountId).map {
          response =>
            Redirect(controllers.routes.MoneyboxController.thanks).withSession(Session(request.session.data))
        }
      }
    )

  }

  def thanks = Action { implicit request =>
    Ok("Sit tight and wait for the round ups!")
  }

}

object MoneyboxAuthHelpers {

  val moneyboxRepository = MoneyboxRepository

  val ws = Play.current.injector.instanceOf[WSClient]

  def moneyboxLogin(email: String, password: String, monzoAccountId: String) = {
    ws.url("https://api.moneyboxapp.com/users/login")
      .withHeaders("AppId" -> "8cb2237d0679ca88db6464", "AppVersion" -> "1.0.13")
      .post(Json.obj("email" -> email, "password" -> password)).map {
      result =>
        val moneyboxAuth = MoneyboxAuth(
          (result.json \ "User" \ "UserId").as[String],
          (result.json \ "Session" \ "BearerToken").as[String],
          email,
          password,
          monzoAccountId = monzoAccountId
        ).encrypt

        moneyboxRepository.save(monzoAccountId, moneyboxAuth)

        result
    }
  }

}
