package me.jezza.restbolt.form;

import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.NEW;
import static me.jezza.restbolt.Internals.*;

import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.StandardCharsets;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jezza
 */
public final class UrlEncoded {
	private static final Logger log = LoggerFactory.getLogger(UrlEncoded.class);

	public static final String PUBLISHER_PATH = "me.jezza.restbolt.form.UrlEncoded.buildURLEncoded";

	private UrlEncoded() {
		throw new IllegalStateException();
	}

	public static void buildURLEncoded(Method method, MethodVisitor impl, String[] names, int[] types, int max) {
		// [Builder]

		impl.visitInsn(DUP);
		impl.visitLdcInsn("Content-Type");
		impl.visitLdcInsn("application/x-www-form-urlencoded");

		impl.visitMethodInsn(INVOKEINTERFACE, REQUEST_BUILDER_INTERNAL, "header", "(Ljava/lang/String;Ljava/lang/String;)" + REQUEST_BUILDER_DESCRIPTOR, true);

		// new StringBuilder(32)
		impl.visitTypeInsn(NEW, Type.getInternalName(StringBuilder.class));
		impl.visitInsn(DUP);
		impl.visitLdcInsn(32);
		impl.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(StringBuilder.class), "<init>", "(I)V", false);
		// [Builder, StringBuilder]

		for (int i = 0, l = types.length; i < l; i++) {
			int type = types[i];
			if ((type & META_MASK) == BODY) {
				int sort = (type & SORT_MASK) >> SORT_SHIFT;
				int slot = (type & SLOT_MASK) >> SLOT_SHIFT;

				if (sort == ARRAY || sort == OBJECT || sort == MAP || sort == LIST) {
					log.warn("Support not yet added :: " + sort);
					continue;
				}
				// We abuse that form body parsers split on ampersands. (That way we don't have to track which body part is the last...)
				impl.visitLdcInsn('&' + URLEncoder.encode(names[i], StandardCharsets.UTF_8) + '=');
				impl.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(StringBuilder.class), "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

				impl.visitVarInsn(op(ILOAD, sort), slot);
				if (sort == STRING) {
					impl.visitFieldInsn(GETSTATIC, STANDARD_CHARSETS_INTERNAL, "UTF_8", CHARSET_DESCRIPTOR);
					impl.visitMethodInsn(INVOKESTATIC, URL_ENCODER_INTERNAL, "encode", "(Ljava/lang/String;Ljava/nio/charset/Charset;)Ljava/lang/String;", false);
					impl.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(StringBuilder.class), "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
				} else {
					char character = PRIMITIVE_DESCRIPTORS.charAt(sort);
					impl.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(StringBuilder.class), "append", "(" + character + ")Ljava/lang/StringBuilder;", false);
				}
			}
		}

		// [Builder, StringBuilder]
		impl.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(StringBuilder.class), "toString", "()Ljava/lang/String;", false);
		// [Builder, String]

		impl.visitMethodInsn(INVOKESTATIC, Type.getInternalName(BodyPublishers.class), "ofString", "(Ljava/lang/String;)" + PUBLISHER_DESCRIPTOR, false);
		// [Builder, Publisher]
	}
}
