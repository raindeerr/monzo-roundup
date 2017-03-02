package controllers

import javax.inject.Inject

import auth.MoneyboxAuthHelpers
import play.api.Logger
import play.api.data._
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.ws.{WSClient, WSResponse}
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

class MoneyboxController @Inject() (val messagesApi: MessagesApi, ws: WSClient) extends Controller with I18nSupport {

  val moneyboxRepository = MoneyboxRepository

  def enterDetails = Action { implicit request =>
    Ok(views.html.moneybox(LoginForm.form))
  }

  def submitDetails = Action.async { implicit request =>
    LoginForm.form.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(views.html.moneybox(formWithErrors))),
      data => {
        val monzoAccountId = request.session.data.getOrElse("accountId", "")

        MoneyboxAuthHelpers.moneyboxLogin(data.email, data.password, monzoAccountId).map {
          response =>
            val bearerToken = (response.json \ "Session" \ "BearerToken").as[String]
            getUserPreferences(bearerToken, monzoAccountId)
            Redirect(controllers.routes.MoneyboxController.thanks).withSession(Session(request.session.data))
        }
      }
    )

  }

  def thanks = Action { implicit request =>
    Ok(views.html.thanks())
  }

  def getUserPreferences(bearerToken: String, monzoAccountId: String): Future[WSResponse] = {
    Logger.info(s"[MoneyboxController][getUserPreferences] - getting prefs for $monzoAccountId")
    ws.url("https://api.moneyboxapp.com/users/personal").withHeaders("AppId" -> "8cb2237d0679ca88db6464", "AppVersion" -> "1.0.13", "Authorization" -> s"Bearer $bearerToken").get.map {
      response =>
        val wholePoundRoundUps = (response.json \ "UserDetail" \ "RoundUpWholePounds").as[Boolean]
        MoneyboxRepository.updatePreferences(monzoAccountId, wholePoundRoundUps)
        response
    }
  }

}


