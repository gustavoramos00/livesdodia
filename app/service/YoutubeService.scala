package service

import java.time.temporal.ChronoUnit
import java.time.{Duration, LocalDateTime}

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

  val liveTtl = Duration.of(7, ChronoUnit.HOURS) // TODO unificar
  val enabled = configuration.get[Boolean]("enableYoutubeFetch")
  val endpoint = configuration.get[String]("youtubeEndpoint")
  val apiKey = configuration.get[String]("apiKey")
  val logger: Logger = Logger(this.getClass())
  val channelUrl = s"$endpoint/channels"
  val searchUrl = s"$endpoint/search"
  val videosUrl = s"$endpoint/videos"
  val liveBroadcastUrl = s"$endpoint/liveBroadcasts"

  private def channelIdByUsername(evento: Evento): Future[Evento] = {
    if (enabled && evento.youtubeData.flatMap(_.userName).isDefined && !evento.youtubeData.flatMap(_.channelId).isDefined) {
      ws.url(channelUrl)
//        .withRequestFilter(AhcCurlRequestLogger())
        .addQueryStringParameters("key" -> apiKey, "part" -> "snippet", "forUsername" -> evento.youtubeData.get.userName.get)
        .get()
        .map { response =>
          if (response.status != 200) logger.error(s"Erro ao fazer requisição ${response.json}")
          val item = (response.json \ "items" \ 0)
          val id = (item \ "id").asOpt[String]
          val channelImg = (item \ "snippet" \ "thumbnails" \ "default" \ "url").asOpt[String]
//          logger.warn(s"fetch byUserName channelId ${evento.nome} [${id.getOrElse("")}]")
          val newYoutubeData = evento.youtubeData.map(_.copy(channelId = id, thumbnail = channelImg))
          evento.copy(youtubeData = newYoutubeData)
        }
    } else {
      Future.successful(evento)
    }
  }

  private def channelThumbnail(evento: Evento): Future[Evento] = {
    if (enabled && evento.thumbnailUrl.isEmpty && evento.youtubeData.flatMap(_.channelId).isDefined) {
      ws.url(channelUrl)
        .addQueryStringParameters("key" -> apiKey, "part" -> "snippet", "id" -> evento.youtubeData.get.channelId.get)
        .get()
        .map { response =>
          if (response.status != 200) logger.error(s"Erro ao fazer requisição ${response.json}")
          val item = (response.json \ "items" \ 0)
          val channelImg = (item \ "snippet" \ "thumbnails" \ "default" \ "url").asOpt[String]
//          logger.warn(s"fetch channel thumbnail ${evento.nome} [$channelImg]")
          val newYoutubeData = evento.youtubeData.map(_.copy(thumbnail = channelImg))
          evento.copy(youtubeData = newYoutubeData)
        }
    } else {
      Future.successful(evento)
    }
  }

  def searchLiveVideoId(evento: Evento): Future[Evento] = {
    if (enabled && evento.youtubeData.flatMap(_.channelId).isDefined &&
      evento.youtubeData.flatMap(_.videoId).isEmpty &&
      evento.data.isBefore(LocalDateTime.now) &&
      evento.data.isAfter(LocalDateTime.now.minusHours(3))) {
      ws.url(searchUrl) // TODO usar com cautela, alto uso de quota API Youtube
//        .withRequestFilter(AhcCurlRequestLogger())
        .addQueryStringParameters(
          "key" -> apiKey,
          "part" -> "snippet",
          "eventType" -> "live",
          "channelId" -> evento.youtubeData.get.channelId.get,
          "type" -> "video")
        .get()
        .map { response =>
          if (response.status != 200) logger.error(s"Erro ao fazer requisição ${response.json}")
          val id = (response.json \ "items" \ 0 \ "id" \ "videoId").asOpt[String]
          logger.warn(s"fetch videoId [${id.getOrElse("")}] evento [${evento.nome}]")
          val newYoutubeData = evento.youtubeData.map(_.copy(videoId = id))
          evento.copy(youtubeData = newYoutubeData)
        }
    } else {
      Future.successful(evento)
    }
  }

  def fetchChannelByUpcomingVideoId(evento: Evento) = {
    if (enabled && evento.youtubeData.flatMap(_.videoId).isDefined &&
      evento.youtubeData.flatMap(_.channelId).isEmpty &&
      evento.data.isAfter(LocalDateTime.now)) {

      ws.url(videosUrl)
        //        .withRequestFilter(AhcCurlRequestLogger())
        .addQueryStringParameters(
        "key" -> apiKey,
        "part" -> " snippet",
        "id" -> evento.youtubeData.get.videoId.get)
        .get()
        .map { response =>
          if (response.status != 200) logger.error(s"Erro ao fazer requisição ${response.json}")
          val item = response.json \ "items" \ 0
          val channelId = (item \ "snippet" \ "channelId").asOpt[String]
          val thumbnail = evento.thumbnailUrl.orElse((item \ "snippet" \ "thumbnails" \ "default").asOpt[String])
          val newYoutubeData = evento.youtubeData.map(_.copy(
            thumbnail = thumbnail,
            channelId = channelId
          ))
          evento.copy(youtubeData = newYoutubeData)
        }
    } else {
      Future.successful(evento)
    }
  }


  def fetchLiveVideoDetails(evento: Evento) = {
    if (enabled && evento.youtubeData.flatMap(_.videoId).isDefined &&
      !evento.encerrado.getOrElse(false) &&
      evento.data.isBefore(LocalDateTime.now) &&
      evento.data.isAfter(LocalDateTime.now.minus(liveTtl))) {

      ws.url(videosUrl)
        //        .withRequestFilter(AhcCurlRequestLogger())
        .addQueryStringParameters(
        "key" -> apiKey,
        "part" -> " snippet,status",
        "id" -> evento.youtubeData.get.videoId.get)
        .get()
        .map { response =>
          if (response.status != 200) logger.error(s"Erro ao fazer requisição ${response.json}")
          val item = response.json \ "items" \ 0
          val liveBroadcastContent = (item \ "snippet" \ "liveBroadcastContent").asOpt[String]
          val channelId = (item \ "snippet" \ "channelId").asOpt[String]
          val embeddable = (item \ "status" \ "embeddable").asOpt[Boolean]
          val thumbnail = evento.thumbnailUrl.orElse((item \ "snippet" \ "thumbnails" \ "default").asOpt[String])
          if (!liveBroadcastContent.contains(YoutubeData.broadcastLive)) {
            logger.warn(s"fetch videoDetails ${evento.nome} " +
              s"live [${liveBroadcastContent.getOrElse("")}] " +
              s"embeddable [${embeddable.getOrElse("")}]")
          }
          val newYoutubeData = evento.youtubeData.map(_.copy(
            liveBroadcastContent = liveBroadcastContent,
            embeddable = embeddable,
            channelId = channelId.orElse(evento.youtubeData.flatMap(_.channelId)),
            thumbnail = thumbnail.orElse(evento.youtubeData.flatMap(_.thumbnail))
          ))
          if (liveBroadcastContent.isEmpty || liveBroadcastContent.contains(YoutubeData.broadcastEncerrado)) {
            if (evento.data.isAfter(LocalDateTime.now.minusHours(1))) { // recente -> erro transmissão
              val ytEmptyVideoId = evento.youtubeData.map(_.copy(videoId = None, liveBroadcastContent = None))
              evento.copy(youtubeData = ytEmptyVideoId)
            } else { // encerrado
              evento.copy(youtubeData = newYoutubeData, encerrado = Some(true))
            }
          } else {
            evento.copy(youtubeData = newYoutubeData)
          }
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

  def fetchEvento(evento: Evento): Future[Evento] = {

    for {
      eventoUrlUnshorten <- urlUnshorten(evento)
      eventoChannelId <- fetchChannelByUpcomingVideoId(eventoUrlUnshorten)
      eventoChannel <- channelIdByUsername(eventoChannelId)
      eventoVideoDetails <- fetchLiveVideoDetails(eventoChannel)
      eventoChannelThumnail <- channelThumbnail(eventoVideoDetails)
      eventoVideo <- searchLiveVideoId(eventoChannelThumnail)
    } yield {
      eventoVideo
    }
  }

  def fetch(eventos: Seq[Evento], id: Option[String]): Future[Seq[Evento]] = {
    logger.warn(s"Fetch dados youtube [${id.getOrElse("todos")}]")
    val eventosFiltrado = eventos.filter(_.data.isAfter(LocalDateTime.now.minus(liveTtl)))
    Future.sequence(eventosFiltrado.map(evento => {
      if (id.isEmpty || id == evento.id) {
        fetchEvento(evento)
      } else {
        Future.successful(evento)
      }
    }))
  }

}
