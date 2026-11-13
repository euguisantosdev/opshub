package dev.opshub.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Funções de apresentação usadas pelos templates (disponível como {@code @fmt}). */
@Component("fmt")
public class ViewHelper {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.of("pt", "BR"));
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.of("pt", "BR"));

    private final Clock clock;

    public ViewHelper(Clock clock) {
        this.clock = clock;
    }

    /** "1 h 23 min", "45 min", "-12 min". */
    public String duration(long seconds) {
        long abs = Math.abs(seconds);
        long h = abs / 3600;
        long m = (abs % 3600) / 60;
        String text;
        if (h > 0) {
            text = h + " h" + (m > 0 ? " " + m + " min" : "");
        } else if (m > 0) {
            text = m + " min";
        } else {
            text = abs + " s";
        }
        return seconds < 0 ? "-" + text : text;
    }

    public String dateTime(Instant instant) {
        return instant == null ? "—" : DATE_TIME.format(instant.atZone(ZONE));
    }

    public String fullDateTime(Instant instant) {
        return instant == null ? "—" : FULL.format(instant.atZone(ZONE));
    }

    /** "há 5 min", "há 3 h", "há 2 d". */
    public String ago(Instant instant) {
        if (instant == null) {
            return "nunca";
        }
        long s = Math.max(0, Duration.between(instant, Instant.now(clock)).toSeconds());
        if (s < 60) {
            return "agora";
        }
        if (s < 3600) {
            return "há " + s / 60 + " min";
        }
        if (s < 86400) {
            return "há " + s / 3600 + " h";
        }
        return "há " + s / 86400 + " d";
    }

    public long maxOf(List<Integer> values) {
        return values == null ? 0 : values.stream().mapToLong(Integer::longValue).max().orElse(0);
    }

    /** Altura percentual (mínimo de 4%) de uma barra em relação ao maior valor da série. */
    public int barPercent(long value, long max) {
        if (max <= 0 || value <= 0) {
            return 0;
        }
        return (int) Math.max(4, Math.round(100.0 * value / max));
    }

    public String percent(Double value) {
        return value == null ? "—" : String.format(Locale.of("pt", "BR"), "%.1f%%", value);
    }

    public String minutes(Double value) {
        if (value == null) {
            return "—";
        }
        return duration(Math.round(value * 60));
    }
}
