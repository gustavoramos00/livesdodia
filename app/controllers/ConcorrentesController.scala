package controllers

import java.time._
import java.time.format.DateTimeFormatter

import javax.inject.Inject
import model.{Evento, YoutubeData}
import play.api.libs.json.JsArray
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
  val regexRemoveChars = "(;\\r\\n|\\r|\\n|\\t|\u21b5)"

  def dadosLivesDoDia = {
    ws.url(livesdodia)
      .get()
      .map(response => {
        response.json
          .as[JsArray]
          .value
          .filter(event => !(event \ "data").as[LocalDate].isBefore(LocalDate.now)) // exclui antigos
          .map(event => {
            val id = (event \ "id").asOpt[String]
            val artista = (event \ "nome").asOpt[String].getOrElse("")
            val info = (event \ "info").asOpt[String].getOrElse("")
            val data = (event \ "data").as[LocalDate]
            val horario = (event \ "horario").as[LocalTime]
            val instagram = (event \ "instagram").asOpt[String].filter(_.nonEmpty)
            val outroLink = (event \ "outroLink").asOpt[String].filter(_.nonEmpty)
            val youtubeChannel = (event \ "youtube").asOpt[String].filter(_.nonEmpty)
            val linkImagem = (event \ "linkImagem").asOpt[String].filter(_.nonEmpty)
            val tags = (event \ "tags").asOpt[String].map(_.split(",").toSeq).getOrElse(Seq.empty)
            Evento(
              id = id,
              nome = artista,
              info = info,
              data = data.atTime(horario),
              youtubeLink = youtubeChannel,
              outroLink = outroLink,
              instagramProfile = instagram,
              tags = tags,
              destaque = true,
              linkImagem = linkImagem,
              origem = Some("Lives do Dia"))
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
              val youtube = (band \ "link_youtube").asOpt[String]
              val instagram = (band \ "link_instagram").asOpt[String]
              (id, BandaAgendaLives(id, youtube, instagram, genre))
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
            val artista = (event \ "title").asOpt[String].getOrElse("").replaceAll(regexRemoveChars, "").trim
            val info = (event \ "description").asOpt[String].getOrElse("").replaceAll(regexRemoveChars, "").trim
            val data = (event \ "when").as[ZonedDateTime].withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime
            val instagram = (event \ "link_instagram").asOpt[String]
            val youtubeChannel = (event \ "link_youtube").asOpt[String]
            val band = (event \ "band").as[Int]
            val evento = Evento(
              id = None,
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
          val optBanda = bandasGenero.get(bandaId)
          val optGeneroId = optBanda.flatMap(_.genero)
          val genero = optGeneroId.map(generoId => generos.getOrElse(generoId, ""))
          val categorias = if (genero.isDefined) Seq(genero.get) else Seq.empty
          val youtubeLink = evento.youtubeLink.orElse(optBanda.flatMap(_.youtube))
          val instagramProfile = evento.instagramProfile.orElse(optBanda.flatMap(_.instagram))
          evento.copy(tags = categorias, youtubeLink = youtubeLink, instagramProfile = instagramProfile)
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
            val artista = (event \ "artists").asOpt[String].getOrElse("").replaceAll(regexRemoveChars, "").trim
            val info = (event \ "title").asOpt[String].getOrElse("").replaceAll(regexRemoveChars, "").trim
            val data = (event \ "datetime").as[ZonedDateTime].withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime
            val instagram = (event \ "instagram").asOpt[String]
            val linkImagem = (event \ "banner").asOpt[String]
            val youtubeChannel = (event \ "youtube_channel").asOpt[String].filter(_.nonEmpty)
            val youtubeVideoId = (event \ "youtube_video").asOpt[String].filter(_.nonEmpty).map(videoId => s"https://youtube.com/watch?v=$videoId")
            val externalLink = (event \ "external_url").asOpt[String].filter(_.nonEmpty)
            val categorias = (event \ "categories").as[JsArray].value.map(cat => (cat \ "name").as[String]).toSeq
            val youtubeData = youtubeChannel.orElse(youtubeVideoId).orElse(externalLink).map(YoutubeData.fromYoutubeLink)
            Evento(
              id = None,
              nome = artista,
              info = info,
              data = data,
              tags = categorias,
              youtubeLink = youtubeData.flatMap(_.link),
              instagramProfile = instagram,
              youtubeData = youtubeData,
              destaque = false,
              linkImagem = linkImagem,
              origem = Some("Lives.mus.br"))
          })
          .toSeq
      })
  }

  def dadosLivesBrasil = {
    ws.url(livesbrasil)
      .get()
      .map(response => {
        val lives = (response.json \ "lives").as[JsArray]
        (lives)
          .value
          .filter(event => !(event \ "startsAt").as[ZonedDateTime].withZoneSameInstant(ZoneId.systemDefault()).toLocalDate.isBefore(LocalDate.now)) // exclui antigos
          .map(event => {
            val artista = (event \ "title").asOpt[String].getOrElse("").replaceAll(regexRemoveChars, "").trim
            val info = (event \ "description").asOpt[String].getOrElse("").replaceAll(regexRemoveChars, "").trim
            val data = (event \ "startsAt").as[ZonedDateTime].withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime
            val url = (event \ "url").asOpt[String]
            val youtubeLink = if (url.isDefined && !url.get.contains("instagram")) url else None
            val instagram = if (url.isDefined && url.get.contains("instagram")) url else None
            val categoria = (event \ "genreName").asOpt[String].getOrElse("")
            val thumbnail = (event \ "imageUrl").asOpt[String]
            Evento(
              id = None,
              nome = artista,
              info = info,
              data = data,
              tags = Seq(categoria),
              youtubeLink = youtubeLink,
              instagramProfile = instagram,
              destaque = false,
              linkImagem = thumbnail,
              origem = Some("LivesBrasil"))
          })
          .toSeq
      })
  }

//  def dadosSofaDeCasa = {
//    def endpoint(dia: String) = s"https://www.sofadecasa.com.br/api/lives?day=$dia"
//
//    ws.url(endpoint(LocalDate.now.toString))
//      .get()
//      .map(response => {
//        val lives = (response.json \ "lives").as[JsArray]
//        (lives)
//          .value
//          .filter(event => !(event \ "startsAt").as[ZonedDateTime].withZoneSameInstant(ZoneId.systemDefault()).toLocalDate.isBefore(LocalDate.now)) // exclui antigos
//          .map(event => {
//          val artista = (event \ "title").asOpt[String].getOrElse("").replaceAll(regexRemoveChars, "").trim
//          val info = (event \ "description").asOpt[String].getOrElse("").replaceAll(regexRemoveChars, "").trim
//          val data = (event \ "startsAt").as[ZonedDateTime].withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime
//          val url = (event \ "url").asOpt[String]
//          val youtubeLink = if (url.isDefined && !url.get.contains("instagram")) url else None
//          val instagram = if (url.isDefined && url.get.contains("instagram")) url else None
//          val categoria = (event \ "genreName").asOpt[String].getOrElse("")
//          val thumbnail = (event \ "imageUrl").asOpt[String]
//          Evento(
//            id = None,
//            nome = artista,
//            info = info,
//            data = data,
//            tags = Seq(categoria),
//            youtubeLink = youtubeLink,
//            instagramProfile = instagram,
//            destaque = false,
//            linkImagem = thumbnail,
//            origem = Some("LivesBrasil"))
//        })
//          .toSeq
//      })
//  }

  def extrairDados() = Action.async { implicit request: Request[AnyContent] =>
    for {
      livesdodia <- dadosLivesDoDia
      livesmusbr <- dadosLivesMus
      livesbrasil <- dadosLivesBrasil
      agendaLives <- dadosAgendaLives
    } yield {
      val filtrado = (livesmusbr ++ livesbrasil ++ agendaLives).filter(ev => {
        livesdodia.find(evld => evld.nome.toLowerCase() == ev.nome.toLowerCase() &&
          evld.data.isEqual(ev.data) &&
          evld.tags.length > 1).isEmpty
      })
      val eventos = (livesdodia ++ filtrado).sortBy(ev => (ev.data, ev.nome))
      val header = Seq(
        "UUID",
        "EMAIL",
        "NOME",
        "INFO",
        "DATA",
        "HORA",
        "TAGS",
        "YOUTUBE/SITE",
        "INSTAGRAM",
        "DESTAQUE",
        "IMAGEM",
        "ORIGEM/PUBLICAR")
        .mkString(",")
      val body =  eventos.map(ev => {
        Seq(ev.id.getOrElse(""),
          ev.origem.get,
          "\"" + ev.nome + "\"",
          "\"" + ev.info + "\"",
          Evento.formatDia(ev.data),
          ev.data.toLocalTime.toString,
          "\"" + ev.tags.mkString(",") + "\"",
          "\"" + ev.youtubeLink.orElse(ev.outroLink).getOrElse("") + "\"",
          "\"" + ev.instagramProfile.getOrElse("") + "\"",
          "",
          "\"" + ev.thumbnailUrl.getOrElse("") + "\"",
          ev.origem.filter(_.equalsIgnoreCase("Lives do Dia")).map(_ => "S").getOrElse("N"))
          .mkString(",")
      })
      val result = header + "\n" + body.mkString("\n")
      Ok(result).as("text/csv")
        .withHeaders(
          "Content-Disposition" -> "attachment; filename=lives.csv"
        )
    }
  }

  private def printLiveSemana(lives: Seq[Evento]) = {
    val formatter = DateTimeFormatter.ofPattern("dd/MM EEEE HH:mm")
    val lista = lives.sortBy(ev => (ev.data, ev.nome)).map(ev => {
      s"${ev.nome} ${ev.data.format(formatter)}"
    }).mkString("\n")
    Ok(lista)
  }

  private def printLive(lives: Seq[Evento]) = {
    val lista = lives.sortBy(ev => (ev.data, ev.nome)).map(ev => {
      s"${ev.nome} ${ev.data.toLocalTime}"
    }).mkString("\n")
    Ok(lista)
  }

  def livesHoje() = Action.async { implicit request: Request[AnyContent] =>
    for {
      livesdodia <- dadosLivesDoDia
    } yield printLive(livesdodia.filter(ev => ev.data.toLocalDate.isEqual(LocalDate.now)))
  }

  def livesAmanha() = Action.async { implicit request: Request[AnyContent] =>
    for {
      livesdodia <- dadosLivesDoDia
    } yield {
      printLive(livesdodia.filter(ev => ev.data.toLocalDate.isEqual(LocalDate.now.plusDays(1))))
    }
  }

  def livesSemana() =  Action.async { implicit request: Request[AnyContent] =>
    for {
      livesdodia <- dadosLivesDoDia
    } yield {
      printLiveSemana(livesdodia.filter(ev => ev.data.toLocalDate.isAfter(LocalDate.now)))
    }
  }

}

case class BandaAgendaLives(id: Int, youtube: Option[String], instagram: Option[String], genero: Option[Int])