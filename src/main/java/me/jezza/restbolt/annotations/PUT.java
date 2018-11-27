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
public @interface PUT {
	String value();

	String publisher() default RestBolt.PUBLISHER_URL_ENCODED;
}
