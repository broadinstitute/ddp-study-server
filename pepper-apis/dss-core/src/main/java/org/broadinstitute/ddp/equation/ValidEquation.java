package org.broadinstitute.ddp.equation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.ElementType;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = EquationValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEquation {
    String message() default "Invalid expression";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
