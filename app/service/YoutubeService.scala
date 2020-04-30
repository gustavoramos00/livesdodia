package service

import java.time.LocalDateTime

import javax.inject.{Inject, Singleton}
import model.{Evento, YoutubeData}
import play.api.cache.AsyncCacheApi
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logger}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class YoutubeService @Inject()(
                                   ws: WSClient,
                                   cache: AsyncCacheApi,
                                   configuration: Configuration
                                 )
                              (implicit ec: ExecutionContext){

  val enabled = configuration.get[Boolean]("enableYoutubeFetch")
  val endpoint = configuration.get[String]("youtubeEndpoint")
  val apiKey = configuration.get[String]("apiKey")
  val logger: Logger = Logger(this.getClass())
  val channelUrl = s"$endpoint/channels"
  val searchUrl = s"$endpoint/search"
  val videosUrl = s"$endpoint/videos"
  val liveBroadcastUrl = s"$endpoint/liveBroadcasts"

  private def channelId(evento: Evento): Future[Evento] = {
    if (enabled && evento.youtubeData.flatMap(_.userName).isDefined && !evento.youtubeData.flatMap(_.channelId).isDefined) {
      logger.warn(s"fetch channelId ${evento.nome}")
      ws.url(channelUrl)
//        .withRequestFilter(AhcCurlRequestLogger())
        .addQueryStringParameters("key" -> apiKey, "part" -> "id", "forUsername" -> evento.youtubeData.get.userName.get)
        .get()
        .map { response =>
          if (response.status != 200) logger.error(s"Erro ao fazer requisição ${response.json}")
          val id = (response.json \ "items" \ 0 \ "id").asOpt[String]
          val newYoutubeData = evento.youtubeData.map(_.copy(channelId = id))
          evento.copy(youtubeData = newYoutubeData)
        }
    } else {
      Future.successful(evento)
    }
  }

  private def liveVideoId(evento: Evento): Future[Evento] = {
    if (enabled && evento.youtubeData.flatMap(_.channelId).isDefined &&
      !evento.youtubeData.flatMap(_.videoId).isDefined &&
      evento.data.isAfter(LocalDateTime.now.minusHours(3))) {
      logger.warn(s"fetch videoId ${evento.nome}")
      ws.url(searchUrl) // TODO usar com cautela, alto uso de quota API Youtube
//        .withRequestFilter(AhcCurlRequestLogger())
        .addQueryStringParameters(
          "key" -> apiKey,
          "part" -> "id",
          "eventType" -> "live",
          "channelId" -> evento.youtubeData.get.channelId.get,
          "type" -> "video",
          "prettyPrint" -> "true")
        .get()
        .map { response =>
          if (response.status != 200) logger.error(s"Erro ao fazer requisição ${response.json}")
          val id = (response.json \ "items" \ 0 \ "id" \ "videoId").asOpt[String]
          val newYoutubeData = evento.youtubeData.map(_.copy(videoId = id))
          evento.copy(youtubeData = newYoutubeData)
        }
    } else {
      Future.successful(evento)
    }
  }

  private def videoDetails(evento: Evento) = {
    if (enabled && evento.youtubeData.flatMap(_.videoId).isDefined && !evento.encerrado.getOrElse(false)) {
      logger.warn(s"fetch videoDetails ${evento.nome}")
      ws.url(videosUrl)
        //        .withRequestFilter(AhcCurlRequestLogger())
        .addQueryStringParameters(
        "key" -> apiKey,
        "part" -> " snippet",
        "id" -> evento.youtubeData.get.videoId.get)
        .get()
        .map { response =>
          if (response.status != 200) logger.error(s"Erro ao fazer requisição ${response.json}")
          val liveBroadcastContent = (response.json \ "items" \ 0 \ "snippet" \ "liveBroadcastContent").asOpt[String]
          val newYoutubeData = evento.youtubeData.map(_.copy(
            liveBroadcastContent = liveBroadcastContent
          ))
          val encerrado = liveBroadcastContent.map(eventStatus => eventStatus == YoutubeData.broadcastEncerrado)
          evento.copy(youtubeData = newYoutubeData, encerrado = encerrado)
        }
    } else {
      Future.successful(evento)
    }
  }

  private def urlUnshorten(evento: Evento) = {
    if (enabled && evento.linkLive.map(_.contains("youtu.be")).getOrElse(false)) {
      ws.url(evento.linkLive.get)
        //        .withRequestFilter(AhcCurlRequestLogger())
        .withFollowRedirects(false)
        .get()
        .map { response =>
          response.header("location").map(link => {
            val youtubeData = YoutubeData.fromYoutubeLink(link)
            evento.copy(youtubeData = Some(youtubeData))
          }).getOrElse {
            logger.error(s"Erro ao seguir link ${evento.linkLive.get}")
            logger.error(s"response body [${response.body}]")
            evento
          }
        }
    } else {
      Future.successful(evento)
    }
  }

  def fetch(eventos: List[Evento]): Future[List[Evento]] = {
    val listFuture = eventos.map(evento => {
      for {
        eventoUrlUnshorten <- urlUnshorten(evento)
        eventoChannel <- channelId(eventoUrlUnshorten)
        eventoVideo <- liveVideoId(eventoChannel)
        eventoVideoDetails <- videoDetails(eventoVideo)
      } yield {
        eventoVideoDetails
      }
    })
    Future.sequence(listFuture)
  }



}
