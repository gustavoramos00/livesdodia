package model

import java.time.LocalDate
import java.time.format.DateTimeFormatter

case class EventosDia(
                     data: LocalDate,
                     eventos: Seq[Evento]
                     ) {
  def diaFmt(): String = data.format(DateTimeFormatter.ofPattern("dd/MM"))
  def diaSemanaFmt(): String = data.format(DateTimeFormatter.ofPattern("EEEE"))
  def diaProgramacaoFmt(): String = {
    if (data.isEqual(LocalDate.now())) {
      "Hoje"
    } else if (data.isEqual(LocalDate.now.plusDays(1))) {
      "Amanhã"
    } else {
      diaSemanaFmt()
    }
  }
}
