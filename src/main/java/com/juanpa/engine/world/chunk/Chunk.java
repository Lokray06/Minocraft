// Modify: com/juanpa/engine/world/chunk/Chunk.java
package com.juanpa.engine.world.chunk;

import com.juanpa.engine.Debug;
import org.joml.Vector3i;

public class Chunk
{
    public static final byte CHUNK_SIZE = 16;

    public short[][][] blocks = new short[CHUNK_SIZE][CHUNK_SIZE][CHUNK_SIZE]; // Make blocks public for ChunkMesh.generateMeshData
    ChunkCoord chunkCoords;
    ChunkMesh mesh; // This holds the GPU-related mesh data

    boolean isDirty; // Indicates if block data has changed and mesh needs re-generation

    public Chunk(ChunkCoord chunkCoords)
    {
        this.chunkCoords = chunkCoords;
        generateTestChunk(); // Generates block data (CPU-side)
        this.mesh = new ChunkMesh(); // Initialize ChunkMesh, but don't generate/upload data yet
        this.isDirty = true; // New chunk is dirty, needs mesh generation/upload
    }

    /**
     * Triggers the regeneration of the chunk's mesh data on a background thread and eventual upload to the GPU on the main thread. This method just *requests* mesh update, doesn't perform it directly. It's called by the World when a chunk is loaded or its blocks change.
     */
    public void requestMeshUpdate()
    {
        this.isDirty = true; // Mark as dirty if not already
        // The World class will pick up this dirty chunk and queue it for generation/upload.
    }

    public void setBlock(Vector3i localCoords, short blockID)
    {
        if (localCoords.x < 0 || localCoords.x >= CHUNK_SIZE || localCoords.y < 0 || localCoords.y >= CHUNK_SIZE || localCoords.z < 0 || localCoords.z >= CHUNK_SIZE)
        {
            Debug.logError("Attempted to set block outside chunk bounds: " + localCoords);
            return;
        }
        this.blocks[localCoords.x][localCoords.y][localCoords.z] = blockID;
        this.isDirty = true; // Mark chunk as dirty when blocks change
        // Note: The world class will now be responsible for detecting dirty chunks
        // and re-queuing them for mesh generation/upload.
    }

    public short getBlock(Vector3i localCoords)
    {
        if (localCoords.x < 0 || localCoords.x >= CHUNK_SIZE || localCoords.y < 0 || localCoords.y >= CHUNK_SIZE || localCoords.z < 0 || localCoords.z >= CHUNK_SIZE)
        {
            // This is frequently called for neighbors, suppress error for expected out-of-bounds checks
            // Debug.logError("Attempted to get block outside chunk bounds: " + localCoords);
            return 0; // Return AIR for out of bounds as per common voxel engine practice
        }
        return this.blocks[localCoords.x][localCoords.y][localCoords.z];
    }

    void generateTestChunk()
    {
        // Define terrain parameters
        // These can be adjusted for different terrain shapes
        final float AMPLITUDE = 4.0f; // Max height of the mountains from the base
        final float FREQUENCY_X = 1.5f; // How "wide" the mountains are along X (smaller value = wider)
        final float FREQUENCY_Z = 1.3f; // How "wide" the mountains are along Z
        final int BASE_HEIGHT = 30; // Minimum height of the terrain

        // Calculate the absolute world starting coordinates for this chunk
        int worldStartX = chunkCoords.x * CHUNK_SIZE;
        int worldStartZ = chunkCoords.z * CHUNK_SIZE;
        // Note: chunkCoords.y is typically for vertical layering of chunks,
        // but for a continuous ground plane, we mostly use x and z for heightmap.

        for (int x = 0; x < CHUNK_SIZE; x++)
        {
            for (int z = 0; z < CHUNK_SIZE; z++)
            {
                
                for (int y = 0; y < CHUNK_SIZE; y++)
                {
                    if(Math.random() > 0.8f)
                    {
                        blocks[x][y][z] = 1;
                    }
                    else
                    {
                        blocks[x][y][z] = 0;
                    }
                }
                continue;
                /*
                // Calculate world coordinates for the current block column
                float worldX = worldStartX + x;
                float worldZ = worldStartZ + z;

                // Use a combination of sine waves for height
                // Math.sin takes radians, so multiply by PI or a scaling factor
                float height_x = (float) Math.sin(worldX * FREQUENCY_X);
                float height_z = (float) Math.sin(worldZ * FREQUENCY_Z);

                // Combine them. You can use different combinations:
                // - Sum: (height_x + height_z)
                // - Multiply: (height_x * height_z)
                // - Average: (height_x + height_z) / 2.0f
                // Here, we'll sum them and adjust range from [-2, 2] to [0, 4] then scale
                float combined_height_factor = (height_x + height_z + 2.0f) / 4.0f; // Normalize to 0-1 range

                // Calculate the final height for this column, convert to int
                int surfaceHeight = BASE_HEIGHT + (int) (combined_height_factor * AMPLITUDE);

                // Ensure surfaceHeight is within reasonable bounds (e.g., within Chunk.CHUNK_SIZE)
                surfaceHeight = Math.min(surfaceHeight, CHUNK_SIZE - 1);
                surfaceHeight = Math.max(surfaceHeight, 1); // Ensure at least y=1, as y=0 could be bedrock or bottom

                // Fill blocks from y=0 up to the calculated surfaceHeight
                for (int y = 0; y <= surfaceHeight; y++)
                {
                    blocks[x][y][z] = 1; // Assuming '1' is a solid block type (e.g., stone or dirt)
                }

                // Any blocks above surfaceHeight will remain air (0 by default in a new short[][][])
                */
            }
        }
    }

    public ChunkMesh getMesh()
    {
        return mesh;
    }

    public ChunkCoord getCoord()
    {
        return chunkCoords;
    }

    public boolean isDirty()
    {
        return isDirty;
    }

    public void setIsDirty(boolean newIsDirtyValue)
    {
        isDirty = newIsDirtyValue;
    }

    // -----------------------------------//
    // ------ Cleanup Method ---------//
    // -----------------------------------//

    /**
     * Disposes of the chunk's resources, particularly its GPU-side mesh data. This should be called when the chunk is unloaded from memory.
     */
    public void dispose()
    {
        //Debug.log("Disposing chunk: " + chunkCoords);
        if (mesh != null)
        {
            mesh.cleanup(); // Call the ChunkMesh's cleanup method to free GPU resources
            mesh = null; // Dereference the mesh object
        }
        // No other significant resources held directly by Chunk itself usually
        // The block array will be garbage collected.
    }
}