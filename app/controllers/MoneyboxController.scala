package controllers

import javax.inject.Inject

import auth.MoneyboxAuthHelpers
import crypto.CryptoHelpers
import models.MoneyboxAuth
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
            Redirect(controllers.routes.MoneyboxController.thanks).withSession(Session(request.session.data))
        }
      }
    )

  }

  def thanks = Action { implicit request =>
    Ok(views.html.thanks())
  }

}


