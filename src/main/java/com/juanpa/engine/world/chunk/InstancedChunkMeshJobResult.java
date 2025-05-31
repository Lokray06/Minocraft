package com.juanpa.engine.world.chunk;

import java.util.List;

// In World.java or a separate file
// (Assuming QuadInstanceData is accessible, e.g. public static class in ChunkMesh or its own file)
public class InstancedChunkMeshJobResult
{
	public final ChunkCoord coord;
	public final List<ChunkMesh.QuadInstanceData> instanceData; // Changed from float[]/byte[]

	public InstancedChunkMeshJobResult(ChunkCoord coord, List<ChunkMesh.QuadInstanceData> instanceData)
	{
		this.coord = coord;
		this.instanceData = instanceData;
	}
}