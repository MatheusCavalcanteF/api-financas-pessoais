package com.finance.api.shared.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Limita o tamanho da string em bytes UTF-8 (não em caracteres).
 * Necessário para senhas: o BCrypt rejeita entradas acima de 72 bytes, e
 * caracteres acentuados ocupam mais de um byte.
 */
@Documented
@Constraint(validatedBy = MaxBytesValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxBytes {

	int value();

	String message() default "deve ter no máximo {value} bytes";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
