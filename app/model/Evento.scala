package model


import java.time.{Duration, LocalDate, LocalDateTime, LocalTime}
import java.time.format.DateTimeFormatter
import java.time.temporal.{ChronoUnit, TemporalUnit}

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
}

object Evento {

  private val horaMinPattern = "HH:mm"
  private val horaMinSegPattern = "HH:mm:ss"
  private val diaMesAnoPattern = "dd/MM/yyyy"
  private val horaMinFormatter = DateTimeFormatter.ofPattern(horaMinPattern)
  private val horaMinSegFormatter = DateTimeFormatter.ofPattern(horaMinSegPattern)
  private val dataFormatter = DateTimeFormatter.ofPattern(diaMesAnoPattern)
  private val dataHoraFormatter = DateTimeFormatter.ofPattern(s"$diaMesAnoPattern $horaMinSegPattern")

  def parseHorario(hora: String) = {
    if (hora.size == horaMinPattern.size)
      LocalTime.parse(hora, horaMinFormatter)
    else
      LocalTime.parse(hora, horaMinSegFormatter)
  }

  def parseData(dia: String, hora: String) = LocalDate.parse(dia, dataFormatter).atTime(parseHorario(hora))

  def formatDiaHora(data: LocalDateTime) = dataHoraFormatter.format(data)
}
