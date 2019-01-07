package me.jezza.restbolt.form.multipart;

import java.io.IOException;

/**
 * @author Jezza
 */
public interface MultipartSegment {
	int read(byte[] b, int off, int len) throws IOException;

//	static MultipartPair create(String name, boolean value) {
//		System.out.println(name + ':' + value);
//	}
//
//	static MultipartPair create(String name, char value) {
//		System.out.println(name + ':' + value);
//	}
//
//	static MultipartPair create(String name, byte value) {
//		System.out.println(name + ':' + value);
//	}
//
//	static MultipartPair create(String name, short value) {
//		System.out.println(name + ':' + value);
//	}
//
//	static MultipartPair create(String name, int value) {
//		System.out.println(name + ':' + value);
//	}
//
//	static MultipartPair create(String name, float value) {
//		System.out.println(name + ':' + value);
//	}
//
//	static MultipartPair create(String name, long value) {
//		System.out.println(name + ':' + value);
//	}
//
//	static MultipartPair create(String name, double value) {
//		System.out.println(name + ':' + value);
//	}
//
//	static MultipartPair create(String name, Object[] value) {
//		System.out.println(name + ':' + Arrays.toString(value));
//	}
//
//	static MultipartPair create(String name, Object value) {
//		System.out.println(name + ':' + value);
//	}
//
//	static MultipartPair create(String name, String value) {
//		System.out.println(name + ':' + value);
//	}
//
//	static MultipartPair create(String name, Map<?, ?> value) {
//		System.out.println(name + ':' + value);
//	}
//
//	static MultipartPair create(String name, List<?> value) {
//		System.out.println(name + ':' + value);
//	}
//
//	static MultipartPair create(String name, File value) {
//		System.out.println(name + ':' + value);
//	}
//
//	static MultipartPair create(String name, InputStream value) {
//		System.out.println(name + ':' + value);
//	}
}
