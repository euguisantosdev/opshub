package dev.opshub.common;

/** Conflito de estado ou de versão (alteração concorrente, nome duplicado). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
