package dev.opshub.domain;

import dev.opshub.domain.SlaCalculator.Input;
import dev.opshub.domain.SlaCalculator.Snapshot;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SlaCalculatorTest {

    private static final Instant T0 = Instant.parse("2026-11-13T12:00:00Z");

    private static Input open(Priority priority, TicketStatus status) {
        return new Input(priority, status, T0, null, null, 0);
    }

    @ParameterizedTest(name = "{0} tem prazo de {1} horas")
    @CsvSource({"CRITICA,1", "ALTA,4", "MEDIA,8", "BAIXA,24"})
    @DisplayName("o prazo total depende da prioridade")
    void prazoPorPrioridade(Priority priority, long hours) {
        Snapshot s = SlaCalculator.calculate(open(priority, TicketStatus.NOVO), T0);
        assertThat(s.totalSeconds()).isEqualTo(Duration.ofHours(hours).toSeconds());
        assertThat(s.remainingSeconds()).isEqualTo(Duration.ofHours(hours).toSeconds());
        assertThat(s.state()).isEqualTo(SlaState.OK);
    }

    @Test
    @DisplayName("o relógio corre enquanto o chamado está em atendimento")
    void relogioCorre() {
        Snapshot s = SlaCalculator.calculate(open(Priority.ALTA, TicketStatus.ATENDIMENTO), T0.plus(Duration.ofHours(1)));
        assertThat(s.elapsedSeconds()).isEqualTo(3600);
        assertThat(s.remainingSeconds()).isEqualTo(3 * 3600);
        assertThat(s.running()).isTrue();
    }

    @Test
    @DisplayName("a pausa em 'aguardando usuário' não consome o prazo")
    void pausaNaoConsome() {
        // 30 min de atendimento e depois 2 h aguardando (pausa em andamento)
        Input in = new Input(Priority.ALTA, TicketStatus.AGUARDANDO, T0, null, T0.plus(Duration.ofMinutes(30)), 0);
        Snapshot s = SlaCalculator.calculate(in, T0.plus(Duration.ofMinutes(150)));
        assertThat(s.elapsedSeconds()).isEqualTo(30 * 60);
        assertThat(s.remainingSeconds()).isEqualTo(4 * 3600 - 30 * 60);
        assertThat(s.running()).isFalse();
    }

    @Test
    @DisplayName("o tempo de pausas já encerradas é descontado")
    void pausasAcumuladas() {
        Input in = new Input(Priority.ALTA, TicketStatus.ATENDIMENTO, T0, null, null, 2 * 3600);
        Snapshot s = SlaCalculator.calculate(in, T0.plus(Duration.ofHours(3)));
        assertThat(s.elapsedSeconds()).isEqualTo(3600);
    }

    @Test
    @DisplayName("abaixo de 25% do prazo restante o chamado entra em risco")
    void risco() {
        // 1 h de prazo: a partir de 45 min restam menos de 25%
        Snapshot ok = SlaCalculator.calculate(open(Priority.CRITICA, TicketStatus.ATENDIMENTO), T0.plus(Duration.ofMinutes(44)));
        Snapshot risco = SlaCalculator.calculate(open(Priority.CRITICA, TicketStatus.ATENDIMENTO), T0.plus(Duration.ofMinutes(46)));
        assertThat(ok.state()).isEqualTo(SlaState.OK);
        assertThat(risco.state()).isEqualTo(SlaState.RISCO);
    }

    @Test
    @DisplayName("passado o prazo o chamado fica estourado")
    void estourado() {
        Snapshot s = SlaCalculator.calculate(open(Priority.CRITICA, TicketStatus.NOVO), T0.plus(Duration.ofMinutes(61)));
        assertThat(s.state()).isEqualTo(SlaState.ESTOURADO);
        assertThat(s.remainingSeconds()).isNegative();
    }

    @Test
    @DisplayName("o prazo exato ainda não estoura")
    void prazoExato() {
        Snapshot s = SlaCalculator.calculate(open(Priority.CRITICA, TicketStatus.NOVO), T0.plus(Duration.ofHours(1)));
        assertThat(s.remainingSeconds()).isZero();
        assertThat(s.state()).isNotEqualTo(SlaState.ESTOURADO);
    }

    @Test
    @DisplayName("chamado resolvido dentro do prazo fica OK e o relógio para")
    void resolvidoNoPrazo() {
        Input in = new Input(Priority.ALTA, TicketStatus.RESOLVIDO, T0, T0.plus(Duration.ofHours(2)), null, 0);
        Snapshot s = SlaCalculator.calculate(in, T0.plus(Duration.ofDays(5)));
        assertThat(s.elapsedSeconds()).isEqualTo(2 * 3600);
        assertThat(s.state()).isEqualTo(SlaState.OK);
        assertThat(s.running()).isFalse();
    }

    @Test
    @DisplayName("chamado resolvido fora do prazo fica estourado")
    void resolvidoForaDoPrazo() {
        Input in = new Input(Priority.ALTA, TicketStatus.RESOLVIDO, T0, T0.plus(Duration.ofHours(5)), null, 0);
        assertThat(SlaCalculator.calculate(in, T0.plus(Duration.ofDays(1))).state()).isEqualTo(SlaState.ESTOURADO);
    }
}
