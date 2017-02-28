package scheduler

import javax.inject._

import akka.actor._
import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class SchedulerModule extends AbstractModule with AkkaGuiceSupport {
  def configure() = {
    bindActor[SchedulerActor]("scheduler")
    bind(classOf[SchedulerStart]) asEagerSingleton
  }
}

@Singleton
class SchedulerStart @Inject()(system: ActorSystem, @Named("scheduler") longRunningActor: ActorRef) {
  system.scheduler.schedule(10 seconds, 120 seconds, longRunningActor, CheckForRoundUps)
}