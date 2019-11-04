package net.devtech.chunk2d;

import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.devtech.data.Deserializer;
import net.devtech.data.PersistentCache;
import net.devtech.data.Serializer;
import net.devtech.util.Tracker;
import java.awt.Point;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

// TODO region based file storage and per-region data :)
public class PrioritisedFile2DChunkCache<C extends Located2D> implements Prioritized2DChunkCache<C, PrioritisedFile2DChunkCache.Priority>, PersistentCache<C> {
	// key to file provider
	protected final Long2ObjectFunction<File> getFile;
	// serializer
	protected final Serializer<C, ?> serializer;
	// deserializer
	protected final Deserializer<C, ?, Point> deserializer;
	// supplier
	protected final ChunkFunction2D<C> chunkSupplier;
	// frequent cache
	protected final Long2ObjectMap<Tracker<C>> frequentCache;
	// loaded cache
	protected final Long2ObjectMap<Tracker<C>> loaded = new Long2ObjectOpenHashMap<>();
	// unloaded cache
	protected final Long2ObjectLinkedOpenHashMap<C> unloadedCache;
	// unloaded cache limit
	protected final int inMemorySize;
	// frequent cache limit
	protected final int frequentSize;
	// current accesses
	protected int accessCounter;

	public PrioritisedFile2DChunkCache(File folder, ChunkFunction2D<C> chunkSupplier, Deserializer<C, ?, Point> deserializer, Serializer<C, ?> serializer, int frequentSize, int inMemorySize) {
		this.serializer = serializer;
		this.frequentSize = frequentSize;
		this.inMemorySize = inMemorySize;
		this.deserializer = deserializer;
		this.chunkSupplier = chunkSupplier;
		getFile = l -> new File(folder, l + ".chunkdata");
		frequentCache = new Long2ObjectOpenHashMap<>(frequentSize);
		unloadedCache = new Long2ObjectLinkedOpenHashMap<>(inMemorySize);
	}
	
