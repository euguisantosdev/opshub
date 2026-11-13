package dev.opshub.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Cálculo de SLA, sem dependência de framework (funções puras, testáveis sem banco).
 *
 * <p>Regras, derivadas do OpsDesk original:
 * <ul>
 *   <li>o relógio corre desde a criação;</li>
 *   <li>pausa enquanto o chamado aguarda o usuário e para quando é resolvido;</li>
 *   <li>"em risco" quando restam menos de 25% do prazo; "estourado" quando o prazo acabou.</li>
 * </ul>
 */
public final class SlaCalculator {

    /** Fração do prazo abaixo da qual o chamado entra em risco. */
    public static final double RISK_FRACTION = 0.25;

    private SlaCalculator() {
    }

    /** Dados mínimos necessários para calcular o SLA. */
    public record Input(Priority priority, TicketStatus status, Instant createdAt, Instant resolvedAt,
                        Instant pausedAt, long pausedSeconds) {
    }

    /** Resultado do cálculo. */
    public record Snapshot(long totalSeconds, long elapsedSeconds, long remainingSeconds, SlaState state,
                           boolean running) {
    }

    public static Snapshot calculate(Input in, Instant now) {
        long total = in.priority().slaTotal().toSeconds();
        Instant end = in.resolvedAt() != null ? in.resolvedAt() : now;
        long gross = Duration.between(in.createdAt(), end).toSeconds();
        long paused = in.pausedSeconds();
        if (in.pausedAt() != null && in.resolvedAt() == null) {
            paused += Duration.between(in.pausedAt(), now).toSeconds();
        }
        long elapsed = Math.max(0, gross - paused);
        long remaining = total - elapsed;

        SlaState state;
        if (remaining < 0) {
            state = SlaState.ESTOURADO;
        } else if (in.status() != TicketStatus.RESOLVIDO && remaining < RISK_FRACTION * total) {
            state = SlaState.RISCO;
        } else {
            state = SlaState.OK;
        }
        return new Snapshot(total, elapsed, remaining, state, in.status().clockRuns());
    }
}
