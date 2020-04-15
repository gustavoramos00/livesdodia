package service

import java.time.{LocalDate, LocalDateTime}

import javax.inject.{Inject, Singleton}
import model.{Evento, EventosDia}
import play.api.cache.AsyncCacheApi
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcCurlRequestLogger

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.Reads._

@Singleton
class RepositoryService @Inject()(
                                   ws: WSClient,
                                   cache: AsyncCacheApi
                                 )
                                 (implicit ec: ExecutionContext){

  val endpoint = "https://sheets.googleapis.com"
  val spreadsheetId = "1egI7CxubWSinAekiPf_3ay1_5jIlt_jX5pIVzDXKqXk"
  val sheetId = "0"
  val apiKey = "AIzaSyCmcAoK5FG5ZpgeQAuGugdEzMwhfQ-vMdA"
  val ranges = "Eventos"
  val urlSpreadSheet = s"$endpoint/v4/spreadsheets/${spreadsheetId}/values/$ranges"
  val cacheKey = "eventos"

  def update() = {
    ws.url(urlSpreadSheet)
      .addQueryStringParameters("key" -> apiKey)
      .withRequestFilter(AhcCurlRequestLogger())
      .get()
      .map { response =>
        val valuesArray = (response.json \ "values").as[Array[Array[String]]]
        val eventos = valuesArray
          .tail // remove cabeçalho
          .map {
            case Array(nome, info, dia, hora, horaFim, youtube, instagram, imagem, "S", _*) =>
              val data = Evento.parseData(dia, hora)
              val dataFim = Evento.parseData(dia, horaFim)
              val dataFimAjustado = if (dataFim.isAfter(data)) dataFim else data.plusDays(1) // Termina no dia seguinte
              val evento = Evento(nome, info, data, dataFimAjustado, Some(youtube), Some(instagram), Some(imagem))
              Some(evento)
            case errArray =>
              println(s"### Error: ###")
              errArray.foreach(str => s" $str /")
              println(s"### Error FIM ###")
              None
          }
          .flatten
          .sortBy(_.data)
        cache.set(cacheKey, eventos)
        eventos
      }
  }

  private def getEventos = {
    cache
      .get[Array[Evento]](cacheKey)
      .map {
        case Some(array) => Future.successful(array)
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

    getEventos.map(array => {
      array
        .filter(_.data.toLocalDate.isAfter(LocalDate.now))
        .groupBy(_.data.toLocalDate)
        .toList
        .sortBy(_._1)
        .map { case (dia: LocalDate, eventos: Array[Evento]) => EventosDia(dia, eventos)}
    })
  }

}
