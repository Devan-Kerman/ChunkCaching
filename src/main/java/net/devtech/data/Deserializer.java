package net.devtech.data;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * deserializes chunks of data to a stream
 * @param <C> chunk type
 * @param <S> input stream type
 * @param <A> extra arg type
 */
public interface Deserializer<C, S extends InputStream, A> {
	/**
	 * deserialize a chunk from the input stream, do not call this method!
	 * @param stream the stream to read a chunk from
	 * @param args extra information that the chunk needs to initialize
	 * @return a new chunk read from the output stream
	 */
	C read(S stream, A args) throws IOException;

	default C deserialize(InputStream inputStream, A args) throws IOException {
		return read(newStream(inputStream), args);
	}

	/**
	 * create a new stream from the one given
	 * @param stream the input stream
	 * @return a stream that reads from the specified input stream
	 */
	S newStream(InputStream stream);

	default C read(byte[] input, A args) throws IOException {
		ByteArrayInputStream inputStream = new ByteArrayInputStream(input);
		return read(newStream(inputStream), args);
	}
}
