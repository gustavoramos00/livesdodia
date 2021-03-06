package service

import java.time.temporal.ChronoUnit
import java.time.{Duration, LocalDate, LocalDateTime}

import javax.inject.{Inject, Singleton}
import model.{Evento, EventosDia, YoutubeData}
import play.api.cache.AsyncCacheApi
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logger}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RepositoryService @Inject()(
                                   youtubeService: YoutubeService,
                                   ws: WSClient,
                                   liveScheduler: LiveScheduler,
                                   cache: AsyncCacheApi,
                                   configuration: Configuration
                                 )
                                 (implicit ec: ExecutionContext){

  val liveTtl = Duration.of(7, ChronoUnit.HOURS) // TODO Unificar
  val endpoint = configuration.get[String]("sheetsEndpoint")
  val spreadsheetId = configuration.get[String]("spreadsheetId")
  val sheetId = configuration.get[String]("sheetId")
  val apiKey = configuration.get[String]("apiKey")
  val ranges = configuration.get[String]("ranges")
  val urlSpreadSheet = s"$endpoint/v4/spreadsheets/${spreadsheetId}/values/$ranges"
  val dataAtualizacaoCacheKey = "atualizadoEm"
  val logger: Logger = Logger(this.getClass())

  private def dataFromSheets(): Future[List[Evento]] = {
    ws.url(urlSpreadSheet)
      .addQueryStringParameters("key" -> apiKey)
      .get()
      .map { response =>
        if (response.status != 200) {
          logger.error(s"Erro ao obter planilha ${response.body}")
        }
        val valuesList = (response.json \ "values").as[List[List[String]]]
        logger.warn(s"Dados obtidos da planilha [${valuesList.length}]")
        valuesList
          .tail // remove cabeçalho
          .flatMap {
          case List(carimboDtHrUuid: String, _, nome, info, dia, hora, tags: String, liveLink: String, instagramProfile: String, destaque: String, thumbnail: String, "S", _*) =>
            try {
              val data = Evento.parseData(dia, hora)
              val (optYoutube, optOutroLink) =
                if(liveLink.contains("youtube") || liveLink.contains("youtu.be"))
                  (Some(liveLink), None)
                else if (liveLink.nonEmpty)
                  (None, Some(liveLink))
                else
                  (None, None)
              val optInstagram = if (instagramProfile.isEmpty) None else Some(instagramProfile)
              val booleanDestaque = if (destaque.isEmpty) false else true
              val optYoutubeData = optYoutube.map(YoutubeData.fromYoutubeLink)
              val optThumbnail = if (thumbnail.isEmpty) None else Some(thumbnail)
              val tagList = tags.split(Array(',', '/')).toSeq.map(_.trim)
              val id = Some(carimboDtHrUuid)
              if (carimboDtHrUuid == null || carimboDtHrUuid.isEmpty) {
                throw new IllegalArgumentException(s"ID não informado para evento $nome")
              }
              val evento = Evento(
                id = id,
                nome = nome,
                info = info,
                data = data,
                tags = tagList,
                youtubeLink = optYoutube,
                outroLink = optOutroLink,
                instagramProfile = optInstagram,
                destaque = booleanDestaque,
                youtubeData = optYoutubeData,
                linkImagem = optThumbnail)
              Some(evento)
            } catch {
              case err: Throwable =>
                logger.error(s"### Erro ao converter dados $nome / $dia / $hora", err)
                None
            }
          case List(_, _, _, _, _, _, _, _, _, _, _, "N", _*) =>
            None
          case errList =>
            if (errList.nonEmpty) {
              logger.error(s"### Error ao obter dados: ${errList} ==> ${errList.mkString(", ")} ###")
            }
            None
        }.sortBy(ev => (!ev.destaque, ev.data))
      }
  }

  private def recuperaDadosYoutubeCache(novosEventos: List[Evento]): Future[List[Evento]] = {
    logger.warn(s"Recuperando dados youtube cache")
    cache.get[List[Evento]](Evento.cacheKey).map {
      case Some(eventosCache) =>
        novosEventos.map(novoEvento => {
          val maybeEventoExistente = eventosCache.find(ev => ev.id == novoEvento.id && ev.linkRegistrado == novoEvento.linkRegistrado)
          (maybeEventoExistente.flatMap(_.youtubeData), novoEvento.youtubeData) match {
            case (Some(ytExistente), Some(ytNovo)) =>
              val ytFinal = ytNovo.copy(
                channelId = ytNovo.channelId.orElse(ytExistente.channelId),
                userName = ytNovo.userName.orElse(ytExistente.userName),
                customUrl = ytNovo.customUrl.orElse(ytExistente.customUrl),
                videoId = ytNovo.videoId.orElse(ytExistente.videoId),
                embeddable = ytNovo.embeddable.orElse(ytExistente.embeddable),
                liveBroadcastContent =  ytNovo.liveBroadcastContent.orElse(ytExistente.liveBroadcastContent),
                thumbnail = ytNovo.thumbnail.orElse(ytExistente.thumbnail),
                videoImg = ytNovo.videoImg.orElse(ytExistente.videoImg)
              )
              novoEvento.copy(youtubeData = Some(ytFinal), encerrado = maybeEventoExistente.get.encerrado)
            case _ =>
              novoEvento
          }
        })
      case None => novosEventos
    }
  }

  def tagsColor() = {
    getEventos.map(eventos => {
      eventos
        .filter(_.data.isAfter(LocalDateTime.now.minus(liveTtl)))
        .flatMap(_.tags)
        .filter(_.nonEmpty)
        .distinct
        .zip(colorList)
        .appended("Destaque","#e606c6")
        .sortBy(t => (Evento.tagsCategoria.contains(t._1), t._1))(Ordering.Tuple2(Ordering.Boolean.reverse, Ordering.String))
    })
  }

  // obtido em https://medialab.github.io/iwanthue/
  def colorList = Seq("#794b1e","#6530be","#4dad39","#bb4ee2","#839f31","#5d5ad4","#3e812d",
    "#da44bb","#4ea264","#e74285","#3ea792","#e24720","#5692cc","#db8227",
    "#827edd","#b6902b","#933996","#486823","#d172bb","#2a714c","#da4248",
    "#474f95","#85761e","#75366f","#989a50","#a62a5f","#424b14","#a87db8",
    "#685f1d","#d7737d","#817b45","#90445b","#b88352","#903328","#c3663a")


  def forceUpdate(id: Option[String]) = {
    logger.warn(s"Forçando atualização de dados [${id.getOrElse("todos")}]")
    for {
      eventosSheet <- dataFromSheets()
      eventosDadosCache <- recuperaDadosYoutubeCache(eventosSheet)
      _ <- cache.set(Evento.cacheKey, eventosDadosCache)
      _ <- cache.set(dataAtualizacaoCacheKey, LocalDateTime.now)
      eventosAtualizados <- youtubeService.fetch(eventosDadosCache, id)
    } yield {
      liveScheduler.reSchedule()
      cache.set(Evento.cacheKey, eventosAtualizados)
      cache.set(dataAtualizacaoCacheKey, LocalDateTime.now)
    }
  }

  def getEventos: Future[List[Evento]] =
    try {
      cache.getOrElseUpdate[List[Evento]](Evento.cacheKey) (dataFromSheets())
    } catch {
      case ex: Throwable => logger.error(s"Erro ao obter do cache", ex)
        throw ex
    }


  private def filtroEventoJaComecou(evento: Evento): Boolean = evento.data.isBefore(LocalDateTime.now) && evento.data.isAfter(LocalDateTime.now.minusHours(8))

  def eventosAgora() = {
    val futureEventos = getEventos.map(eventos => {
      eventos
        .filter(filtroEventoJaComecou)
        .sortBy(ev =>
          (ev.destaque && !ev.encerrado.getOrElse(false), ev.data))(Ordering[(Boolean, LocalDateTime)].reverse)
    })
    // verifica eventos sem links
    futureEventos.map(eventos => {
      eventos
        .filter(_.linkLive.isEmpty)
        .foreach(ev => logger.error(s"Erro: acontecendo agora sem link: ${ev.nome}"))
    })
    futureEventos
  }

  private def filtroProximosEventosHoje(evento: Evento) = !filtroEventoJaComecou(evento) &&
    evento.data.toLocalDate.isEqual(LocalDate.now) &&
    evento.data.isAfter(LocalDateTime.now)

  def eventosAconteceraoHoje() = {

    val futureEventos = getEventos.map(_.filter(filtroProximosEventosHoje))
    futureEventos.map(eventos => {
      eventos
        .filter(_.linkLive.isEmpty)
        .foreach(ev => logger.warn(s"WARN: Live hoje ainda sem link: ${ev.nome}"))
    })
    futureEventos
  }

  def eventosProximosDias() = {

    getEventos.map(values => {
      values
        .filter(_.data.toLocalDate.isAfter(LocalDate.now))
        .groupBy(_.data.toLocalDate)
        .toList
        .sortBy(_._1)
        .map { case (dia: LocalDate, eventos: List[Evento]) => EventosDia(dia, eventos)}
    })
  }

  def atualizadoEm(): Future[String] = {
    cache
      .get[LocalDateTime](dataAtualizacaoCacheKey)
      .map(_.map(data => Evento.formatDiaHora(data)).getOrElse(""))
  }

//  def historico(): Future[List[EventosMes]] = {
//    getEventos.map(values => {
//      values
//        .filter(ev => ev.data.toLocalDate.isBefore(LocalDate.now) && !filtroEventoJaComecou(ev))
//    })
//    null
//  }

}
