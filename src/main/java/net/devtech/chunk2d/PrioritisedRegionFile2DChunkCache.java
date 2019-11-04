package net.devtech.chunk2d;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.devtech.data.Deserializer;
import net.devtech.data.Serializer;
import net.devtech.util.Region2D;
import java.awt.Point;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

// region based version of its parent class
public class PrioritisedRegionFile2DChunkCache<C extends Located2D> extends PrioritisedFile2DChunkCache<C> {
	private final int regionSize;
	private Long2ObjectMap<Region2D<C>> regions = new Long2ObjectOpenHashMap<>();

	public PrioritisedRegionFile2DChunkCache(File folder, ChunkFunction2D<C> chunkSupplier, Deserializer<C, ?, Point> deserializer, Serializer<C, ?> serializer, int frequentSize, int inMemorySize, int regionSize) {
		super(folder, chunkSupplier, deserializer, serializer, frequentSize, inMemorySize);
		this.regionSize = regionSize;
	}

	public PrioritisedRegionFile2DChunkCache(File folder, ChunkFunction2D<C> chunkSupplier, Deserializer<C, ?, Point> deserializer, Serializer<C, ?> serializer, int regionSize) {
		super(folder, chunkSupplier, deserializer, serializer);
		this.regionSize = regionSize;
	}

	@Override
	protected void serialize(int x, int y, C c, boolean force) {
		Point point = Region2D.region(x, y, regionSize);
		Region2D<C> region2D = getRegion(key(point.x, point.y));
		Point offset = Region2D.offset(x, y, regionSize);
		region2D.put(offset.x, offset.y, c);
		if(force || region2D.filled()) {
			File file = getFile.apply(key(point.x, point.y));
			region2D.forEach((ox, oy) -> {
				try (GZIPOutputStream out = new GZIPOutputStream(new FileOutputStream(file, true))) {
					serializer.serialize(c, out);
					out.flush();
				} catch (Throwable t) {
					throw new RuntimeException(t);
				}
			});
			regions.remove(key(point.x, point.y));
		}
	}

	@Override
	protected C read(int x, int y) {
		Point point = Region2D.region(x, y, regionSize);
		Region2D<C> region2D = getRegion(key(point.x, point.y));
		if(region2D.empty()) {
			File file = getFile.apply(key(point.x, point.y));
			region2D.populate((ox, oy) -> {
				if (file.exists()) try (FileInputStream input = new FileInputStream(file)) {
					return deserializer.deserialize(new GZIPInputStream(input), new Point(x+ox, y+oy));
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				return chunkSupplier.newChunk(x, y);
			});
		}
		Point point1 = Region2D.offset(x, y, regionSize);
		return region2D.remove(point1.x, point1.y);
	}

	private Region2D<C> getRegion(long key) {
		return regions.computeIfAbsent(key, l -> new Region2D<>(regionSize));
	}

}
