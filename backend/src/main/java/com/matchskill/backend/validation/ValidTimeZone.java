package com.matchskill.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The field must be a real IANA time zone id, e.g. America/Sao_Paulo. */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidTimeZoneValidator.class)
public @interface ValidTimeZone {

    String message() default "must be a valid IANA time zone id";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
