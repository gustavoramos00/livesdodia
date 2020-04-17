package model


import java.time.{LocalDate, LocalDateTime, LocalTime}
import java.time.format.DateTimeFormatter

case class Evento(
                   nome: String,
                   info: String,
                   data: LocalDateTime,
                   dataFim: LocalDateTime,
                   linkYoutube: Option[String],
                   linkInstagram: Option[String],
                   destaque: Boolean
                 ) {
  def horarioFmt: String = data.format(Evento.horaMinFormatter)
}

object Evento {

  private val horaMinPattern = "HH:mm"
  private val horaMinSegPattern = "HH:mm:SS"
  private val diaMesAnoPattern = "dd/MM/yyyy"
  private val horaMinFormatter = DateTimeFormatter.ofPattern(horaMinPattern)
  private val horaMinSegFormatter = DateTimeFormatter.ofPattern(horaMinSegPattern)
  private val dataFormatter = DateTimeFormatter.ofPattern(diaMesAnoPattern)
  private val dataHoraFormatter = DateTimeFormatter.ofPattern(s"$diaMesAnoPattern $horaMinSegFormatter")

  def parseHorario(hora: String) = {
    if (hora.size.eq(horaMinPattern.size))
      LocalTime.parse(hora, horaMinFormatter)
    else
      LocalTime.parse(hora, horaMinSegFormatter)
  }

  def parseData(dia: String, hora: String) = LocalDate.parse(dia, dataFormatter).atTime(parseHorario(hora))

  def formatDiaHora(data: LocalDateTime) = dataHoraFormatter.format(data)
}
