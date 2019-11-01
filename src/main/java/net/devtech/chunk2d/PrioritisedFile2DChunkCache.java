package net.devtech.chunk2d;

import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.devtech.data.Deserializer;
import net.devtech.data.PersistentCache;
import net.devtech.data.Serializer;
import net.devtech.util.Tracker;
import java.awt.Point;
import java.io.*;
import java.util.Iterator;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class PrioritisedFile2DChunkCache<C extends Located2D> implements Prioritized2DChunkCache<C, PrioritisedFile2DChunkCache.Priority>, PersistentCache<C> {
	private final Long2ObjectFunction<File> getFile;
	private final Serializer<C, ?> serializer;
	private final Long2ObjectMap<Tracker<C>> frequentCache;
	private final Long2ObjectMap<C> loaded = new Long2ObjectOpenHashMap<>();
	private final Long2ObjectLinkedOpenHashMap<C> unloadedCache;
	private final ChunkFunction2D<C> chunkFunction2D;
	private final int inMemorySize;
	private final int frequentSize;
	// current accesses
	private int accessCounter;

	public PrioritisedFile2DChunkCache(File folder, ChunkFunction2D<C> chunkSupplier, Deserializer<C, ?, Point> deserializer, Serializer<C, ?> serializer, int frequentSize, int inMemorySize) {
		this.serializer = serializer;
		this.frequentSize = frequentSize;
		this.inMemorySize = inMemorySize;
		getFile = l -> new File(folder, l + ".chunkdata");
		this.chunkFunction2D = (x, y) -> {
			File file = getFile.apply(key(x, y));
			if (file.exists()) try (FileInputStream input = new FileInputStream(file)) {
				return deserializer.deserialize(new GZIPInputStream(input), new Point(x, y));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			return chunkSupplier.newChunk(x, y);
		};
		frequentCache = new Long2ObjectOpenHashMap<>(frequentSize);
		unloadedCache = new Long2ObjectLinkedOpenHashMap<>(inMemorySize);
	}

	public PrioritisedFile2DChunkCache(File folder, ChunkFunction2D<C> chunkSupplier, Deserializer<C, ?, Point> deserializer, Serializer<C, ?> serializer) {
		this(folder, chunkSupplier, deserializer, serializer, 128, 1028);
	}

	@Override
	public C getNoPriority(int x, int y) {
		long key = key(x, y);
		C first = frequentCache.get(key).get();
		if (first == null) first = loaded.get(key);
		if (first == null) {
			try {
				first = getFromUnloadedNoPriority(x, y);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return first;
	}

	@Override
	public Priority getPriority(int x, int y) {
		long key = key(x, y);
		if (frequentCache.containsKey(key)) return Priority.FREQUENT;
		else if (loaded.containsKey(key)) return Priority.LOADED;
		else if (unloadedCache.containsKey(key)) return Priority.UNLOADED_CACHED;
		return Priority.UNLOADED;
	}

	@Override
	public void setPriority(int x, int y, Priority priority) {
		long key = key(x, y);
		C c = forceRemove(key);
		try {
			switch (priority) {
				case FREQUENT:
					frequentCache.put(key, new Tracker<>(c));
					break;
				case LOADED:
					loaded.put(key, c);
					break;
				case UNLOADED_CACHED:
					addToUnloaded(key, c);
					break;
				case UNLOADED:
					save(x, y, true);
					break;
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public C unload(int x, int y) {
		try {
			long key = key(x, y);
			C c = forceRemove(key);
			addToUnloaded(key, c);
			return c;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void set(int x, int y, C object) {
		long key = key(x, y);
		frequentCache.put(key, new Tracker<>(object));
		trimFrequents();
	}

	@Override
	public C get(int x, int y) {
		long key = key(x, y);
		C first = frequentCache.get(key).get();
		if (first == null) first = loaded.get(key);
		if (first == null) first = unloadedCache.getAndMoveToFirst(key);
		if (first == null) set(x, y, first = chunkFunction2D.newChunk(x, y));
		return first;
	}

	@Override
	public int size() {
		return frequentCache.size() + unloadedCache.size() + loaded.size();
	}

	@Override
	public Iterator<C> iterator() {
		ObjectIterator<Long2ObjectMap.Entry<Tracker<C>>> freq = Long2ObjectMaps.fastIterator(frequentCache);
		ObjectIterator<Long2ObjectMap.Entry<C>> load = Long2ObjectMaps.fastIterator(loaded);
		ObjectIterator<Long2ObjectMap.Entry<C>> ulad = Long2ObjectMaps.fastIterator(unloadedCache);

		return new Iterator<C>() {
			@Override
			public boolean hasNext() {
				return freq.hasNext() || load.hasNext() || ulad.hasNext();
			}

			@Override
			public C next() {
				if (freq.hasNext()) return freq.next().getValue().get();
				else if (load.hasNext()) return load.next().getValue();
				else if (ulad.hasNext()) return ulad.next().getValue();
				return null;
			}
		};
	}

	private long key(int x, int y) {
		return (long) x << 32 | y & 0xffffffffL;
	}

	private C forceRemove(long key) {
		C first = frequentCache.remove(key).get();
		if (first == null) first = loaded.remove(key);
		if (first == null) first = unloadedCache.remove(key);
		return first;
	}

	@Override
	public C save(int x, int y, boolean remove) throws IOException {
		long key = key(x, y);
		C c = remove ? forceRemove(key) : get(x, y);
		serialize(key, c);
		return c;
	}

	@Override
	public void saveAll(boolean remove) throws IOException {
		for (C c : this)
			save(c.getX(), c.getY(), remove);
	}

	private void serialize(long key, C c) {
		File file = getFile.apply(key);
		try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
			serializer.serialize(c, new GZIPOutputStream(fileOutputStream));
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	private C getFromUnloadedNoPriority(int x, int y) throws IOException {
		long key = key(x, y);
		C c = unloadedCache.get(key);
		if (c == null) {
			c = chunkFunction2D.newChunk(x, y);
			unloadedCache.put(key, c);
			trimUnloadedCacheOnce();
		}
		return c;
	}

	private C getFromUnloadedPriority(int x, int y) throws IOException {
		long key = key(x, y);
		C c = unloadedCache.getAndMoveToFirst(key);
		if (c == null) {
			c = chunkFunction2D.newChunk(x, y);
			unloadedCache.put(key, c);
			trimUnloadedCacheOnce();
		}
		return c;
	}

	private void addToUnloaded(long key, C chunk) throws IOException {
		unloadedCache.put(key, chunk);
		trimUnloadedCacheOnce();
	}

	private void trimFrequents() {
		trimFrequentOnce();
		if (accessCounter++ > frequentSize * 2) {
			ObjectIterator<Long2ObjectMap.Entry<Tracker<C>>> iterator = Long2ObjectMaps.fastIterator(frequentCache);
			Long2ObjectMap.Entry<Tracker<C>> curr;
			while ((curr = iterator.next()) != null) curr.getValue().reset();
			accessCounter = 0;
		}
	}

	private void trimFrequentOnce() {
		if (frequentCache.size() > frequentSize) {
			ObjectIterator<Long2ObjectMap.Entry<Tracker<C>>> iterator = Long2ObjectMaps.fastIterator(frequentCache);
			Long2ObjectMap.Entry<Tracker<C>> curr;
			Long2ObjectMap.Entry<Tracker<C>> lowest = null;
			int lowestVal = Integer.MAX_VALUE;

			while ((curr = iterator.next()) != null) if (curr.getValue().getAccesses() < lowestVal) lowest = curr;

			assert lowest != null : "Unable to trim frequent list!";

			long key = lowest.getLongKey();
			frequentCache.remove(key);
			loaded.put(key, lowest.getValue().get());
		}
	}


	private void trimUnloadedCacheOnce() throws IOException {
		if (unloadedCache.size() > inMemorySize) {
			C c = unloadedCache.removeLast();
			save(c.getX(), c.getY(), true);
		}
	}

	public enum Priority {
		FREQUENT, LOADED, UNLOADED_CACHED, UNLOADED
	}
}
