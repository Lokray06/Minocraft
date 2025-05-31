// Create a new file: com/juanpa/engine/world/chunk/ChunkMeshJobResult.java
package com.juanpa.engine.world.chunk;

// A simple data class to hold the results of a chunk mesh generation job.
// This data will be passed from the worker thread to the main rendering thread.
public class ChunkMeshJobResult
{
    public final ChunkCoord coord;
    public final float[] vertices;
    public final float[] normals;
    public final int vertexCount; // The number of vertices (not floats)

    public ChunkMeshJobResult(ChunkCoord coord, float[] vertices, float[] normals, int vertexCount)
    {
        this.coord = coord;
        this.vertices = vertices;
        this.normals = normals;
        this.vertexCount = vertexCount;
    }
}