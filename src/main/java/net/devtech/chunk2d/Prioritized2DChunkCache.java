package net.devtech.chunk2d;

/**
 * A cache that stores chunks in a 2 dimensional plane, and it has priorities within the cache
 * @implNote the default iterator for ChunkCache must only iterate through fully loaded chunks (chunks that have not explicitly been unloaded) for example, if you cache chunks that have been explicitly unloaded, do not iterate through them.
 * @param <C> the chunk class type
 * @param <P> the priority enum / datatype
 */
public interface Prioritized2DChunkCache<C, P> extends ChunkCache2D<C> {
	/**
	 * gets the chunk without changing its priority
	 * @param x the x coordinate of the chunk
	 * @param y the y coordinate of the chunk
	 * @return the chunk at the coordinates or null if none was cached or provided
	 */
	C getNoPriority(int x, int y);

	/**
	 * caches the chunk, but does not "load it" it will remain in the lower priorities of the chunk
	 * @return the currently unloaded chunk
	 */
	C preGen(int x, int y);

	/**
	 * gets the priority of the chunk at the given location
	 * @param x the x coordinate of the chunk
	 * @param y the y coordinate of the chunk
	 * @return the priority of the chunk
	 */
	P getPriority(int x, int y);

	/**
	 * sets the priority of a chunk, does not have to be already generated
	 * @param x the x coordinate
	 * @param y the y coordinate
	 */
	void setPriority(int x, int y);
}
