package service

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Cancellable}
import javax.inject.Inject
import model.Evento
import play.api.cache.AsyncCacheApi
import play.api.{Configuration, Logger}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Random

class LiveScheduler @Inject() (actorSystem: ActorSystem,
                               cache: AsyncCacheApi,
                               youtubeService: YoutubeService,
                               configuration: Configuration)(
  implicit executionContext: ExecutionContext){

  var jobs = Seq.empty[Cancellable]
  val logger = Logger(getClass)

  def scheduleCount = jobs.length

  def reSchedule(eventos: Seq[Evento]) = {
    if (isMainServer) {
      // cancela todos os jobs
      jobs.foreach(job => job.cancel())
      // agenda novamente
      jobs = eventos
          .filter(_.data.isAfter(LocalDateTime.now.minusHours(8)))
          .filter(_.data.isBefore(LocalDateTime.now.plusDays(1)))
          .groupBy(_.data).toSeq.flatMap {
        case (data, eventos) =>
          val randSeconds = Random.between(0,240) // para evitar fetches no mesmo instante
          Seq(
            scheduleEventosDetails(data, eventos), // fetch eventos na hora agendada
            scheduleEventosDetails(data.plusMinutes(5), eventos), // fetch 5 minutos depois
            scheduleEventosDetails(data.plusMinutes(20), eventos), // fetch 20 minutos depois
            scheduleEventosDetails(data.plusHours(2).plusSeconds(randSeconds), eventos), // fetch ~2h depois
            scheduleEventosDetails(data.plusHours(4).plusSeconds(randSeconds), eventos), // fetch ~4h depois
            scheduleEventosDetails(data.plusHours(6).plusSeconds(randSeconds), eventos) // fetch ~6h depois
          ).flatten
      }
    } else {
      logger.warn(s"NOT main server")
    }
  }

  private def scheduleEventosDetails(data: LocalDateTime, eventos: Seq[Evento]) = {
    val seconds = LocalDateTime.now.until(data, ChronoUnit.SECONDS)
    if (seconds > 0) {
      val duration = Duration(seconds, TimeUnit.SECONDS)

      // não funcionou com scheduleOnce.
      // Usando fixedDelay com valor alto suficiente para não executar novamente
      val cancellable = actorSystem.scheduler.scheduleWithFixedDelay(duration, 10.hours) { () =>
        logger.warn(s"running schedule for [${eventos.map(_.nome).mkString(", ")}]")
        Future.sequence(eventos.map(evento =>
          for {
            eventoVideo <- youtubeService.fetchLiveVideoId(evento)
            eventoVideoDetails <- youtubeService.fetchVideoDetails(eventoVideo)
          } yield eventoVideoDetails
        )).map(eventosAtualizados => {
          updateEventosCache(eventosAtualizados)
        })
      }
      Some(cancellable)
    } else {
      None
    }
  }


  def updateEventosCache(eventos: Seq[Evento]) = {
    for {
      Some(eventosCache) <- cache.get[List[Evento]](Evento.cacheKey)
    } yield {
      val (_, eventosNoMatch) = eventosCache.partition(ev => eventos.exists(_.id == ev.id))
      val eventosFinal = eventos ++ eventosNoMatch
      logger.warn(s"Atualiza cache apos fetch eventos length= [${eventosFinal.length}] ")
      cache.set(Evento.cacheKey, eventosFinal)
    }
  }

  private def isMainServer: Boolean =
    configuration.getOptional[String]("http.port")
      .map(_.endsWith("00")) // termina com 00 (atualmente 8000)
      .getOrElse {
      logger.error("Não foi possível obter porta do serviço em execução (http.port)")
      true
    }

}
