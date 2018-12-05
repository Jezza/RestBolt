package me.jezza.restbolt;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;

import me.jezza.restbolt.annotations.GET;
import me.jezza.restbolt.annotations.Header;
import me.jezza.restbolt.annotations.Path;
import me.jezza.restbolt.annotations.Query;
import me.jezza.restbolt.annotations.RestService;

/**
 * @author Jezza
 */
public final class Main {
	private static final Lookup LOOKUP = MethodHandles.lookup();

	private static final Service SERVICE = RestBolt.bind("http://localhost:8080", Service.class, LOOKUP);

	private Main() {
		throw new IllegalStateException();
	}

	public interface Service extends RestService {
		@GET("/{value}")
		CompletableFuture<?> transmit(@Path("value") String path, @Header("*") Map<String, String> headerMap, @Query("first, middle?, last?name") Map<String, String> queryMap) throws SyncException;
	}

	public static void main(String[] args) {
		// @TODO Jezza - 24 Nov. 2018:
		// Body handlers (Other than String and discarding...)
		// Map types
		// List types
		// Array types
		// StringBuilder optimisation -> new StringBuilder(32).append("/ping").toString();

		Map<String, String> queries = new HashMap<>();
		Map<String, String> headers = new HashMap<>();

		try {
			SERVICE.transmit("poke", headers, queries);
		} catch (SyncException ignored) {
		}
		try {
			SERVICE.transmit("prod", headers, queries);
		} catch (SyncException e) {
			e.printStackTrace();
		}
	}

	public static final class ServiceImpl { // implements Connection
		private final URI host;
		private volatile HttpClient client;

		ServiceImpl(URI host) {
			this.host = host;
		}


		private HttpClient client() {
			HttpClient client = this.client;
			if (client == null) {
				synchronized (this) {
					client = this.client;
					if (client == null) {
						this.client = client = HttpClient.newBuilder()
								.build();
					}
				}
			}
			return client;
		}
//
		public void transmit(String path, Map<String, String> headerMap, @Query("first,last") Map<String, String> queryMap) throws SyncException {
			StringBuilder b = new StringBuilder(32);
			// %path%
			b.append("/users/");
			b.append(path);
			b.append("/name");
			// %path%

			// %query%
			// fixed:append
			{
				b.append("?sort=asc");
				b.append("&a=").append(queryMap.get("a"));
				b.append("&b=").append(queryMap.get("b"));
			}
			// fixed:first
			{
				b.append("?a=").append(queryMap.get("a"));
				b.append("&b=").append(queryMap.get("b"));
			}

			// @Query("first, middle?, last?name") Map<String, String> queryMap
			{
				b.append("?a=").append(queryMap.get("first"));
				String value = queryMap.get("middle");
				if (value != null) {
					b.append('?');
//					for (String s : value) {
//						b.append("a=").append(s);
//					}
				}
			}

			// wildcard:append
			{
				b.append("?sort=asc");
				Iterator<Entry<String, String>> it = queryMap.entrySet().iterator();
				while (it.hasNext()) {
					Entry<String, String> entry = it.next();
					String key = entry.getKey();
					String value = entry.getValue();
					b.append('&').append(key).append('=').append(value);
				}
			}

			// wildcard:first
			{
				Iterator<Entry<String, String>> it = queryMap.entrySet().iterator();
				if (it.hasNext()) {
					Entry<String, String> entry = it.next();
					String key = entry.getKey();
					String value = entry.getValue();
					b.append('?').append(key).append('=').append(value);
					while (it.hasNext()) {
						entry = it.next();
						key = entry.getKey();
						value = entry.getValue();
						b.append('&').append(key).append('=').append(value);
					}
				}
			}

			// %query%

			URI uri = host.resolve(b.toString());

			// %request%
			Builder builder = HttpRequest.newBuilder(uri);
			// %request%

			// %header%
//			builder.header("Accept", "application/json");
//			builder.header("Auth", auth);
			// %header%

			// %publisher%
			BodyPublisher publisher = BodyPublishers.noBody();
			// method == GET
			//   ? BodyPublishers.noBody()
			//   : !!!!!publishers.get(String);!!!!!
			// %publisher%

			builder.method("GET", publisher);

			HttpRequest request = builder.build();

			// %handler%
			BodyHandler<Void> handler = BodyHandlers.discarding();
			// %handler%

			// %return%sync
			try {
				HttpResponse<Void> response = client().send(request, handler);
//				return response;
			} catch (IOException | InterruptedException e) {
//				throw new SyncException(e);
			}
			// %return%?sync

			// %return%?async
			CompletableFuture<HttpResponse<Void>> future = client().sendAsync(request, handler);
//			return future;
			// %return%?async
		}
	}
}
