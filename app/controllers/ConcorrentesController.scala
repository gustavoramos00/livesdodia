package controllers

import java.time._

import javax.inject.Inject
import model.Evento
import play.api.libs.json.{JsArray, Json}
import play.api.libs.ws.WSClient
import play.api.mvc.{AnyContent, BaseController, ControllerComponents, Request}
import service.RepositoryService

import scala.concurrent.{ExecutionContext, Future}

class ConcorrentesController @Inject()(repository: RepositoryService,
                                       ws: WSClient,
                                       val controllerComponents: ControllerComponents
                                      )(implicit ec: ExecutionContext) extends BaseController {

  val livesmusbr = "https://api.lives.mus.br/lives"
  val agendalives = "https://agendalives.info/api"
  val livesdodia = "https://livesdodia.com.br/livesjson"
  val livesbrasil = "https://api.livesbrasil.com/lives"

  def dadosLivesDoDia = {
    ws.url(livesdodia)
      .get()
      .map(response => {
        response.json
          .as[JsArray]
          .value
          .filter(event => !(event \ "data").as[LocalDate].isBefore(LocalDate.now)) // exclui antigos
          .map(event => {
            val artista = (event \ "nome").asOpt[String].getOrElse("")
            val info = (event \ "info").asOpt[String].getOrElse("")
            val data = (event \ "data").as[LocalDate]
            val horario = (event \ "horario").as[LocalTime]
            val instagram = (event \ "instagram").asOpt[String]
            val youtubeChannel = (event \ "youtube").asOpt[String]
//            val categorias = (event \ "categories").as[JsArray].value.map(cat => (cat \ "name").as[String]).toSeq
            Evento(
              nome = artista,
              info = info,
              data = data.atTime(horario),
              youtubeLink = youtubeChannel,
              instagramProfile = instagram,
              destaque = true,
              origem = Some("Lives do Dia"))
            //            s"$artista\t$titulo\t${data.toLocalDate}\t${data.toLocalTime}\t${categorias.mkString(",")}\t$youtubeChannel\t$instagram"
          })
          .toSeq
      })
  }

  def dadosAgendaLives: Future[Seq[Evento]] = {
    def generosAgendaLives = {
      ws.url(agendalives + "/genre")
        .get()
        .map(response => {
          (response.json \ "genres")
            .as[JsArray]
            .value
            .map(genre => {
              val id = (genre \ "id").as[Int]
              val nome = (genre \ "name").as[String]
              (id, nome)
            })
            .toMap
        })
    }

    def bandaGeneroAgendaLives = {
      ws.url(agendalives + "/band")
        .get()
        .map(response => {
          (response.json \ "bands")
            .as[JsArray]
            .value
            .map(band => {
              val id = (band \ "id").as[Int]
              val genre = (band \ "genre").asOpt[Int]
              (id, genre)
            })
            .toMap
        })
    }

    def eventosAgendaLives = {
      ws.url(agendalives + "/live")
        .get()
        .map(response => {
          (response.json \ "lives")
            .as[JsArray]
            .value
            .filter(event => !(event \ "when").as[ZonedDateTime].withZoneSameInstant(ZoneId.systemDefault()).toLocalDate.isBefore(LocalDate.now)) // exclui antigos
            .map(event => {
            val artista = (event \ "title").asOpt[String].getOrElse("")
            val info = (event \ "description").asOpt[String].getOrElse("")
            val data = (event \ "when").as[ZonedDateTime].withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime
            val instagram = (event \ "link_instagram").asOpt[String]
            val youtubeChannel = (event \ "link_youtube").asOpt[String]
            val band = (event \ "band").as[Int]
            val evento = Evento(
              nome = artista,
              info = info,
              data = data,
              youtubeLink = youtubeChannel,
              instagramProfile = instagram,
              destaque = false,
              origem = Some("AgendaLives"))
            (evento, band)
          })
            .toSeq
        })
    }

    for {
      generos <- generosAgendaLives
      bandasGenero <- bandaGeneroAgendaLives
      eventos <- eventosAgendaLives
    } yield {
      eventos.map {
        case (evento, bandaId) =>
          val optGeneroId = bandasGenero.get(bandaId).flatten
          val genero = optGeneroId.map(generoId => generos.getOrElse(generoId, ""))
          val categorias = if (genero.isDefined) Seq(genero.get) else Seq.empty
          evento.copy(tags = categorias)
      }
    }
  }

  def dadosLivesMus = {
    ws.url(livesmusbr)
      .get()
      .map(response => {
        response.json
          .as[JsArray]
          .value
          .filter(event => (event \ "active").as[Boolean]) // filtra os ativos
          .filter(event => !(event \ "datetime").as[ZonedDateTime].withZoneSameInstant(ZoneId.systemDefault()).toLocalDate.isBefore(LocalDate.now)) // exclui antigos
          .map(event => {
            val artista = (event \ "artists").asOpt[String].getOrElse("")
            val info = (event \ "title").asOpt[String].getOrElse("")
            val data = (event \ "datetime").as[ZonedDateTime].withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime
            val instagram = (event \ "instagram").asOpt[String]
            val youtubeChannel = (event \ "youtube_channel").asOpt[String]
            val categorias = (event \ "categories").as[JsArray].value.map(cat => (cat \ "name").as[String]).toSeq
            Evento(
              nome = artista,
              info = info,
              data = data,
              tags = categorias,
              youtubeLink = youtubeChannel,
              instagramProfile = instagram,
              destaque = false,
              origem = Some("Lives.mus.br"))
          })
          .toSeq
      })
  }

  def dadosLivesBrasil = {
    ws.url(livesbrasil)
      .get()
      .map(response => {
        val current = (response.json \ "current").as[JsArray]
        val upcomingIndexSeq = Json.toJson(response.json \ "upcoming" \\ "items")
          .as[JsArray]
          .value.flatMap(up => up.as[JsArray].value)
        val upcoming = Json.toJson(upcomingIndexSeq).as[JsArray]
        (current ++ upcoming)
          .value
          .filter(event => !(event \ "startsAt").as[ZonedDateTime].withZoneSameInstant(ZoneId.systemDefault()).toLocalDate.isBefore(LocalDate.now)) // exclui antigos
          .map(event => {
            val artista = (event \ "title").asOpt[String].getOrElse("")
            val info = (event \ "description").asOpt[String].getOrElse("").replaceAll("[^a-zA-Z0-9 -]", "")
            val data = (event \ "startsAt").as[ZonedDateTime].withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime
            val url = (event \ "url").asOpt[String]
            val youtubeLink = if (url.isDefined && url.get.contains("youtube")) url else None
            val instagram = if (url.isDefined && url.get.contains("instagram")) url else None
            val categoria = (event \ "genreName").asOpt[String].getOrElse("")
            Evento(
              nome = artista,
              info = info,
              data = data,
              tags = Seq(categoria),
              youtubeLink = youtubeLink,
              instagramProfile = instagram,
              destaque = false,
              origem = Some("LivesBrasil"))
          })
          .toSeq
      })
  }

  def extrairDados() = Action.async { implicit request: Request[AnyContent] =>
    for {
      livesdodia <- dadosLivesDoDia
      livesmusbr <- dadosLivesMus
      livesbrasil <- dadosLivesBrasil
      agendaLives <- dadosAgendaLives
    } yield {
      val eventos = (livesdodia ++ livesmusbr ++ livesbrasil ++ agendaLives).sortBy(ev => (ev.data, ev.nome))
      val result = eventos.map(ev => {
        s"${ev.nome}\t${ev.info}\t${ev.data.toLocalDate}\t${ev.data.toLocalTime}\t${ev.tags.mkString(",")}\t" +
        s"${ev.youtubeLink.getOrElse("")}\t${ev.instagramProfile.getOrElse("")}\t${ev.origem.get}\t"
      })
      Ok(result.mkString("\n"))
    }
  }

}
