package controllers

import javax.inject._
import play.api._
import play.api.http.DefaultHttpErrorHandler
import play.api.mvc.Results._
import play.api.mvc._
import play.api.routing.Router

import scala.concurrent._

@Singleton
class ErrorHandler @Inject()(env: Environment,
                             config: Configuration,
                             sourceMapper: OptionalSourceMapper,
                             router: Provider[Router]) extends DefaultHttpErrorHandler(env, config, sourceMapper, router) {

  private val logger: Logger = Logger(this.getClass)

  override def onProdServerError(request: RequestHeader, exception: UsefulException) = {
    logger.error(s"### Ocorreu um erro inesperado \n exception [$exception] \n request[$request]")
    Future.successful(
//        InternalServerError(views.html.erroInesperado())
      InternalServerError // TODO incluir html
    )
  }

  override def onBadRequest(request: RequestHeader, message: String): Future[Result] = {
    // logger.error(s"### badrequest \nmsg [$message] \n request[$request]")
    this.onNotFound(request, message)
  }

  override def onNotFound(request: RequestHeader, message: String) = {
    // logger.error(s"### notfound\n msg [$message]\n request[$request]")
    Future.successful(
//      NotFound(views.html.notfound())
      NotFound  // TODO incluir html
    )
  }

}
