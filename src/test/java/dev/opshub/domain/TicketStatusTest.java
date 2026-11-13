package dev.opshub.domain;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static dev.opshub.domain.TicketStatus.AGUARDANDO;
import static dev.opshub.domain.TicketStatus.ATENDIMENTO;
import static dev.opshub.domain.TicketStatus.NOVO;
import static dev.opshub.domain.TicketStatus.RESOLVIDO;
import static org.assertj.core.api.Assertions.assertThat;

class TicketStatusTest {

    static Stream<Arguments> transitions() {
        return Stream.of(
                Arguments.of(NOVO, ATENDIMENTO, true),
                Arguments.of(NOVO, AGUARDANDO, false),
                Arguments.of(NOVO, RESOLVIDO, false),
                Arguments.of(ATENDIMENTO, AGUARDANDO, true),
                Arguments.of(ATENDIMENTO, RESOLVIDO, true),
                Arguments.of(ATENDIMENTO, NOVO, false),
                Arguments.of(AGUARDANDO, ATENDIMENTO, true),
                Arguments.of(AGUARDANDO, RESOLVIDO, false),
                Arguments.of(RESOLVIDO, ATENDIMENTO, false),
                Arguments.of(RESOLVIDO, NOVO, false));
    }

    @ParameterizedTest(name = "{0} -> {1} permitido? {2}")
    @MethodSource("transitions")
    @DisplayName("tabela de transições de estado")
    void tabela(TicketStatus from, TicketStatus to, boolean allowed) {
        assertThat(from.canGoTo(to)).isEqualTo(allowed);
    }

    @ParameterizedTest
    @MethodSource("clockStates")
    @DisplayName("o relógio só corre em 'novo' e 'em atendimento'")
    void relogio(TicketStatus status, boolean runs) {
        assertThat(status.clockRuns()).isEqualTo(runs);
    }

    static Stream<Arguments> clockStates() {
        return Stream.of(Arguments.of(NOVO, true), Arguments.of(ATENDIMENTO, true),
                Arguments.of(AGUARDANDO, false), Arguments.of(RESOLVIDO, false));
    }
}
