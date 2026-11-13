package dev.opshub.ticket;

/** Publicado quando uma pessoa resolve um chamado ligado a um serviço (para o monitoramento reagir). */
public record TicketResolvedEvent(Long ticketId, Long serviceId) {
}
