package controllers

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}
import java.time.temporal.ChronoUnit

import javax.inject._
import model.{Evento, EventosDia}
import play.api.cache.Cached
import play.api.mvc._
import service.RepositoryService

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._


/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(repository: RepositoryService,
                               cached: Cached,
                                val controllerComponents: ControllerComponents
                              )(implicit ec: ExecutionContext) extends BaseController {




  /**
   * Create an Action to render an HTML page.
   *
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index() = cached(_ => "index", 1.minute) {

    Action.async { implicit request: Request[AnyContent] =>
      for {
        eventosAgora <- repository.eventosAgora()
        eventosHoje <- repository.eventosAconteceraoHoje()
        eventosProgramacao <- repository.eventosProximosDias()
        atualizadoEm <- repository.atualizadoEm()
      } yield {
        val proxEventoMiliSec = eventosHoje.headOption.map(ev => LocalDateTime.now.until(ev.data, ChronoUnit.MILLIS))
        val jsonld = jsonLdSchemaLivesDoDia(eventosAgora, eventosHoje, eventosProgramacao)
        Ok(views.html.index(eventosAgora, eventosHoje, eventosProgramacao, atualizadoEm, proxEventoMiliSec, jsonld))
      }
    }
  }

  def updateCache() = Action.async { implicit request: Request[AnyContent] =>
    repository.forceUpdate()
      .map(_ => Redirect("/"))
  }

  def incluir() =
    cached(_ => "incluir", 1.minute) {
      Action { implicit request: Request[AnyContent] =>
        Ok(views.html.incluir())
      }
    }

  private def jsonLdSchemaLivesDoDia(eventosAgora: List[Evento], eventosHoje: List[Evento], eventosProgramacao: List[EventosDia]) = {
    s"""[{
       |  "@context": "http://schema.org",
       |  "@type": "Guide",
       |  "name": "Lives acontecendo agora",
       |  "about": [${jsonldSchemaEventos((eventosAgora))}]
       |},
       |{
       |  "@context": "http://schema.org",
       |  "@type": "Guide",
       |  "name": "Lives de hoje",
       |  "about": [${jsonldSchemaEventos((eventosHoje))}]
       |},
       |{
       |  "@context": "http://schema.org",
       |  "@type": "Guide",
       |  "name": "Agenda das lives",
       |  "about": [${jsonldSchemaEventos((eventosProgramacao.flatMap(_.eventos)))}]
       |
       |}
       |]""".stripMargin
  }

  private def jsonldSchemaEventos(eventos: List[Evento]): String = {
    val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    eventos.map(evento => {
      s"""{
         |  "@type": "BroadcastEvent",
         |  "name": "${evento.nome}",
         |  "description": "${evento.info}",
         |  "isAccessibleForFree": "true",
         |  "isLiveBroadcast": "true",
         |  "startDate": "${evento.data.atZone(ZoneId.systemDefault()).format(formatter)}",
         |  "endDate": "${evento.data.plusHours(3).atZone(ZoneId.systemDefault()).format(formatter)}"
         |}""".stripMargin
    }).mkString(",")
  }
}
