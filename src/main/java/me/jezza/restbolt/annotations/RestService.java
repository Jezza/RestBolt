package me.jezza.restbolt.annotations;

import java.net.http.HttpClient;

/**
 * @author Jezza
 */
public interface RestService {
	HttpClient client();
}
