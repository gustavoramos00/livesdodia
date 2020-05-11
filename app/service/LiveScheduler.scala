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
                               myPushService: MyPushService,
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
          val randSeconds = Random.between(15,240) // para evitar fetches no mesmo instante
          Seq(
            scheduleEventosDetails(data.plusSeconds(5), eventos, true), // fetch ~ na hora agendada
            scheduleEventosDetails(data.plusMinutes(5), eventos), // fetch após
            scheduleEventosDetails(data.plusMinutes(25), eventos), // fetch última tentativa
            scheduleEventosDetails(data.plusHours(3).plusSeconds(randSeconds), eventos), // fetch ~3h depois
            scheduleEventosDetails(data.plusHours(5).plusSeconds(randSeconds), eventos) // fetch ~5h depois
          ).flatten
      }
    } else {
      logger.warn(s"NOT main server")
    }
  }

  private def scheduleEventosDetails(data: LocalDateTime, eventos: Seq[Evento], notifyObservers: Boolean = false) = {

    val seconds = LocalDateTime.now.until(data, ChronoUnit.SECONDS)
    if (seconds > 0) {
      val duration = Duration(seconds, TimeUnit.SECONDS)

      // não funcionou com scheduleOnce.
      // Usando fixedDelay com valor alto suficiente para não executar novamente
      val cancellable = actorSystem.scheduler.scheduleWithFixedDelay(duration, 10.hours) { () =>
        logger.warn(s"running schedule for [${eventos.map(_.nome).mkString(", ")}]")
        for {
          eventosDoCache <- eventosCache
          eventosAtualizados <- Future.successful(eventos.map(evAntes => eventosDoCache.find(_.id == evAntes.id).getOrElse(evAntes)))
          eventosVideo <- Future.sequence(eventosAtualizados.map(youtubeService.fetchLiveVideoId))
          eventosVideoDetails <- Future.sequence(eventosVideo.map(youtubeService.fetchVideoDetails))
        } yield {
          if (notifyObservers) {
            eventosVideoDetails.foreach(evento => myPushService.notify(evento.id.get, evento))
          }
          updateEventosCache(eventosVideoDetails)
          eventosVideoDetails
        }
      }
      Some(cancellable)
    } else {
      None
    }
  }

  def eventosCache = cache.get[List[Evento]](Evento.cacheKey).map(_.getOrElse(Seq.empty))

  def updateEventosCache(eventos: Seq[Evento]) = {

    def cacheReplace(eventosCache: Seq[Evento]) = {
      val (_, eventosNoMatch) = eventosCache.partition(ev => eventos.exists(_.id == ev.id))
      val eventosFinal = eventos ++ eventosNoMatch
      logger.warn(s"Atualiza cache apos fetch eventos length= [${eventosFinal.length}] ")
      eventosFinal
    }

    for {
      eventosCache <- eventosCache
      done <- cache.set(Evento.cacheKey, cacheReplace(eventosCache))
    } yield {
      done
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
