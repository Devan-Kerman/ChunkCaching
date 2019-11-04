package net.devtech.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Function;

/**
 * serializes chunks of data to a stream
 * @param <C> chunk type
 * @param <S> output stream type
 */
public interface Serializer<C, S extends OutputStream> {

	/**
	 * serialize the chunk to the output stream
	 * @param chunk the chunk
	 * @param output the output
	 */
	default void serialize(C chunk, OutputStream output) throws IOException {
		write(chunk, newStream(output));
	}

	/**
	 * do not call this method, only implement it
	 */
	void write(C chunk, S output) throws IOException;

	/**
	 * creates an new stream that writes to the given output stream
	 * @param outputStream the given outputstream
	 * @return a new stream
	 */
	S newStream(OutputStream outputStream);

	/**
	 * serializes the chunk to a newly created byte array
	 * @param chunk the chunk
	 * @return a newly allocated and populated byte array
	 */
	default byte[] serialize(C chunk) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		serialize(chunk, newStream(out));
		return out.toByteArray();
	}
}
