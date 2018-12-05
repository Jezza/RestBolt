/**
 * @author Jezza
 */
module rest.bolt {
	requires java.net.http;
	requires org.objectweb.asm;
	requires slf4j.api;

	exports me.jezza.restbolt;
	exports me.jezza.restbolt.annotations;
}