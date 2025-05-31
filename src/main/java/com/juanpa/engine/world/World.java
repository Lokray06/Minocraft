// Modify: com/juanpa/engine/world/World.java
package com.juanpa.engine.world;

import com.juanpa.engine.Debug;
import com.juanpa.engine.components.Camera;
import com.juanpa.engine.renderer.Renderer;
import com.juanpa.engine.world.chunk.Chunk;
import com.juanpa.engine.world.chunk.ChunkCoord;
import com.juanpa.engine.world.chunk.ChunkMeshJobResult;
import com.juanpa.game.Game;
import org.joml.Vector3f;
import org.joml.Vector3i;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class World
{
    // --- Constants ---
    public static final short BLOCK_TYPE_AIR_ID = 0;
    public static final short BLOCK_TYPE_SOLID_ID = 1; // Example solid block ID
    public static final short BLOCK_TYPE_GRASS_ID = 2; // Add this if not already in Chunk class
    public static final short BLOCK_TYPE_STONE_ID = 3; // Add this if not already in Chunk class

    private static final int WORLD_MIN_BLOCK_Y = 0;
    // Calculate based on Chunk.java's generation parameters:
    // BASE_SEA_LEVEL (60) + HEIGHT_SCALE (80) = 140
    private static final int WORLD_MAX_BLOCK_Y = 60 + 80; // Should be 140

    // Then, the chunk bounds will be:
    private static final int WORLD_MIN_CHUNK_Y = WORLD_MIN_BLOCK_Y / Chunk.CHUNK_SIZE; // 0
    private static final int WORLD_MAX_CHUNK_Y = WORLD_MAX_BLOCK_Y / Chunk.CHUNK_SIZE; // 140 / 16 = 8 (approx)
                                                                                       // This will ensure chunks from Y=0 to Y=8 are loaded.

    // --- Fields ---
    private long seed;
    private Map<ChunkCoord, Chunk> loadedChunks;
    private Renderer renderer;

    private Vector3f playerPosition;
    private ChunkCoord lastPlayerChunkCoord;

    private Queue<ChunkCoord> chunksToUnloadQueue;
    private Queue<ChunkMeshJobResult> chunksToUploadQueue;
    private Queue<ChunkCoord> chunksToForceUpdateQueue;
    private Queue<ChunkCoord> chunksToGenerateQueue; // Moved here for logical grouping

    private ExecutorService chunkGenerationThreadPool;
    private static final int CHUNKS_PER_FRAME_PROCESS_LIMIT = 24;
    private static final int CHUNKS_PER_FRAME_GENERATE_LIMIT = 24;

    // ----------------------//
    // ---- Constructor ----//
    // ----------------------//
    public World(long generationSeed, Renderer renderer)
    {
        this.seed = generationSeed;
        this.renderer = renderer;
        this.loadedChunks = new HashMap<>();

        this.playerPosition = new Vector3f(0.0f, 0.0f, 0.0f);
        this.lastPlayerChunkCoord = getChunkCoordinatesForBlock(playerPosition);

        this.chunksToUnloadQueue = new ConcurrentLinkedQueue<>();
        this.chunksToGenerateQueue = new ConcurrentLinkedQueue<>();
        this.chunksToUploadQueue = new ConcurrentLinkedQueue<>();
        this.chunksToForceUpdateQueue = new ConcurrentLinkedQueue<>();

        int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        chunkGenerationThreadPool = Executors.newFixedThreadPool(numThreads);
        Debug.logInfo("Initialized chunk generation thread pool with " + numThreads + " threads.");

        init();
        Debug.logInfo("World initialized with seed [" + seed + "]");
    }

    // -----------------------------------//
    // ------ Lifecycle Methods ---------//
    // -----------------------------------//

    public void init()
    {
        enqueueInitialChunks();
    }

    private void enqueueInitialChunks()
    {
        int renderDistance = Game.renderDistance; // This is XZ render distance
        ChunkCoord playerChunkCoords = getChunkCoordinatesForBlock(playerPosition);

        List<ChunkCoord> potentialChunksToGenerate = new ArrayList<>();

        for (int x = -renderDistance; x <= renderDistance; x++)
        {
            for (int z = -renderDistance; z <= renderDistance; z++)
            {
                // Loop through all relevant Y-levels for terrain
                for (int y = WORLD_MIN_CHUNK_Y; y <= WORLD_MAX_CHUNK_Y; y++) // <<< MODIFIED HERE
                {
                    ChunkCoord targetChunkCoord = new ChunkCoord(playerChunkCoords.x + x, y, // Use the iterated Y-chunk coordinate
                            playerChunkCoords.z + z);

                    // Load/Generate chunk only if it's not already loaded and not already in a queue
                    if (!loadedChunks.containsKey(targetChunkCoord) && !chunksToGenerateQueue.contains(targetChunkCoord) && !chunksToUploadQueue.stream().anyMatch(res -> res.coord.equals(targetChunkCoord)) && !chunksToForceUpdateQueue.contains(targetChunkCoord))
                    {
                        potentialChunksToGenerate.add(targetChunkCoord);
                    }
                }
            }
        }

        // Sort chunks by distance from the player chunk (Manhattan distance for simplicity)
        // We still prioritize XZ distance for visible chunks, but also include Y distance.
        potentialChunksToGenerate.sort(Comparator.comparingInt(coord -> Math.abs(coord.x - playerChunkCoords.x) + Math.abs(coord.z - playerChunkCoords.z) + Math.abs(coord.y - playerChunkCoords.y) // Include Y distance for sorting
        ));

        for (ChunkCoord coord : potentialChunksToGenerate)
        {
            chunksToGenerateQueue.add(coord);
        }
    }

    public void update()
    {
        Camera activeCamera = Game.getActiveCamera(); // You need to implement this method in your Game class
        if (activeCamera == null) {
            Debug.logWarning("No main camera found for frustum culling. Skipping culling for this frame.");
            // Potentially return early or handle gracefully if no camera is active
            return;
        }

        ChunkCoord currentPlayerChunk = getChunkCoordinatesForBlock(playerPosition);

        // Only update chunk queues if player has moved to a new XZ chunk (or Y chunk, if relevant)
        // This 'fixedChunkY=0' in your original ChunkCoord check means it only triggers on XZ moves.
        // If you want it to trigger on Y-chunk changes too, you'll need ChunkCoord to check Y in equals().
        if (!currentPlayerChunk.equals(lastPlayerChunkCoord))
        {
            updateChunkQueues();
            lastPlayerChunkCoord = currentPlayerChunk;
        }

        processChunkQueuesAsync();
    }

    public void dispose()
    {
        Debug.logInfo("Disposing world: unloading all chunks and shutting down thread pool.");

        chunksToUnloadQueue.clear();
        chunksToGenerateQueue.clear();
        chunksToUploadQueue.clear();
        chunksToForceUpdateQueue.clear();

        chunkGenerationThreadPool.shutdown();
        try
        {
            if (!chunkGenerationThreadPool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS))
            {
                Debug.logWarning("Chunk generation thread pool did not terminate in time, forcing shutdown.");
                chunkGenerationThreadPool.shutdownNow();
            }
        } catch (InterruptedException e)
        {
            Debug.logError("Chunk generation thread pool termination interrupted: " + e.getMessage());
            chunkGenerationThreadPool.shutdownNow();
        }

        Set<ChunkCoord> coordsToDispose = new HashSet<>(loadedChunks.keySet());

        for (ChunkCoord coord : coordsToDispose)
        {
            Chunk chunk = loadedChunks.remove(coord);
            if (chunk != null)
            {
                this.renderer.disposeChunkMesh(coord);
                chunk.dispose();
            }
        }
        loadedChunks.clear();
    }

    // -----------------------------------//
    // ---- Chunk Management Methods ----//
    // -----------------------------------//

    private void updateChunkQueues()
    {
        int renderDistance = Game.renderDistance; // XZ render distance
        ChunkCoord playerChunkCoords = getChunkCoordinatesForBlock(playerPosition);

        Set<ChunkCoord> desiredChunkCoords = new HashSet<>();
        List<ChunkCoord> newChunksToGenerate = new ArrayList<>();

        for (int x = -renderDistance; x <= renderDistance; x++)
        {
            for (int z = -renderDistance; z <= renderDistance; z++)
            {
                // Loop through all relevant Y-levels for terrain
                for (int y = WORLD_MIN_CHUNK_Y; y <= WORLD_MAX_CHUNK_Y; y++) // <<< MODIFIED HERE
                {
                    ChunkCoord targetChunkCoord = new ChunkCoord(playerChunkCoords.x + x, y, // Use the iterated Y-chunk coordinate
                            playerChunkCoords.z + z);
                    desiredChunkCoords.add(targetChunkCoord);

                    if (!loadedChunks.containsKey(targetChunkCoord) && !chunksToGenerateQueue.contains(targetChunkCoord) && !chunksToUploadQueue.stream().anyMatch(res -> res.coord.equals(targetChunkCoord)) && !chunksToForceUpdateQueue.contains(targetChunkCoord))
                    {
                        newChunksToGenerate.add(targetChunkCoord);
                    }
                }
            }
        }

        // Sort new chunks to generate by distance from the player
        newChunksToGenerate.sort(Comparator.comparingInt(coord -> Math.abs(coord.x - playerChunkCoords.x) + Math.abs(coord.z - playerChunkCoords.z) + Math.abs(coord.y - playerChunkCoords.y) // Include Y distance for sorting
        ));

        for (ChunkCoord coord : newChunksToGenerate)
        {
            chunksToGenerateQueue.add(coord);
        }

        // Identify chunks to unload: those currently loaded but not in the desired set.
        Iterator<Map.Entry<ChunkCoord, Chunk>> iterator = loadedChunks.entrySet().iterator();
        while (iterator.hasNext())
        {
            Map.Entry<ChunkCoord, Chunk> entry = iterator.next();
            ChunkCoord currentLoadedCoord = entry.getKey();

            // If a chunk is loaded but outside the current desired XZ *or* the fixed Y range, unload it.
            // This ensures chunks beyond your WORLD_MAX_BLOCK_Y or below WORLD_MIN_BLOCK_Y are unloaded.
            // You might want to adjust the unload distance based on your render distance.
            // For now, we'll keep the existing logic and let the desiredChunkCoords handle it.
            if (!desiredChunkCoords.contains(currentLoadedCoord))
            {
                if (!chunksToUnloadQueue.contains(currentLoadedCoord))
                {
                    chunksToUnloadQueue.add(currentLoadedCoord);
                }
            }
        }
    }

    private void processChunkQueuesAsync()
    {
        // ... (No changes needed in this method, it correctly processes the queues) ...
        int processedCount = 0;

        // 1. Process forced updates (e.g., from player interaction) first
        while (!chunksToForceUpdateQueue.isEmpty() && processedCount < CHUNKS_PER_FRAME_PROCESS_LIMIT)
        {
            ChunkCoord coordToUpdate = chunksToForceUpdateQueue.poll();
            if (coordToUpdate != null)
            {
                Chunk chunk = loadedChunks.get(coordToUpdate);
                if (chunk != null)
                {
                    submitChunkGenerationTask(chunk);
                    processedCount++;
                }
            }
        }

        // 2. Process unloading tasks
        while (!chunksToUnloadQueue.isEmpty() && processedCount < CHUNKS_PER_FRAME_PROCESS_LIMIT)
        {
            ChunkCoord coordToUnload = chunksToUnloadQueue.poll();
            if (coordToUnload != null)
            {
                Chunk chunk = loadedChunks.remove(coordToUnload);
                if (chunk != null)
                {
                    this.renderer.disposeChunkMesh(coordToUnload);
                    chunk.dispose();
                    processedCount++;
                }
            }
        }

        // 3. Submit new chunk generation tasks to the thread pool
        int submittedCount = 0;
        while (!chunksToGenerateQueue.isEmpty() && submittedCount < CHUNKS_PER_FRAME_GENERATE_LIMIT)
        {
            ChunkCoord coord = chunksToGenerateQueue.poll();
            if (coord != null)
            {
                if (loadedChunks.containsKey(coord))
                {
                    continue;
                }
                Chunk newChunk = new Chunk(coord);
                loadedChunks.put(coord, newChunk);
                submitChunkGenerationTask(newChunk);
                submittedCount++;
            }
        }

        // 4. Process completed mesh data ready for GPU upload (main thread only)
        int uploadedCount = 0;
        while (!chunksToUploadQueue.isEmpty() && uploadedCount < CHUNKS_PER_FRAME_PROCESS_LIMIT)
        {
            ChunkMeshJobResult result = chunksToUploadQueue.poll();
            if (result != null)
            {
                Chunk chunk = loadedChunks.get(result.coord);
                if (chunk != null)
                {
                    chunk.getMesh().uploadToGPU(result.vertices, result.normals);
                    this.renderer.registerChunkMesh(chunk.getCoord(), chunk.getMesh());

                    chunk.setIsDirty(false);
                    uploadedCount++;
                } else
                {
                    //Debug.logWarning("Skipping GPU upload for unloaded chunk: " + result.coord);
                }
            }
        }
    }

    private void submitChunkGenerationTask(Chunk chunk)
    {
        if (chunk == null)
            return;
        final ChunkCoord coord = chunk.getCoord();

        chunkGenerationThreadPool.submit(() -> {
            try
            {
                List<Float> positions = new ArrayList<>();
                List<Float> normals = new ArrayList<>();

                // Pass the chunk itself for block data access
                chunk.getMesh().generateMeshData(chunk, positions, normals);

                float[] vertices = new float[positions.size()];
                for (int i = 0; i < positions.size(); i++)
                {
                    vertices[i] = positions.get(i);
                }

                float[] normalData = new float[normals.size()];
                for (int i = 0; i < normals.size(); i++)
                {
                    normalData[i] = normals.get(i);
                }

                int vertexCount = vertices.length / 3;

                chunksToUploadQueue.add(new ChunkMeshJobResult(coord, vertices, normalData, vertexCount));
            } catch (Exception e)
            {
                Debug.logError("Error generating chunk mesh asynchronously for " + coord + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public Chunk getChunk(ChunkCoord coord)
    {
        return loadedChunks.get(coord);
    }

    // -----------------------------------//
    // ------ Block Management ---------//
    // -----------------------------------//

    public void setBlock(Vector3i blockCoords, short blockId)
    {
        ChunkCoord chunkCoords = getChunkCoordinatesForBlock(blockCoords);
        Chunk chunkToModify = loadedChunks.get(chunkCoords);

        if (chunkToModify == null)
        {
            // If the chunk is not loaded, create it and add to loadedChunks
            Debug.logWarning("Attempted to set block in unloaded chunk at: " + chunkCoords.toString() + ". Force loading it now.");
            chunkToModify = new Chunk(chunkCoords);
            loadedChunks.put(chunkCoords, chunkToModify);
        }

        Vector3i blockLocalCoords = getLocalBlockCoordinatesInChunk(blockCoords);

        if (chunkToModify.getBlock(blockLocalCoords) != blockId)
        {
            chunkToModify.setBlock(blockLocalCoords, blockId);
            if (!chunksToForceUpdateQueue.contains(chunkCoords) && !chunksToGenerateQueue.contains(chunkCoords) && !chunksToUploadQueue.stream().anyMatch(res -> res.coord.equals(chunkCoords)))
            {
                chunksToForceUpdateQueue.add(chunkCoords);
            }
        }
    }

    public short getBlock(Vector3i blockCoords)
    {
        ChunkCoord chunkCoords = getChunkCoordinatesForBlock(blockCoords);
        Chunk chunk = loadedChunks.get(chunkCoords);

        if (chunk == null)
        {
            return BLOCK_TYPE_AIR_ID;
        }

        Vector3i blockLocalCoords = getLocalBlockCoordinatesInChunk(blockCoords);
        return chunk.getBlock(blockLocalCoords);
    }

    // -----------------------------------//
    // ---- Helper/Utility Methods -----//
    // -----------------------------------//

    public ChunkCoord getChunkCoordinatesForBlock(Vector3i worldBlockPos)
    {
        int chunkX = (int) Math.floor((float) worldBlockPos.x / Chunk.CHUNK_SIZE);
        int chunkY = (int) Math.floor((float) worldBlockPos.y / Chunk.CHUNK_SIZE);
        int chunkZ = (int) Math.floor((float) worldBlockPos.z / Chunk.CHUNK_SIZE);
        return new ChunkCoord(chunkX, chunkY, chunkZ);
    }

    public ChunkCoord getChunkCoordinatesForBlock(Vector3f worldPos)
    {
        int chunkX = (int) Math.floor(worldPos.x / Chunk.CHUNK_SIZE);
        int chunkY = (int) Math.floor(worldPos.y / Chunk.CHUNK_SIZE);
        int chunkZ = (int) Math.floor(worldPos.z / Chunk.CHUNK_SIZE);
        return new ChunkCoord(chunkX, chunkY, chunkZ);
    }

    public Vector3i getLocalBlockCoordinatesInChunk(Vector3i worldBlockPos)
    {
        int localX = Math.floorMod(worldBlockPos.x, Chunk.CHUNK_SIZE);
        int localY = Math.floorMod(worldBlockPos.y, Chunk.CHUNK_SIZE);
        int localZ = Math.floorMod(worldBlockPos.z, Chunk.CHUNK_SIZE);
        return new Vector3i(localX, localY, localZ);
    }

    // -----------------------------------//
    // --- Player Position Accessor ----//
    // -----------------------------------//

    public void setPlayerPosition(Vector3f playerPosition)
    {
        this.playerPosition.set(playerPosition);
    }
}