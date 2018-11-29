package me.jezza.restbolt;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import me.jezza.restbolt.annotations.Body;
import me.jezza.restbolt.annotations.GET;
import me.jezza.restbolt.annotations.Header;
import me.jezza.restbolt.annotations.POST;
import me.jezza.restbolt.annotations.Path;
import me.jezza.restbolt.annotations.Query;
import me.jezza.restbolt.annotations.RestService;

/**
 * @author Jezza
 */
public final class Main {
	private static final Lookup LOOKUP = MethodHandles.lookup();

	private static final Service SERVICE = RestBolt.bind("http://localhost:8080", Service.class, LOOKUP);
	private static final Connection CONNECTION = RestBolt.bind("http://localhost:8080", Connection.class, LOOKUP);

	private Main() {
		throw new IllegalStateException();
	}

	public interface Connection extends RestService {
		@GET("/{value}")
		void transmit(@Path("value") String path, @Header("*") Map<String, String> queryMap, @Query("*") Map<String, String> headerMap) throws SyncException;
	}

	public static void main(String[] args) throws SyncException {
		Map<String, String> queries = new HashMap<>();
		Map<String, String> headers = new HashMap<>();

		CONNECTION.transmit("poke", headers, queries);
		CONNECTION.transmit("prod", headers, queries);
	}

	public static void main0(String[] args) throws SyncException {
		// @TODO Jezza - 24 Nov. 2018:
		// Body handlers
		// Sync try-catch -> SyncException
		// StringBuilder optimisation -> new StringBuilder(32).append("/ping").toString();

//		Binder<Service> binder = RestBolt.binder(Service.class, MethodHandles.lookup());
//		Service service1 = binder.bind("http://localhost:8080");
//		Service service2 = binder.bind("http://localhost:8081");
//		Service service3 = binder.bind("http://localhost:8082");

//		CompletableFuture<?> future1 = service1.pingAsync();
//		CompletableFuture<?> future2 = service2.pingAsync();
//		CompletableFuture<?> future3 = service3.pingAsync();

		Service service = RestBolt.bind("http://localhost:8080", Service.class, MethodHandles.lookup());
//		int size = 100;
//		List<CompletableFuture<?>> futures = new ArrayList<>(size);
//		for (int i = 0; i < size; i++) {
//			futures.add(service.pingAsync());
//		}
//		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
//		System.out.println(service.count());

		System.out.println(service.touch(4L));
		System.out.println(service.touch(4L));
	}

	private static <S, T> CompletableFuture<Object> race(S[] services, Function<S, CompletableFuture<T>> function, Class<T> type) {
		CompletableFuture<?>[] futures = new CompletableFuture[services.length];
		for (int i = 0, l = services.length; i < l; i++) {
			futures[i] = function.apply(services[i]);
		}
		return CompletableFuture.anyOf(futures);
	}

