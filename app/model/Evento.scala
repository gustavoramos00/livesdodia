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
                   linkImagem: Option[String]
                 ) {
  def horarioFmt: String = data.format(Evento.horaPattern)
}

object Evento {

  private val horaPattern = DateTimeFormatter.ofPattern("HH:mm")
  private val datePattern = DateTimeFormatter.ofPattern("dd/MM/yyyy")
  private val dataHoraPattern = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:SS")

  def parseHorario(hora: String) = LocalTime.parse(hora, horaPattern)

  def parseData(dia: String, hora: String) = LocalDate.parse(dia, datePattern).atTime(parseHorario(hora))

  def formatDiaHora(data: LocalDateTime) = dataHoraPattern.format(data)
}
