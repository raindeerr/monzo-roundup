package auth

import models.MoneyboxAuth
import play.api.Play
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import repositories.MoneyboxRepository

import scala.concurrent.ExecutionContext.Implicits.global

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
