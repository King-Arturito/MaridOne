package org.example.maridone.annotation;

import org.example.maridone.enums.Position;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BulkNotify {
    String message();
    String importance() default "LOW";
    Position targetRole();
}
