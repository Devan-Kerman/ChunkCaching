package net.devtech.util;

import java.awt.Point;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class Region2D<C> {
	public C[][] region;

	public Region2D(int size) {
		this.region = (C[][]) new Object[size][size];
	}

	public C get(int x, int y) {
		C c = region[x][y];
		region[x][y] = null;
		return c;
	}

	public C remove(int x, int y) {
		C temp = region[x][y];
		region[x][y] = null;
		return temp;
	}

	public void put(int x, int y, C c) {
		region[x][y] = c;
	}

	public boolean has(int x, int y) {
		return region[x][y] != null;
	}

	public boolean filled() {
		for (C[] cs : region)
			for (C c : cs)
				if (c == null) return false;
		return true;
	}

	public boolean empty() {
		for (C[] cs : region)
			for (C c : cs)
				if (c != null) return false;
		return true;
	}

	public void populate(BiFunction<Integer, Integer, C> populator) {
		for (int x = 0; x < region.length; x++)
			for (int y = 0; y < region[x].length; y++)
				if (!has(x, y)) region[x][y] = populator.apply(x, y);
	}

	public void forEach(BiConsumer<Integer, Integer> forEach) {
		for (int x = 0; x < region.length; x++)
			for (int y = 0; y < region[x].length; y++)
				forEach.accept(x, y);
	}

	public static Point region(int x, int y, int scale) {
		return new Point(Math.floorDiv(x, scale), Math.floorDiv(y, scale));
	}

	public static Point offset(int x, int y, int scale) {
		return new Point(Math.floorMod(x, scale), Math.floorMod(y, scale));
	}
}