	private static void spam(Service service, int count) throws SyncException {
		CompletableFuture<?>[] futures = new CompletableFuture[count];
		long start = System.nanoTime();
		for (int i = 0; i < count; i++) {
			futures[i] = service.createUser(i, "Jeremy", "Barrow");
		}
		long end = System.nanoTime();
		System.out.println("Time: " + (end - start) + " ns");
//
		start = System.nanoTime();
		int errors = 0;
		int ok = 0;
		for (CompletableFuture<?> future : futures) {
			try {
				future.join();
				ok++;
			} catch (Exception e) {
				errors++;
			}
		}
		end = System.nanoTime();
		System.out.println("Ok: " + ok);
		System.out.println("Errors: " + errors);
		System.out.println("Total: " + (ok + errors));
		System.out.println("Time: " + (end - start) + " ns");
		System.out.println("Done!");
		String size = service.size();
		System.out.println(size);
	}

//	public static final class ServiceImpl { // implements Service
//		private final URI host;
//		private volatile HttpClient client;
//
//		ServiceImpl(URI host) {
//			this.host = host;
//		}
//
//		private HttpClient client() {
//			HttpClient client = this.client;
//			if (client == null) {
//				synchronized (this) {
//					client = this.client;
//					if (client == null) {
//						this.client = client = HttpClient.newBuilder()
//								.build();
//					}
//				}
//			}
//			return client;
//		}
//
//		//		@Override
//		public String size() {
//			URI path = host.resolve("/size");
//			HttpRequest.Builder req = HttpRequest.newBuilder(path)
//					.method("GET", BodyPublishers.noBody());
//			BodyHandler<String> handler = BodyHandlers.ofString();
//			try {
//				return client().send(req.build(), handler)
//						.body();
//			} catch (IOException | InterruptedException e) {
//				return null;
//			}
//		}
//
//		//		@Override
//		public String name0(String id, String sort, String auth) {
//			StringBuilder b = new StringBuilder(32);
//			// %path%
//			b.append("/users/");
//			b.append(id);
//			b.append("/name");
//			// %path%
//
//			// %query%
//			b.append('?');
//
//			b.append("sort"); // = URLEncoder.encode("sort", StandardCharsets.UTF_8)
//			if (sort != null) {
//				b.append('=');
//				b.append(URLEncoder.encode(sort, StandardCharsets.UTF_8));
//			}
//			// %query%
//
//			URI uri = host.resolve(b.toString());
//
//			// %request%
//			Builder builder = HttpRequest.newBuilder(uri);
//			// %request%
//
//			// %header%
//			builder.header("Accept", "application/vnd.github.v3.full+json");
//			builder.header("User-Agent", "Retrofit-Sample-App");
//			builder.header("Auth", auth);
//			// %header%
//
//			// %publisher%
//			BodyPublisher publisher = BodyPublishers.noBody();
//			// method == GET
//			//   ? BodyPublishers.noBody()
//			//   : !!!!!publishers.get(String);!!!!!
//			// %publisher%
//
//			builder.method("GET", publisher);
//
//			HttpRequest request = builder.build();
//
//			// %handler%
//			BodyHandler<Void> handler = BodyHandlers.discarding();
//			// %handler%
//
//			// %return%sync
//			try {
//				HttpResponse<Void> response = client().send(request, handler);
////				return response;
//			} catch (IOException | InterruptedException e) {
////				throw new SyncException(e);
//			}
//			// %return%?sync
//
//			// %return%?async
//			CompletableFuture<HttpResponse<Void>> future = client().sendAsync(request, handler);
////			return future;
//			// %return%?async
//
//			return null;
//		}
//	}

	public interface Service {
//		@GET("/size")
//		String size();

		@GET("/users/{user}/name")
		@Header(value = "Accept", data = "application/vnd.github.v3.full+json")
		@Header(value = "User-Agent", data = "Rest-Bolt-UserAgent")
		CompletableFuture<HttpResponse<String>> name(@Path("user") String id, @Query("sort") String sort, @Header("Auth") String auth);

		@GET("/touch/{id}")
		String touch(@Path("id") long id) throws SyncException;

		@GET("/send?name=Jeremy")
		void sendJeremy(@Query("data") String data) throws SyncException;

		@GET("/send")
		void send(@Query("name") String name, @Query("data") String data) throws SyncException;

		@GET("/ping")
		void ping(@Header("User-Agent") String userAgent) throws SyncException;

		@GET("/size")
		String size() throws SyncException;

		@GET("/ping")
		CompletableFuture<?> pingAsync();

		@GET("/ping")
		void pingSync0() throws SyncException;

		@GET("/count")
		String count() throws SyncException;

		@GET("/ping")
		String pingSync1() throws SyncException;
//		HttpResponse<String> pingSync1() throws SyncException;

		@GET("/ping")
		HttpResponse<?> pingSync2() throws SyncException;

		@GET("/ping")
		HttpResponse<Void> pingSync3() throws SyncException;

		@GET("/asd")
		CompletableFuture<?> pingAsync2(@Query("cache") long test0, @Header("cache_updated") long test1);

		@POST("/users/{id}/create")
		CompletableFuture<?> createUser(@Path("id") long id, @Body("first") String first, @Body("last") String last);

//		@GET("/ping?timeout=10")
//		void test(@Header("*") Map<String, String> headers, @Query("test") Map<String, String> query, @Query("test") List<String> mQuery) throws SyncException;
	}
}
