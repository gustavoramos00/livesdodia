package model

import java.net.URL

case class YoutubeData(
                        linkRegistrado: Option[String] = None,
                        channelId: Option[String] = None,
                        userName: Option[String] = None,
                        customUrl: Option[String] = None,
                        videoId: Option[String] = None,
                        liveBroadcastContent: Option[String] = None,
                        channelImg: Option[String] = None,
                        videoImg: Option[String] = None
                      ) {
  def link =
    linkVideoId
    .orElse(canalYoutube)
    .orElse(linkRegistrado)

  def linkVideoId = videoId.map(vid => s"https://youtube.com/watch?v=$vid")

  def canalYoutube = channelId.map(channel => s"https://youtube.com/channel/$channel")
}

object YoutubeData {

  val broadcastEncerrado = "none"
  val broadcastLive = "live"

  def fromYoutubeLink(youtubeLink: String) = {
    val safeLink = "https://" + youtubeLink
      .replaceFirst("https://", "")
      .replaceFirst("http://", "")
    val url = new URL(safeLink)
    if (Seq("youtube.com", "www.youtube.com").contains(url.getHost)) {
      val path = url.getPath
        .tail // remove primeiro caracter - sempre uma barra "/"
        .split('/')

      if (path.headOption.contains("watch")) { // youtube.com/watch?v=XYZ
        // converte query parameters em Map
        val queryMap = url.getQuery
          .split(Array('&', '='))
          .grouped(2)
          .map {
            case Array(key: String, value: String) => key -> value
          }.toMap
        val videoId = queryMap.get("v")
        YoutubeData(linkRegistrado = Some(youtubeLink), videoId = videoId)
      } else if (path.length == 1) { // customUrl
        YoutubeData(linkRegistrado = Some(youtubeLink), customUrl = path.headOption)
      } else if (path.headOption.contains("user")) { // youtube.com/user/XYZ
        YoutubeData(linkRegistrado = Some(youtubeLink), userName = path.drop(1).headOption)
      } else if (path.headOption.contains("channel")) { // youtube.com/channel/XYZ
        YoutubeData(linkRegistrado = Some(youtubeLink), channelId = path.drop(1).headOption)
      } else {
        YoutubeData(linkRegistrado = Some(youtubeLink))
      }
    } else {
      YoutubeData(linkRegistrado = Some(youtubeLink))
    }

  }
}