	protected C read(int x, int y) {
		File file = getFile.apply(key(x, y));
		if (file.exists()) try (FileInputStream input = new FileInputStream(file)) {
			return deserializer.deserialize(new GZIPInputStream(input), new Point(x, y));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return chunkSupplier.newChunk(x, y);
	}

	public PrioritisedFile2DChunkCache(File folder, ChunkFunction2D<C> chunkSupplier, Deserializer<C, ?, Point> deserializer, Serializer<C, ?> serializer) {
		this(folder, chunkSupplier, deserializer, serializer, 128, 1028);
	}

	@Override
	public C getNoPriority(int x, int y) {
		long key = key(x, y);
		C first = frequentCache.getOrDefault(key, (Tracker<C>) Tracker.EMPTY).get();
		if (first == null) first = loaded.getOrDefault(key, (Tracker<C>) Tracker.EMPTY).get();
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
					loaded.put(key, new Tracker<>(c));
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
		update();
	}

	@Override
	public C get(int x, int y) {
		long key = key(x, y);
		Tracker<C> tracker = frequentCache.get(key);
		C first = tracker == null ? null : tracker.get();
		if (first == null)
			first = (tracker = loaded.get(key)) == null ? null : tracker.get();
		if (first == null)
			first = getUnloadedPriority(x, y);
		if (first == null)
			set(x, y, first = read(x, y));
		return first;
	}

	@Override
	public int size() {
		return frequentCache.size() + unloadedCache.size() + loaded.size();
	}

	@Override
	public Iterator<C> iterator() {
		ObjectIterator<Long2ObjectMap.Entry<Tracker<C>>> freq = Long2ObjectMaps.fastIterator(frequentCache);
		ObjectIterator<Long2ObjectMap.Entry<Tracker<C>>> load = Long2ObjectMaps.fastIterator(loaded);
		ObjectIterator<Long2ObjectMap.Entry<C>> ulad = Long2ObjectMaps.fastIterator(unloadedCache);

		return new Iterator<C>() {
			@Override
			public boolean hasNext() {
				return freq.hasNext() || load.hasNext() || ulad.hasNext();
			}

			@Override
			public C next() {
				if (freq.hasNext()) return freq.next().getValue().get();
				else if (load.hasNext()) return load.next().getValue().get();
				else if (ulad.hasNext()) return ulad.next().getValue();
				return null;
			}
		};
	}

	protected long key(int x, int y) {
		return (long) x << 32 | y & 0xffffffffL;
	}


	protected C forceRemove(long key) {
		Tracker<C> tracker = frequentCache.remove(key);
		C first = tracker != null ? tracker.get() : null;
		if (first == null) first = (tracker = loaded.remove(key)) == null ? null : tracker.get();
		if (first == null) first = unloadedCache.remove(key);
		return first;
	}

	@Override
	public C save(int x, int y, boolean remove) throws IOException {
		long key = key(x, y);
		C c = remove ? forceRemove(key) : get(x, y);
		serialize(x, y, c, remove);
		return c;
	}

	@Override
	public void saveAll(boolean remove) throws IOException {
		for (C c : this)
			if (c != null) save(c.getX(), c.getY(), false);
			else System.out.println("Null chunk!");
		if (remove) {
			loaded.clear();
			unloadedCache.clear();
			frequentCache.clear();
		}
	}

	protected void serialize(int x, int y, C c, boolean force) {
		File file = getFile.apply(key(x, y));
		try (GZIPOutputStream out = new GZIPOutputStream(new FileOutputStream(file, true))) {
			serializer.serialize(c, out);
			out.flush();
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	protected C getFromUnloadedNoPriority(int x, int y) throws IOException {
		long key = key(x, y);
		C c = unloadedCache.get(key);
		if (c == null) {
			c = read(x, y);
			unloadedCache.put(key, c);
			trimUnloadedCacheOnce();
		}
		return c;
	}

	protected C getUnloadedPriority(int x, int y) {
		long key = key(x, y);
		return unloadedCache.getAndMoveToFirst(key);
	}

	protected void addToUnloaded(long key, C chunk) throws IOException {
		unloadedCache.put(key, chunk);
		trimUnloadedCacheOnce();
	}


	protected void update() {
		promoteLoaded(trimFrequentOnce());
		if (accessCounter++ > frequentSize * 2)
			accessCounter = 0;
	}

	protected void promoteLoaded(int prev) {
		ObjectIterator<Long2ObjectMap.Entry<Tracker<C>>> iterator = Long2ObjectMaps.fastIterator(loaded);
		Long2ObjectMap.Entry<Tracker<C>> curr;
		while (iterator.hasNext())
			if((curr = iterator.next()).getValue().getAccesses() > prev) {
				frequentCache.put(curr.getLongKey(), curr.getValue());
				iterator.remove();
			}
	}

	protected int trimFrequentOnce() {
		accessCounter++;
		while (frequentCache.size() > frequentSize) {
			ObjectIterator<Long2ObjectMap.Entry<Tracker<C>>> iterator = Long2ObjectMaps.fastIterator(frequentCache);
			Long2ObjectMap.Entry<Tracker<C>> curr;
			Long2ObjectMap.Entry<Tracker<C>> lowest = null;
			int lowestVal = Integer.MAX_VALUE;

			while (iterator.hasNext()) {
				if ((curr = iterator.next()).getValue().getAccesses() < lowestVal) lowest = curr;
				if (accessCounter > frequentSize * 2)
					curr.getValue().reset();
			}

			assert lowest != null : "Unable to trim frequent list!";

			long key = lowest.getLongKey();
			loaded.put(key, lowest.getValue());
			frequentCache.remove(key);
			return lowestVal;
		}
		return -1;
	}


	protected void trimUnloadedCacheOnce() throws IOException {
		if (unloadedCache.size() > inMemorySize) {
			C c = unloadedCache.removeLast();
			save(c.getX(), c.getY(), true);
		}
	}

	public enum Priority {
		FREQUENT, LOADED, UNLOADED_CACHED, UNLOADED
	}

	@Override
	public String toString() {
		return "PrioritisedFile2DChunkCache{" + "getFile=" + getFile + ", serializer=" + serializer + ", frequentCache=" + frequentCache + ", loaded=" + loaded + ", unloadedCache=" + unloadedCache + ", chunkFunction2D=" + chunkSupplier + ", inMemorySize=" + inMemorySize + ", frequentSize=" + frequentSize + ", accessCounter=" + accessCounter + '}';
	}
}
