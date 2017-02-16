package repositories

import controllers.EncryptedMoneyboxAuth
import play.api.Play
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoApi
import play.modules.reactivemongo.json.ImplicitBSONHandlers._
import reactivemongo.api.commands.WriteResult
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait MoneyboxRepository {

  def reactiveMongoApi: ReactiveMongoApi

  def collectionFuture: Future[JSONCollection]

  def save(monzoAccountId: String, moneyboxAuth: EncryptedMoneyboxAuth): Future[WriteResult] = {
    val query = Json.obj("monzoAccountId" -> monzoAccountId, "userId" -> moneyboxAuth.userId)
    val update = Json.obj(
      "$set" -> Json.obj("monzoAccountId" -> monzoAccountId, "userId" -> moneyboxAuth.userId, "bearerToken" -> moneyboxAuth.bearerToken, "emailAddress" -> moneyboxAuth.emailAddress, "password" -> moneyboxAuth.password),
      "$setOnInsert" -> Json.obj("roundUpBalance" -> BigDecimal(0))
    )

    for {
      collection <- collectionFuture
      findAndMod <- collection.update(query, update, upsert = true)
    } yield findAndMod
  }

  def findByAccountId(monzoAccountId: String): Future[Option[EncryptedMoneyboxAuth]] = {
    for {
      collection <- collectionFuture
      result <- collection.find(Json.obj("monzoAccountId" -> monzoAccountId)).one[EncryptedMoneyboxAuth]
    } yield result
  }

  def updateBalance(monzoAccountId: String, balanceFunction: BigDecimal => BigDecimal): Future[WriteResult] = {
    for {
      collection <- collectionFuture
      find <- findByAccountId(monzoAccountId)
      update <- {
        val roundUpBalance = find.map(a => a.roundUpBalance)
        roundUpBalance.map { roundUp =>
          val newBalance = balanceFunction(roundUp)//roundUp + amountToAdd
          println(s"upadting balance....")
          collection.update(Json.obj("monzoAccountId" -> monzoAccountId), Json.obj("$set" -> Json.obj("roundUpBalance" -> newBalance)))
        }
      }.get
    } yield update
  }

  def findAllForRoundup = {
    for {
      collection <- collectionFuture
      findAll <- collection.find(Json.obj()).cursor[EncryptedMoneyboxAuth]().collect[Seq]()
    } yield findAll
  }

}

object MoneyboxRepository extends MoneyboxRepository {
  lazy val reactiveMongoApi: ReactiveMongoApi = Play.current.injector.instanceOf[ReactiveMongoApi]
  lazy val collectionFuture: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection[JSONCollection]("moneybox"))

}