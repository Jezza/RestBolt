package me.jezza.restbolt.form.multipart;

import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.NEW;
import static me.jezza.restbolt.Internals.*;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.function.Function;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

/**
 * @author Jezza
 */
public final class Multipart {
	public static final String PUBLISHER_PATH = "me.jezza.restbolt.form.multipart.Multipart.buildMultipart";

	private Multipart() {
		throw new IllegalStateException();
	}

	public static void buildMultipart(Method method, MethodVisitor impl, String[] names, int[] types, int max) {
		impl.visitInsn(DUP);
		impl.visitLdcInsn("Content-Type");
		impl.visitLdcInsn("multipart/form-data; boundary=boundary");

		impl.visitMethodInsn(INVOKEINTERFACE, REQUEST_BUILDER_INTERNAL, "header", "(Ljava/lang/String;Ljava/lang/String;)" + REQUEST_BUILDER_DESCRIPTOR, true);

		int count = 0;
		for (int i = 0, l = types.length; i < l; i++) {
			int type = types[i];
			if ((type & META_MASK) == BODY) {
				count++;
			}
		}

		impl.visitTypeInsn(NEW, Type.getInternalName(MultipartBuilder.class));
		impl.visitInsn(DUP);
		impl.visitLdcInsn(count);
		impl.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(MultipartBuilder.class), "<init>", "(I)V", false);

		Charset defaultCharset = method.getDeclaredAnnotation(Charset.class);
		
		Function<Charset, String> fetchCharset = in -> {
			if (in != null) {
				return in.value();
			} else if (defaultCharset != null) {
				return defaultCharset.value();
			}
			return null;
		};

		Parameter[] parameters = method.getParameters();
		for (int i = 0, l = types.length; i < l; i++) {
			int type = types[i];
			if ((type & META_MASK) == BODY) {
				int sort = (type & SORT_MASK) >> SORT_SHIFT;
				int slot = (type & SLOT_MASK) >> SLOT_SHIFT;
				String charset = fetchCharset.apply(parameters[i].getDeclaredAnnotation(Charset.class));

				impl.visitInsn(DUP);
				impl.visitLdcInsn(i);
				impl.visitLdcInsn(names[i]);
				impl.visitVarInsn(op(ILOAD, sort), slot);

				if (sort == ARRAY) {
					impl.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(MultipartBuilder.class), "add", "(ILjava/lang/String;[Ljava/lang/Object;)V", false);
				} else if (sort == OBJECT) {
					impl.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(MultipartBuilder.class), "add", "(ILjava/lang/String;Ljava/lang/Object;)V", false);
				} else if (sort == STRING) {
					impl.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(MultipartBuilder.class), "add", "(ILjava/lang/String;Ljava/lang/String;)V", false);
				} else if (sort == MAP) {
					impl.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(MultipartBuilder.class), "add", "(ILjava/lang/String;Ljava/util/Map;)V", false);
				} else if (sort == LIST) {
					impl.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(MultipartBuilder.class), "add", "(ILjava/lang/String;Ljava/util/List;)V", false);
				} else if (sort == FILE) {
					impl.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(MultipartBuilder.class), "add", "(ILjava/lang/String;Ljava/io/File;)V", false);
				} else if (sort == INPUT_STREAM) {
					impl.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(MultipartBuilder.class), "add", "(ILjava/lang/String;Ljava/io/InputStream;)V", false);
				} else {
					char character = PRIMITIVE_DESCRIPTORS.charAt(sort);
					impl.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(MultipartBuilder.class), "add", "(ILjava/lang/String;" + character + ")V", false);
				}
			}
		}

		impl.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(MultipartBuilder.class), "build", "()" + PUBLISHER_DESCRIPTOR, false);
	}
}
