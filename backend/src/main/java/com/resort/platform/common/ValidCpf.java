package com.resort.platform.common;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** CPF com ou sem pontuação, validado pelos dígitos verificadores. Vazio e nulo são aceitos. */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidCpf.Validator.class)
public @interface ValidCpf {

    String message() default "CPF inválido.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidCpf, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            String digits = Cpf.normalize(value);
            return digits == null || Cpf.isValid(digits);
        }
    }
}
