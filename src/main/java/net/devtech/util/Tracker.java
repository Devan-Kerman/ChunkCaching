package net.devtech.util;

public class Tracker<C> {
	public static final Tracker<?> EMPTY = new Tracker<>(null);
	private int accesses;
	private C objects;

	public Tracker(C objects) {
		this.objects = objects;
	}

	public C get() {
		accesses++;
		return objects;
	}

	public int getAccesses() {
		return accesses;
	}

	public void reset() {
		accesses = 0;
	}
}