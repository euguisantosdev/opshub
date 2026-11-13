package dev.opshub.web;

import dev.opshub.security.AppUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Disponibiliza o usuário logado ({@code me}) para todas as telas. */
@ControllerAdvice(basePackageClasses = WebController.class)
public class GlobalModelAdvice {

    public record Me(String displayName, String roleLabel, String role, String initials, boolean analyst,
                     boolean manager, boolean canSimulate) {
    }

    @ModelAttribute("me")
    public Me me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserDetails d) {
            String[] parts = d.displayName().trim().split("\\s+");
            String initials = parts.length == 1
                    ? parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase()
                    : ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
            return new Me(d.displayName(), d.role().label(), d.role().name(), initials, d.role().isAnalyst(),
                    d.role() == dev.opshub.domain.Role.GESTOR,
                    d.role() == dev.opshub.domain.Role.GESTOR || d.role() == dev.opshub.domain.Role.ANALISTA_N2);
        }
        return null;
    }
}
