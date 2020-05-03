package service

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Cancellable}
import javax.inject.Inject
import model.Evento
import play.api.{Configuration, Logger}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class LiveScheduler @Inject() (actorSystem: ActorSystem,
                               repositoryService: RepositoryService,
                               youtubeService: YoutubeService,
                               configuration: Configuration)(
  implicit executionContext: ExecutionContext){

  var jobs = Seq.empty[Cancellable]
  val logger = Logger(getClass)

  if (isMainServer) {
    logger.warn(s"is main server")
    actorSystem.scheduler.scheduleAtFixedRate(initialDelay = 1.hour, interval = 2.hour) { () =>
      // the block of code that will be executed
      actorSystem.log.info("Schedule forcing update...")
      repositoryService.forceUpdate()
    }
  } else {
    logger.warn(s"NOT main server")
  }

  def scheduleEvento(evento: Evento) = {
    val seconds = evento.data.until(LocalDateTime.now, ChronoUnit.SECONDS)
    val duration = Duration(seconds, TimeUnit.SECONDS)
    val cancellable = actorSystem.scheduler.scheduleOnce(duration) { () =>
      youtubeService.fetchEvento(evento)
    }
    jobs = jobs :+ cancellable
  }

  def isMainServer: Boolean =
    configuration.getOptional[String]("http.port")
      .map(_.endsWith("00")) // termina com 00 (atualmente 8000)
      .getOrElse {
      logger.error("Não foi possível obter porta do serviço em execução (http.port)")
      true
    }

}
