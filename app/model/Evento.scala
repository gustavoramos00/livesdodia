package model


import java.net.URLEncoder
import java.time.{Duration, LocalDate, LocalDateTime, LocalTime}
import java.time.format.DateTimeFormatter

case class Evento(
                   nome: String,
                   info: String,
                   data: LocalDateTime,
                   linkYoutube: Option[String],
                   linkInstagram: Option[String],
                   destaque: Boolean
                 ) {
  def horarioFmt: String = {
    val duration = Duration.between(data, LocalDateTime.now)
    val horas = duration.toHours
    val minutos = duration.minusHours(horas).toMinutes
    if (duration.isNegative)
      data.format(Evento.horaMinFormatter)
    else if (horas > 0)
      f"Há ${horas}h ${minutos}%02dmin"
    else
      f"Há ${minutos}%02dmin"
  }

  def generatedId: String = nome.replaceAll("[^a-zA-Z]+", "") + data.toLocalDate

  def urlEncodedShare: String = {
    val horario = data.format(Evento.horaMinFormatter)
    val prefixo =
      if (Duration.between(data, LocalDateTime.now).isNegative) "Não perca essa live!"
      else "Veja essa live, já começou!"
    val link = linkYoutube.orElse(linkInstagram).map(url => "\uD83C\uDFA6" + url)
    val text =
      s"$prefixo\n\n" +
      s"▶ *$nome*\n" +
      s"️🗓️ *${Evento.formatDia(data)}*\n" +
      s"🕒 *$horario*\n" +
      s"${info}\n" +
      s"${link.getOrElse("")}\n\n" +
      s"*Programação completa em https://livesdodia.com.br*"
    URLEncoder.encode(text, "UTF-8")
  }

  def urlEncodedShareFacebook = {
    val horario = data.format(Evento.horaMinFormatter)
    val link = linkYoutube.orElse(linkInstagram)
    val text = s"Veja essa live que achei em https://livesdodia.com.br\n\n$nome\n${Evento.formatDia(data)}\n$horario*\n${info}\n\n${link.getOrElse("")}"
    URLEncoder.encode(text, "UTF-8")
  }

}

object Evento {

  private val horaMinPattern = "HH:mm"
  private val horaMinSegPattern = "HH:mm:ss"
  private val diaMesAnoPattern = "dd/MM/yyyy"
  private val horaMinFormatter = DateTimeFormatter.ofPattern(horaMinPattern)
  private val horaMinSegFormatter = DateTimeFormatter.ofPattern(horaMinSegPattern)
  private val dataFormatter = DateTimeFormatter.ofPattern(diaMesAnoPattern)
  private val dataHoraFormatter = DateTimeFormatter.ofPattern(s"$diaMesAnoPattern $horaMinPattern")

  def parseHorario(hora: String) = {
    if (hora.size == horaMinPattern.size)
      LocalTime.parse(hora, horaMinFormatter)
    else
      LocalTime.parse(hora, horaMinSegFormatter)
  }

  def parseData(dia: String, hora: String) = LocalDate.parse(dia, dataFormatter).atTime(parseHorario(hora))

  def formatDiaHora(data: LocalDateTime) = dataHoraFormatter.format(data)

  def formatDia(data: LocalDateTime): String = dataFormatter.format(data)
}
