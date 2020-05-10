package controllers

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{LocalDate, LocalDateTime, ZoneId}

import javax.inject._
import model.{Evento, EventosDia}
import play.api.cache.Cached
import play.api.libs.json.Json
import play.api.mvc._
import service.{LiveScheduler, RepositoryService}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._


/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(repository: RepositoryService,
                               cached: Cached,
                               liveScheduler: LiveScheduler,
                               val controllerComponents: ControllerComponents
                              )(implicit ec: ExecutionContext) extends BaseController {

  val cacheDuration = 20.seconds


  /**
   * Create an Action to render an HTML page.
   *
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index() = cached(_ => "index", cacheDuration) {

    Action.async { implicit request: Request[AnyContent] =>
      for {
        eventosAgora <- repository.eventosAgora()
        eventosHoje <- repository.eventosAconteceraoHoje()
        eventosProgramacao <- repository.eventosProximosDias()
        atualizadoEm <- repository.atualizadoEm()
        tagsColor <- repository.tagsColor()
      } yield {
        val jsonld = jsonLdSchemaLivesDoDia(eventosAgora, eventosHoje, eventosProgramacao)
        Ok(views.html.index(eventosAgora, eventosHoje, eventosProgramacao, atualizadoEm, jsonld, tagsColor))
      }
    }
  }

  def subscribeLive(id: String) = Action { implicit request: Request[AnyContent] =>
    println(s"subscribe live id [$id]")
    if (request.contentType.contains("application/json")) {
      println(s"request body json [${request.body.asJson}]")
    }
    InternalServerError
  }

  def eventosHoje() = cached(_ => "jaComecou", cacheDuration) {

    Action.async { implicit request: Request[AnyContent] =>
      for {
        eventosAgora <- repository.eventosAgora()
        eventosHoje <- repository.eventosAconteceraoHoje()
        tagsColor <- repository.tagsColor()
      } yield {
        Ok(views.html.eventosHoje(eventosAgora, eventosHoje, tagsColor))
      }
    }
  }

  def updateCache(id: Option[String]) = Action.async { implicit request: Request[AnyContent] =>
    repository.forceUpdate(id)
      .map(_ => Redirect("/"))
  }

  def incluir() =
    cached(_ => "incluir", cacheDuration) {
      Action { implicit request: Request[AnyContent] =>
        Ok(views.html.incluir())
      }
    }


  def livesJson() = Action.async { implicit request: Request[AnyContent] =>
    for {
      eventos <- repository.getEventos
    } yield {
      val json = eventos
        .filter(!_.data.toLocalDate.isBefore(LocalDate.now))
        .sortBy(_.data)
        .map(ev => Json.obj(
          "id" -> ev.id,
          "nome" -> ev.nome,
          "info" -> ev.info,
          "data" -> ev.data.toLocalDate.toString,
          "horario" -> ev.data.toLocalTime.toString,
          "tags" -> ev.tags.mkString(","),
          "outroLink" -> ev.outroLink,
          "youtube" -> ev.youtubeData.flatMap(_.link).getOrElse("").toString,
          "instagram" -> ev.instagramProfile.getOrElse("").toString,
          "destaque" -> ev.destaque,
          "linkImagem" -> ev.thumbnailUrl,
          "publicar" -> "S"
        ))


      Ok(Json.toJson(json))
    }
  }

  def scheduleCount() = Action { implicit request: Request[AnyContent] =>
    Ok(liveScheduler.scheduleCount.toString)
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
