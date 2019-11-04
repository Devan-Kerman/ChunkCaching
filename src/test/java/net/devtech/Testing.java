package net.devtech;

import net.devtech.chunk2d.PrioritisedFile2DChunkCache;
import net.devtech.chunk2d.PrioritisedRegionFile2DChunkCache;
import net.devtech.data.Deserializer;
import net.devtech.data.Serializer;
import java.awt.Point;
import java.io.*;
import java.util.Random;

public class Testing {
	public static void main(String[] args) throws IOException {
		File folder = new File("test");
		folder.mkdirs();
		PrioritisedRegionFile2DChunkCache<Chunk> chunks = new PrioritisedRegionFile2DChunkCache<>(folder, Chunk::new, new Deserializer<Chunk, DataInputStream, Point>() {
			@Override
			public Chunk read(DataInputStream stream, Point args) throws IOException {
				Chunk chunk = new Chunk(args.x, args.y);
				chunk.setValue(stream.readInt());
				return chunk;
			}

			@Override
			public DataInputStream newStream(InputStream stream) {
				return new DataInputStream(stream);
			}
		}, new Serializer<Chunk, DataOutputStream>() {
			@Override
			public void write(Chunk chunk, DataOutputStream output) throws IOException {
				output.writeInt(chunk.getValue());
			}

			@Override
			public DataOutputStream newStream(OutputStream outputStream) {
				return new DataOutputStream(outputStream);
			}
		}, 3, 1, 2);
		Random random = new Random(0);
		for(int x = 0; x < 50; x++)
			chunks.get(random.nextInt(), random.nextInt());
		Runtime.getRuntime().addShutdownHook(new Thread(() ->{
			try {
				chunks.saveAll(true);
				System.out.println("Chunks saved!");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}));
	}
}
