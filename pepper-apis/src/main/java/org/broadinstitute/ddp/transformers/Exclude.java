package org.broadinstitute.ddp.transformers;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation meant to mark fields that should be excluded from GSON serialization
 * Important! For this to work you will need to use an exclusion strategy that looks for
 * it.
 * @see org.broadinstitute.ddp.util.GsonUtil.ExcludeAnnotationStrategy
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Exclude {}

