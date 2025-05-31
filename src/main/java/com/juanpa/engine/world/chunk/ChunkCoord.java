package com.juanpa.engine.world.chunk;

import java.util.Objects; // For Objects.hash and Objects.equals

public class ChunkCoord
{
	public final int x, y, z;

	public ChunkCoord(int x, int y, int z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}

	@Override
	public boolean equals(Object o)
	{
		if(this == o)
			return true;
		if(o == null || getClass() != o.getClass())
			return false;
		ChunkCoord that = (ChunkCoord) o;
		return x == that.x && y == that.y && z == that.z;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(x, y, z);
	}

	@Override
	public String toString()
	{
		return "ChunkCoord{" + x +  ", " + y + ", " + z + '}';
	}

	/**
	 * Calculates the squared Euclidean distance between this ChunkCoord and another ChunkCoord.
	 * This avoids sqrt for faster comparisons.
	 *
	 * @param other The other ChunkCoord.
	 * @return The squared distance.
	 */
	public double distanceSq(ChunkCoord other) {
		long dx = (long) this.x - other.x;
		long dy = (long) this.y - other.y;
		long dz = (long) this.z - other.z;
		return (double) (dx * dx + dy * dy + dz * dz);
	}
}