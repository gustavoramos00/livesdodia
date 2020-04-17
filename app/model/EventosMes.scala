package model

import java.time.LocalDate
import java.time.format.DateTimeFormatter


case class EventosMes(data: LocalDate, eventosDia: Seq[EventosDia]) {

  def mesFmt(): String = data.format(DateTimeFormatter.ofPattern("MMMM"))

}
