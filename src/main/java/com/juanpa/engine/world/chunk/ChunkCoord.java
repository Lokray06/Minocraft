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
}