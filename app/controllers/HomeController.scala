package controllers

import java.time.LocalDate

import javax.inject._
import model.EventosDia
import play.api.mvc._
import service.RepositoryService

import scala.concurrent.ExecutionContext

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(repository: RepositoryService,
                                val controllerComponents: ControllerComponents
                              )(implicit ec: ExecutionContext) extends BaseController {


  /**
   * Create an Action to render an HTML page.
   *
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index() = Action.async { implicit request: Request[AnyContent] =>
    for {
      eventosAgora <- repository.eventosAgora()
      eventosHoje <- repository.eventosAconteceraoHoje()
      eventosProgramacao <- repository.eventosProximosDias()
    } yield Ok(views.html.index(eventosAgora, eventosHoje, eventosProgramacao))

  }

  def updateCache() = Action.async { implicit request: Request[AnyContent] =>
    repository.update()
      .map(_ => Ok)
  }
}
