package me.jezza.restbolt.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import me.jezza.restbolt.RestBolt;

/**
 * @author Jezza
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CUSTOM {
	String verb();

	String value();

	String publisher() default RestBolt.PUBLISHER_NO_BODY;
}
