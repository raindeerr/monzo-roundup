package controllers

import java.util.UUID
import javax.inject._

import auth.MonzoAuth
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




