package service

import akka.actor.{ActorRef, ActorSystem, Props}
import javax.inject.{Inject, Named}
import play.api.{Configuration, Logger}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class LiveScheduler @Inject() (actorSystem: ActorSystem,
                               repositoryService: RepositoryService,
                               configuration: Configuration)(
  implicit executionContext: ExecutionContext){

  val logger = Logger(getClass)

  if (isMainServer) {
    println(s"is main server")
    actorSystem.scheduler.scheduleAtFixedRate(initialDelay = 10.seconds, interval = 1.hour) { () =>
      // the block of code that will be executed
      actorSystem.log.info("Schedule forcing update...")
      repositoryService.forceUpdate()
    }
  } else {
    println(s"NOT main server")
  }

  def isMainServer: Boolean =
    configuration.getOptional[String]("http.port")
      .map(_.endsWith("00")) // termina com 00 (atualmente 8000)
      .getOrElse {
      logger.error("Não foi possível obter porta do serviço em execução (http.port)")
      true
    }

}
