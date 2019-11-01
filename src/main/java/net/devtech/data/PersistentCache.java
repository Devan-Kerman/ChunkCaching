package net.devtech.data;

/**
 * represents a cache that has persistent storage
 * @param <C> the chunk
 */
public interface PersistentCache<C> {
	/**
	 * forcefully save the chunk to persistent storage regardless of priority, call this when the program is shutting down
	 * if the chunk hasn't been supplied yet, create a new one and save it
	 */
	C save(int x, int y, boolean remove) throws Exception;
	/**
	 * save all the chunks
	 */
	void saveAll(boolean remove) throws Exception;
}
