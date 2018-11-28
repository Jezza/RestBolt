So, the basic idea behind this isn't to revolutionise REST clients, or arguably even improve them.
The main goal was to... eh.... I've forgotten at this point, but it was something like:
"I want to use the new Java 11 client stuff, but I like not having to write code."
I knew of Retrofit and knew that it mapped a collection of endpoints against an interface.
I thought the idea was neat, and brought in my "expertise" of the JVM, and thought I could generate code for the methods, based off of their declarations.
And it works really well.
A preliminary test showed that it outperformed okHttp3.
I'll do a proper benchmark when this is a bit more evolved, and I guess if there's enough interest.
From my shitty testing, I was able to send 10,000 requests in a second.
Rapidoid started rejecting the requests up around 30,000, so that might have something to do with it.

Basically, you start with something like this:

```java
interface Service {
	// A method declared with a void or wildcard (eg, HttpResponse<?>) discards the response's body.
	@GET("/ping")
	void ping0() throws SyncException;

	// SyncException replaces IOException, because the HttpClient can technically interrupt as well, so this exception covers both.
	@GET("/ping")
	HttpResponse<?> ping1() throws SyncException;

	// All of the methods above are synchronous, to mark a method as async, you need to return a CompletableFuture.
	@GET("/ping")
	CompletableFuture<HttpResponse<?>> ping2();

	// A bit more extreme of an example, but this basically does all of the stuff you'd expect.
	@POST("/users/{id}/create")
	CompletableFuture<HttpResponse<?>> list(@Path("id") String path, @Query("admin") boolean admin, @Body("first_name") String firstName, @Body("last_name") String lastName);
}
```

Side-note: All bodies are currently published with a URL encoded scheme by default.
If you wish to see more implementations you can either wait until they get implemented by someone, or do them yourself.
(eg, I want to implement multipart, so that'll probably be implemented at some point)
If you want to implement it yourself, go take a look at the bottom of this document.
It describes how you can implement a publisher.

Another side-note:
Currently, the only handled return type is String.
Support still needs to come, so hold your horses...
I'll get around to it at some point...

```java
class Main {
	// These implementations are thread safe. 
	private static final Service SERVICE = RestBolt.bind("http://localhost:8080", Service.class, MethodHandles.lookup());

	public static void main(String[] args) throws SyncException {
		// You can call methods on them, blah, all the usual magic stuff.
		SERVICE.ping();
	}
}
```

That's basically all that's remotely interesting for now.
Or at least, all that I can remember.
If you're interested in what it generates behind the scenes, there's two methods.
First, you can take a look in "Main".
That was what I based my idea off.
I wrote the first implementation by hand, and got an idea of what I needed to do.

The second way is a bit more "advance", and that's to use the debug option inside of RestBolt itself.
There's a property at the top of the class that you can define.
Point it to a folder, and it'll write the generated class to it, then just use "javap" to take a peek at it. My recommended flags are "-p -v -c".


The following part is more of a note to myself and anyone wanting to implement a custom body serialiser as there's some hoops you have to jump through.
So, if you're not interested in that, you can stop reading here...

---

Assuming I've already managed to explain the general concept, annotate interface method with annotation that corresponds to verb to want to execute.
Good.
Simple.
Now, some of these annotations take a body.
Such as POST or PUT.
These Body parts need to be serialised and how that is defined is through this "publisher" value on the annotation.

This value is basically just a "target" for RestBolt.
Take, for example, POST's default value. (At the time of writing this, it's the URL_ENCODED impl)
This string contains 2 parts.
The class name and the method name.
The signature itself is actually statically defined: {@link me.jezza.restbolt.RestBolt#PUBLISHER_FACTORY_SIGNATURE}
RestBolt will try its best to load the class, and then locate the method.
The method obviously needs to match the given signature.

Once located, the method will be invoked and expected to perform 1 task.
Construct the publisher.

You will receive a MethodVisitor, which is the core component.
This is the method implementation itself.
Be careful, as one wrong step will break things, you're writing bytecode after all.

So, on the stack ready for you is the {@link java.net.http.HttpRequest.Builder}.
(There's obviously more on the stack, because I'm a lazy fuck and don't bother using locals all that much. I should probably go through it and improve it...)
So, you have the request builder on the stack.
In the factory, you have the impl, the method itself, the types, the names, and the slotMax.

The impl is obviously the MethodVisitor and is used to write the method implementation.
The method just a {@link java.lang.reflect.Method} that is the interface's method.
The types is a bit more complex and I can't be fucked explaining it yet... (TL;DR: it contains information about the method parameters. Flags, slot indices, and typing information)
The names is kind of an aux data structure that the types array uses. (If a type is flagged as a Body, the value inside of the array at that location is the constant that was written.)
And the slotMax is the highwater mark of the parameters. This means that if you want to store local variables do so ABOVE this mark. (eg, ASTORE slotMax + 1).
If you don't do that, you're gonna stomp on the parameters that were given to the method. Potentially breaking things.

In this factory, you have one job.
Place a {@link java.net.http.HttpRequest.BodyPublisher} onto the stack.
That's it.
Using all of the information I give you, create a BodyPublisher that does what is needed.
Don't touch anything else on the stack. (eg, don't remove the builder, as we still need that...)

You can take a look at {@link me.jezza.restbolt.RestBolt#buildNoBody(java.lang.reflect.Method, org.objectweb.asm.MethodVisitor, java.lang.String[], int[], int)} for a simple impl
or {@link me.jezza.restbolt.RestBolt#buildURLEncoded(java.lang.reflect.Method, org.objectweb.asm.MethodVisitor, java.lang.String[], int[], int)} for a more complex one.
