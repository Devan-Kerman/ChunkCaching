package net.devtech;

import net.devtech.chunk2d.Located2D;
import java.util.Random;

public class Chunk implements Located2D {
	private int x;
	private int y;
	private int value;

	public Chunk(int x, int y) {
		this.x = x;
		this.y = y;
		this.value = new Random(x ^ (long)y >> 32).nextInt();
	}

	public void setValue(int value) {
		this.value = value;
	}

	public int getValue() {
		return value;
	}

	@Override
	public int getX() {
		return x;
	}

	@Override
	public int getY() {
		return y;
	}
}
