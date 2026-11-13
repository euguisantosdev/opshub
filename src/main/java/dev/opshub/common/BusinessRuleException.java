package dev.opshub.common;

/** Violação de uma regra de negócio (transição inválida, evidência ausente etc.). */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
