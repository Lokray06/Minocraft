package com.juanpa.engine.world;

import com.juanpa.engine.world.chunk.ChunkCoord;

class NewChunkMeshJobResult
{
	public final ChunkCoord coord;
	public final float[] vertices;
	public final byte[] normalIDs;
	public final byte[] blockTypes;
	// vertexCount can be derived from vertices.length / 3

	public NewChunkMeshJobResult(ChunkCoord coord, float[] vertices, byte[] normalIDs, byte[] blockTypes)
	{
		this.coord = coord;
		this.vertices = vertices;
		this.normalIDs = normalIDs;
		this.blockTypes = blockTypes;
	}
}