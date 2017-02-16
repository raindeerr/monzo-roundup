package repositories

import crypto.CryptoHelpers
import models.EncryptedAuthTokenResponse
import org.joda.time.DateTime
import play.api.Play
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoApi
import play.modules.reactivemongo.json.ImplicitBSONHandlers._
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.bson.BSONDateTime
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

trait MonzoRepository {

  def reactiveMongoApi: ReactiveMongoApi

  def collectionFuture: Future[JSONCollection]

  def save(monzoAuth: EncryptedAuthTokenResponse): Future[UpdateWriteResult] = {
    val query = Json.obj("userId" -> monzoAuth.userId)
    val update =
      Json.obj("$setOnInsert" -> Json.obj("clientId" -> monzoAuth.clientId, "userId" -> monzoAuth.userId, "accountId" -> monzoAuth.accountId),
        "$set" -> Json.obj("accessToken" -> monzoAuth.accessToken, "expiresIn" -> monzoAuth.expiresIn, "refreshToken" -> monzoAuth.refreshToken, "tokenType" -> monzoAuth.tokenType, "updated" -> BSONDateTime(new DateTime().getMillis))
    )

    for {
      collection <- collectionFuture
      upsert <- collection.update(query, update, upsert = true)
    } yield upsert
  }

  def findByAccountId(accountId: String)(implicit ec: ExecutionContext) = {
    for {
      collection <- collectionFuture
      result <- collection.find(Json.obj("accountId" -> CryptoHelpers.encrypt(accountId))).one[EncryptedAuthTokenResponse]
    } yield result
  }

}

object MonzoRepository extends MonzoRepository {
  lazy val reactiveMongoApi: ReactiveMongoApi = Play.current.injector.instanceOf[ReactiveMongoApi]
  lazy val collectionFuture: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection[JSONCollection]("monzo"))
}