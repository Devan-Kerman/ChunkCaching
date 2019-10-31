package net.devtech;

/**
 * represents a cache that has persistent storage
 * @param <C> the chunk
 */
public interface PersistentCache<C> {
	/**
	 * forcefully save the chunk to persistent storage regardless of priority, call this when the program is shutting down
	 * if the chunk hasn't been supplied yet, create a new one and save it
	 * @param unload whether or not the chunks should be unloaded as well
	 */
	void save(C chunk, boolean unload);
	/**
	 * save all the chunks
	 * @param unload
	 */
	void saveAll(C chunk, boolean unload);
}
