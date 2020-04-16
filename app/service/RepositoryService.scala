package service

import java.time.{LocalDate, LocalDateTime}

import javax.inject.{Inject, Singleton}
import model.{Evento, EventosDia}
import play.api.Configuration
import play.api.cache.AsyncCacheApi
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcCurlRequestLogger

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RepositoryService @Inject()(
                                   ws: WSClient,
                                   cache: AsyncCacheApi,
                                   configuration: Configuration
                                 )
                                 (implicit ec: ExecutionContext){

  val endpoint = "https://sheets.googleapis.com"
  val spreadsheetId = configuration.get[String]("spreadsheetId")
  val sheetId = "0"
  val apiKey = "AIzaSyCmcAoK5FG5ZpgeQAuGugdEzMwhfQ-vMdA"
  val ranges = "Eventos"
  val urlSpreadSheet = s"$endpoint/v4/spreadsheets/${spreadsheetId}/values/$ranges"
  val cacheKey = "eventos"
  val dataAtualizacaoCacheKey = "atualizadoEm"

  def update(): Future[List[Evento]] = {
    ws.url(urlSpreadSheet)
      .addQueryStringParameters("key" -> apiKey)
      .get()
      .map { response =>
        val valuesList = (response.json \ "values").as[List[List[String]]]
        val eventos = valuesList
          .tail // remove cabeçalho
          .map {
            case List(nome, info, dia, hora, horaFim, youtube, instagram, imagem, "S", _*) =>
              val data = Evento.parseData(dia, hora)
              val dataFim = Evento.parseData(dia, horaFim)
              val dataFimAjustado = if (dataFim.isAfter(data)) dataFim else dataFim.plusDays(1) // Termina no dia seguinte
              val evento = Evento(nome, info, data, dataFimAjustado, Some(youtube), Some(instagram), Some(imagem))
              Some(evento)
            case errList =>
              println(s"### Error: ###")
              errList.foreach(str => s" $str /")
              println(s"### Error FIM ###")
              None
          }
          .flatten
          .sortBy(_.data)
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
    getEventos.map(_.filter(filtroEventosAgora))
  }

  def eventosAconteceraoHoje() = {
    def filtroEventosHoje(evento: Evento) = !filtroEventosAgora(evento) &&
      evento.data.toLocalDate.isEqual(LocalDate.now) &&
      evento.data.isAfter(LocalDateTime.now)

    getEventos.map(_.filter(filtroEventosHoje))
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
