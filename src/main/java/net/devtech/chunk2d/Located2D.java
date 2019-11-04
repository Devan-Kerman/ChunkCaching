package net.devtech.chunk2d;

import java.awt.Point;

/**
 * represents an object that exists on a 2D plane
 */
public interface Located2D {
	/**
	 * returns the x coordinate of the location this object is on
	 * @return the x coordinate
	 */
	int getX();

	/**
	 * returns the y coordinate of the location this object is on
	 * @return the y coordinate
	 */
	int getY();

	/**
	 * returns an awt point object of the chunk
	 * @return a new instance of the coordinates
	 */
	default Point getLocation() {
		return new Point(getX(), getY());
	}

	static Located2D of(Point point) {
		return new Located2D() {
			@Override
			public int getX() {
				return point.x;
			}

			@Override
			public int getY() {
				return point.y;
			}
		};
	}
}
