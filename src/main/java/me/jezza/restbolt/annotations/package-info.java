/**
 * Ok, so there's not really a better place to put all of this information save on each and every annotation,
 * but I feel like it's gonna be saying a lot of the same stuff..
 * Now that I think about it, here is probably fine.
 * <p>
 * <p>
 * This specifically is related to the publisher stuff.
 * I'll add more information either here, or in a README.md, but I'm adding it here for consistency sake.
 * <p>
 * So, publisher shit:
 * <p>
 * Assuming I've already managed to explain the general concept, annotate interface method with annotation that corresponds to verb to want to execute.
 * Good.
 * Simple.
 * Now, some of these annotations take a body.
 * Such as POST or PUT.
 * These Body parts need to be serialised and how that is defined is through this "publisher" value on the annotation.
 * <p>
 * This value is basically just a "target" for RestBolt.
 * Take, for example, POST's default value. (At the time of writing this, it's the URL_ENCODED impl)
 * This string contains 2 parts.
 * The class name and the method name.
 * The signature itself is actually statically defined: {@link me.jezza.restbolt.RestBolt#PUBLISHER_FACTORY_SIGNATURE}
 * RestBolt will try its best to load the class, and then locate the method.
 * The method obviously needs to match the given signature.
 * <p>
 * Once located, the method will be invoked and expected to perform 1 task.
 * Construct the publisher.
 * <p>
 * You will receive a MethodVisitor, which is the core component.
 * This is the method implementation itself.
 * Be careful, as one wrong step will break things, you're writing bytecode after all.
 * <p>
 * So, on the stack ready for you is the {@link java.net.http.HttpRequest.Builder}.
 * (There's obviously more on the stack, because I'm a lazy fuck and don't bother using locals all that much. I should probably go through it and improve it...)
 * So, you have the request builder on the stack.
 * In the factory, you have the impl, the method itself, the types, the names, and the slotMax.
 * <p>
 * The impl is obviously the MethodVisitor and is used to write the method implementation.
 * The method just a {@link java.lang.reflect.Method} that is the interface's method.
 * The types is a bit more complex and I can't be fucked explaining it yet... (TL;DR: it contains information about the method parameters. Flags, slot indices, and typing information)
 * The names is kind of an aux data structure that the types array uses. (If a type is flagged as a Body, the value inside of the array at that location is the constant that was written.)
 * And the slotMax is the highwater mark of the parameters. This means that if you want to store local variables do so ABOVE this mark. (eg, ASTORE slotMax + 1).
 * If you don't do that, you're gonna stomp on the parameters that were given to the method. Potentially breaking things.
 * <p>
 * In this factory, you have one job.
 * Place a {@link java.net.http.HttpRequest.BodyPublisher} onto the stack.
 * That's it.
 * Using all of the information I give you, create a BodyPublisher that does what is needed.
 * Don't touch anything else on the stack. (eg, don't remove the builder, as we still need that...)
 * <p>
 * You can take a look at {@link me.jezza.restbolt.RestBolt#buildNoBody(java.lang.reflect.Method, org.objectweb.asm.MethodVisitor, java.lang.String[], int[], int)} for a simple impl
 * or {@link me.jezza.restbolt.RestBolt#buildURLEncoded(java.lang.reflect.Method, org.objectweb.asm.MethodVisitor, java.lang.String[], int[], int)} for a more complex one.
 *
 * @author Jezza
 */
package me.jezza.restbolt.annotations;