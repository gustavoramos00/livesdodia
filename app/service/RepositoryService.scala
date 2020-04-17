package service

import java.time.{LocalDate, LocalDateTime}

import javax.inject.{Inject, Singleton}
import model.{Evento, EventosDia}
import play.api.{Configuration, Logger}
import play.api.cache.AsyncCacheApi
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RepositoryService @Inject()(
                                   ws: WSClient,
                                   cache: AsyncCacheApi,
                                   configuration: Configuration
                                 )
                                 (implicit ec: ExecutionContext){

  val endpoint = configuration.get[String]("endpoint")
  val spreadsheetId = configuration.get[String]("spreadsheetId")
  val sheetId = configuration.get[String]("sheetId")
  val apiKey = configuration.get[String]("apiKey")
  val ranges = configuration.get[String]("ranges")
  val urlSpreadSheet = s"$endpoint/v4/spreadsheets/${spreadsheetId}/values/$ranges"
  val cacheKey = "eventos"
  val dataAtualizacaoCacheKey = "atualizadoEm"
  val logger: Logger = Logger(this.getClass())

  def update(): Future[List[Evento]] = {
    ws.url(urlSpreadSheet)
      .addQueryStringParameters("key" -> apiKey)
      .get()
      .map { response =>
        val valuesList = (response.json \ "values").as[List[List[String]]]
        val eventos = valuesList
          .tail // remove cabeçalho
          .flatMap {
            case List(_, _, nome, info, dia, hora, horaFim, youtube: String, instagram: String, destaque: String, "S", _*) =>
              try {
                val data = Evento.parseData(dia, hora)
                val dataFim = Evento.parseData(dia, horaFim)
                val dataFimAjustado = if (dataFim.isAfter(data)) dataFim else dataFim.plusDays(1) // Termina no dia seguinte
                val optYoutube = if (youtube.isEmpty) None else Some(youtube)
                val optInstagram = if (instagram.isEmpty) None else Some(instagram)
                val booleanDestaque = if (destaque.isEmpty) false else true
                val evento = Evento(nome, info, data, dataFimAjustado, optYoutube, optInstagram, booleanDestaque)
                Some(evento)
              } catch {
                case err: Throwable =>
                  logger.error(s"### Erro ao converter dados $nome / $dia / $hora", err)
                  None
              }
            case errList =>
              logger.error(s"### Error ao obter dados: ${errList.mkString(", ")} ###")
              None
          }
          .sortBy(ev => (!ev.destaque, ev.data))
        cache.set(cacheKey, eventos)
        cache.set(dataAtualizacaoCacheKey, LocalDateTime.now)
        eventos
      }
  }

  private def getEventos = {
    cache
      .get[List[Evento]](cacheKey)
      .map {
        case Some(values) => Future.successful(values)
        case None => update()
      }
      .flatten
  }

  private def filtroEventosAgora(evento: Evento) = evento.data.isBefore(LocalDateTime.now) && evento.dataFim.isAfter(LocalDateTime.now)

  def eventosAgora() = {
    val futureEventos = getEventos.map(_.filter(filtroEventosAgora))
    futureEventos.map(eventos => {
      eventos
        .filter(ev => ev.linkInstagram.isEmpty && ev.linkYoutube.isEmpty)
        .foreach(ev => logger.error(s"Erro: acontecendo agora sem link: ${ev.nome}"))
    })
    futureEventos
  }

  def eventosAconteceraoHoje() = {
    def filtroEventosHoje(evento: Evento) = !filtroEventosAgora(evento) &&
      evento.data.toLocalDate.isEqual(LocalDate.now) &&
      evento.data.isAfter(LocalDateTime.now)

    val futureEventos = getEventos.map(_.filter(filtroEventosHoje))
    futureEventos.map(eventos => {
      eventos
        .filter(ev => ev.linkInstagram.isEmpty && ev.linkYoutube.isEmpty)
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

}
