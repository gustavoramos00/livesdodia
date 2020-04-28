package service

import java.time.{LocalDate, LocalDateTime}

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
                                   cache: AsyncCacheApi,
                                   configuration: Configuration
                                 )
                                 (implicit ec: ExecutionContext){

  val endpoint = configuration.get[String]("sheetsEndpoint")
  val spreadsheetId = configuration.get[String]("spreadsheetId")
  val sheetId = configuration.get[String]("sheetId")
  val apiKey = configuration.get[String]("apiKey")
  val ranges = configuration.get[String]("ranges")
  val urlSpreadSheet = s"$endpoint/v4/spreadsheets/${spreadsheetId}/values/$ranges"
  val cacheKey = "eventos"
  val dataAtualizacaoCacheKey = "atualizadoEm"
  val logger: Logger = Logger(this.getClass())

  private def dataFromSheets(): Future[List[Evento]] = {
    ws.url(urlSpreadSheet)
      .addQueryStringParameters("key" -> apiKey)
      .get()
      .map { response =>
        val valuesList = (response.json \ "values").as[List[List[String]]]
        valuesList
          .tail // remove cabeçalho
          .flatMap {
          case List(_, _, nome, info, dia, hora, tags: String, youtubeLink: String, instagramProfile: String, destaque: String, "S", _*) =>
            try {
              val data = Evento.parseData(dia, hora)
              val optYoutube = if (youtubeLink.isEmpty) None else Some(youtubeLink)
              val optInstagram = if (instagramProfile.isEmpty) None else Some(instagramProfile)
              val booleanDestaque = if (destaque.isEmpty) false else true
              val optYoutubeData = optYoutube.map(YoutubeData.fromYoutubeLink)
              val tagList = tags.split(",").toSeq
              val evento = Evento(
                nome = nome,
                info = info,
                data = data,
                tags = tagList,
                youtubeLink = optYoutube,
                instagramProfile = optInstagram,
                destaque = booleanDestaque,
                youtubeData = optYoutubeData)
              Some(evento)
            } catch {
              case err: Throwable =>
                logger.error(s"### Erro ao converter dados $nome / $dia / $hora", err)
                None
            }
          case errList =>
            if (errList.nonEmpty) {
              logger.error(s"### Error ao obter dados: ${errList} ==> ${errList.mkString(", ")} ###")
            }
            None
        }.sortBy(ev => (!ev.destaque, ev.data))
      }
  }

  private def recuperaDadosYoutubeCache(novosEventos: List[Evento]): Future[List[Evento]] = {
    cache.get[List[Evento]](cacheKey).map {
      case Some(eventosCache) =>
        novosEventos.map(novoEvento => {
          val maybeEventoExistente = eventosCache.find(ev => ev.nome == novoEvento.nome &&
            ev.data == novoEvento.data &&
            ev.linkRegistrado == novoEvento.linkRegistrado)
          if (maybeEventoExistente.isDefined) {
            novoEvento.copy(youtubeData = maybeEventoExistente.get.youtubeData, encerrado = maybeEventoExistente.get.encerrado)
          }
          else {
            novoEvento
          }
        })
      case None => novosEventos
    }
  }

  def tags() = {
    getEventos.map(eventos => {
      eventos
        .filter(_.data.toLocalDate.isAfter(LocalDate.now.minusDays(1)))
        .flatMap(_.tags)
        .filter(_.nonEmpty)
        .distinct
        .sortBy(identity)
    })
  }

  def forceUpdate() = {
    logger.warn(s"Forçando atualização de dados")
    for {
      eventosSheet <- dataFromSheets()
      eventosDadosCache <- recuperaDadosYoutubeCache(eventosSheet)
      _ <- cache.set(cacheKey, eventosDadosCache)
      _ <- cache.set(dataAtualizacaoCacheKey, LocalDateTime.now)
      (eventosComecou, outrosEventos) <- Future.successful(eventosDadosCache.partition(ev => filtroEventoJaComecou(ev)))
      eventosComecouAtualizados <- youtubeService.fetch(eventosComecou)
    } yield {
      val todosEventos = eventosComecouAtualizados ++ outrosEventos
      cache.set(cacheKey, todosEventos)
      cache.set(dataAtualizacaoCacheKey, LocalDateTime.now)
    }
  }

  def getEventos: Future[List[Evento]] =
    cache.getOrElseUpdate[List[Evento]](cacheKey) (dataFromSheets())


  private def filtroEventoJaComecou(evento: Evento): Boolean = evento.data.isBefore(LocalDateTime.now) && evento.data.isAfter(LocalDateTime.now.minusHours(12))

  def eventosAgora() = {
    val futureEventos = getEventos.map(eventos => {
      eventos
        .filter(filtroEventoJaComecou)
        .sortBy(_.data)(Ordering[LocalDateTime].reverse) // mais recentes primeiro
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
