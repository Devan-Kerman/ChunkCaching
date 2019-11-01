package net.devtech.chunk2d;

/**
 * a cache that is guaranteed to cache explicitly loaded chunks on a 2d plane
 * @param <C> the chunk class type
 */
public interface ChunkCache2D<C> extends Iterable<C> {
	/**
	 * tells the cache to unload the chunk, does not guarantee it will be saved in persistent data
	 * @param x the x coordinate of the chunk
	 * @param y the y coordinate of the chunk
	 * @return the chunk previously associated with the position
	 */
	C unload(int x, int y);

	/**
	 * sets the chunk at the given coordinates, if the cache has priority, the chunk will be moved to the top priority
	 * @param x the x coordinate
	 * @param y the y coordinate
	 * @param object the chunk
	 */
	void set(int x, int y, C object);

	/**
	 * get the chunk at the given coordinate, if the cache has priority, the chunk will then be moved to the top priority
	 * @param x the x coor
	 * @param y the y coor
	 * @return the chunk at the coordinates or null if none was cached or provided
	 */
	C get(int x, int y);


	/**
	 * gets the number of chunks currently cached
	 * @return the size of the chunks
	 */
	int size();
}
