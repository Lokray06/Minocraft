// Modify: com/juanpa/engine/world/World.java
package com.juanpa.engine.world;

import com.juanpa.engine.Debug;
import com.juanpa.engine.renderer.Renderer;
import com.juanpa.engine.world.chunk.Chunk;
import com.juanpa.engine.world.chunk.ChunkCoord;
import com.juanpa.engine.world.chunk.ChunkMeshJobResult; // NEW: Import
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
import java.util.concurrent.ExecutorService; // NEW: For thread pool
import java.util.concurrent.Executors; // NEW: For thread pool
import java.util.List; // For temporary lists in background generation
import java.util.ArrayList; // For temporary lists in background generation
import java.util.Collections; // NEW: For shuffling lists
import java.util.Comparator; // NEW: For sorting lists

public class World
{
    // --- Constants ---
    public static final short BLOCK_TYPE_AIR_ID = 0;
    public static final short BLOCK_TYPE_SOLID_ID = 1; // Example solid block ID

    // --- Fields ---
    private long seed;
    private Map<ChunkCoord, Chunk> loadedChunks; // Stores currently active chunks
    private Renderer renderer; // Reference to your renderer (for GPU upload/removal)

    // Player position (updated by Game/PlayerController, typically floating-point)
    private Vector3f playerPosition;

    // Last known player chunk coordinates, to detect chunk changes and trigger updates
    private ChunkCoord lastPlayerChunkCoord;

    // --- NEW: Chunk Management Queues and Budget ---
    // Queues for throttling chunk processing
    private Queue<ChunkCoord> chunksToUnloadQueue; // Chunks to be removed from loadedChunks and disposed
    private Queue<ChunkCoord> chunksToGenerateQueue; // Chunks whose mesh data needs CPU generation (added by main thread, consumed by worker threads)
    private Queue<ChunkMeshJobResult> chunksToUploadQueue; // Generated mesh data ready for GPU upload (added by worker threads, consumed by main thread)
    private Queue<ChunkCoord> chunksToForceUpdateQueue; // Chunks that need immediate regeneration (e.g., block changed by player)

    private ExecutorService chunkGenerationThreadPool; // Thread pool for background chunk generation
    private static final int CHUNKS_PER_FRAME_PROCESS_LIMIT = 24; // How many chunks to process (unload/upload) per update call
    private static final int CHUNKS_PER_FRAME_GENERATE_LIMIT = 24; // How many generation tasks to submit per update call

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

        // Initialize new queues
        this.chunksToUnloadQueue = new ConcurrentLinkedQueue<>();
        this.chunksToGenerateQueue = new ConcurrentLinkedQueue<>();
        this.chunksToUploadQueue = new ConcurrentLinkedQueue<>();
        this.chunksToForceUpdateQueue = new ConcurrentLinkedQueue<>();

        // Initialize thread pool for chunk generation
        // Use a number of threads equal to available CPU cores minus one for the main thread, minimum 1.
        int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        chunkGenerationThreadPool = Executors.newFixedThreadPool(numThreads);
        Debug.logInfo("Initialized chunk generation thread pool with " + numThreads + " threads.");

        init();
        Debug.logInfo("World initialized with seed [" + seed + "]");
    }

    // -----------------------------------//
    // ------ Lifecycle Methods ---------//
    // -----------------------------------//

    /**
     * Initializes the world by loading the initial set of chunks around the player.
     */
    public void init()
    {
        // Enqueue initial chunks for generation
        enqueueInitialChunks();
    }

    private void enqueueInitialChunks()
    {
        int renderDistance = Game.renderDistance;
        ChunkCoord playerChunkCoords = getChunkCoordinatesForBlock(playerPosition);
        int fixedChunkY = 0; // Assuming all chunks are at y=0

        // Use a temporary list to store desired chunks, then sort them by distance
        List<ChunkCoord> potentialChunksToGenerate = new ArrayList<>();

        for (int x = -renderDistance; x <= renderDistance; x++)
        {
            for (int z = -renderDistance; z <= renderDistance; z++)
            {
                ChunkCoord targetChunkCoord = new ChunkCoord(playerChunkCoords.x + x, fixedChunkY, playerChunkCoords.z + z);
                // Load/Generate chunk only if it's not already loaded and not already in a queue
                if (!loadedChunks.containsKey(targetChunkCoord) && !chunksToGenerateQueue.contains(targetChunkCoord) && !chunksToUploadQueue.stream().anyMatch(res -> res.coord.equals(targetChunkCoord)) &&
                        !chunksToForceUpdateQueue.contains(targetChunkCoord))
                {
                    potentialChunksToGenerate.add(targetChunkCoord);
                }
            }
        }

        // Sort chunks by distance from the player chunk (Manhattan distance for simplicity)
        // You could use Euclidean distance if preferred, but Manhattan is faster for grid systems.
        potentialChunksToGenerate.sort(Comparator.comparingInt(coord ->
                Math.abs(coord.x - playerChunkCoords.x) + Math.abs(coord.z - playerChunkCoords.z)));

        // Add sorted chunks to the generation queue
        for (ChunkCoord coord : potentialChunksToGenerate) {
            chunksToGenerateQueue.add(coord);
        }
    }

    /**
     * Updates the world state, primarily managing chunk loading/unloading based on player movement. Processes a limited number of items from the chunk queues each frame.
     *
     * @param deltaTime The time elapsed since the last frame (not directly used here, but common for update methods).
     */
    public void update()
    {
        // Convert player's float position to integer chunk coordinates
        ChunkCoord currentPlayerChunk = getChunkCoordinatesForBlock(playerPosition);

        // Check if the player has moved to a new chunk
        if (!currentPlayerChunk.equals(lastPlayerChunkCoord))
        {
            // // // Debug.logInfo("Player moved to new chunk: " + currentPlayerChunk.toString());
            updateChunkQueues(); // Re-evaluate and update queues
            lastPlayerChunkCoord = currentPlayerChunk; // Update last known chunk
        }

        // --- NEW: Process chunks from queues within a budget ---
        processChunkQueuesAsync();

        // You might add other world update logic here later (e.g., ticking blocks, physics)
    }

    /**
     * Disposes of all resources held by the world, specifically unloading all loaded chunks and freeing their GPU-side mesh data, and shutting down the thread pool.
     */
    public void dispose()
    {
        // Debug.logInfo("Disposing world: unloading all chunks and shutting down thread pool.");

        // Clear any pending chunks in queues
        chunksToUnloadQueue.clear();
        chunksToGenerateQueue.clear();
        chunksToUploadQueue.clear();
        chunksToForceUpdateQueue.clear();

        // Shut down the thread pool (wait for current tasks to finish or interrupt them)
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

        // Dispose all currently loaded chunks immediately upon shutdown
        Set<ChunkCoord> coordsToDispose = new HashSet<>(loadedChunks.keySet());

        for (ChunkCoord coord : coordsToDispose)
        {
            Chunk chunk = loadedChunks.remove(coord); // Remove from map
            if (chunk != null)
            {
                this.renderer.disposeChunkMesh(coord); // Notify renderer to remove GPU mesh
                chunk.dispose(); // Call the chunk's dispose method
                //Debug.log("Disposing chunk: " + coord);
            }
        }
        loadedChunks.clear();
    }

    // -----------------------------------//
    // ---- Chunk Management Methods ----//
    // -----------------------------------//

    /**
     * Updates the loading and unloading queues based on the current player position and render distance. This method is called when the player changes chunks. It identifies which chunks *should* be loaded and which *should* be unloaded.
     */
    private void updateChunkQueues()
    {
        int renderDistance = Game.renderDistance;
        ChunkCoord playerChunkCoords = getChunkCoordinatesForBlock(playerPosition);
        int fixedChunkY = 0;

        Set<ChunkCoord> desiredChunkCoords = new HashSet<>();
        List<ChunkCoord> newChunksToGenerate = new ArrayList<>(); // Use a temporary list

        for (int x = -renderDistance; x <= renderDistance; x++)
        {
            for (int z = -renderDistance; z <= renderDistance; z++)
            {
                ChunkCoord targetChunkCoord = new ChunkCoord(playerChunkCoords.x + x, fixedChunkY, playerChunkCoords.z + z);
                desiredChunkCoords.add(targetChunkCoord);

                // If a desired chunk is NOT already loaded and NOT already in any processing queue, add it to our temporary list.
                if (!loadedChunks.containsKey(targetChunkCoord) && !chunksToGenerateQueue.contains(targetChunkCoord) && !chunksToUploadQueue.stream().anyMatch(res -> res.coord.equals(targetChunkCoord)) &&
                        !chunksToForceUpdateQueue.contains(targetChunkCoord))
                {
                    newChunksToGenerate.add(targetChunkCoord);
                }
            }
        }

        // Sort new chunks to generate by distance from the player
        newChunksToGenerate.sort(Comparator.comparingInt(coord ->
                Math.abs(coord.x - playerChunkCoords.x) + Math.abs(coord.z - playerChunkCoords.z)));

        // Add sorted new chunks to the generation queue
        for (ChunkCoord coord : newChunksToGenerate) {
            chunksToGenerateQueue.add(coord);
        }

        // Identify chunks to unload: those currently loaded but not in the desired set.
        Iterator<Map.Entry<ChunkCoord, Chunk>> iterator = loadedChunks.entrySet().iterator();
        while (iterator.hasNext())
        {
            Map.Entry<ChunkCoord, Chunk> entry = iterator.next();
            ChunkCoord currentLoadedCoord = entry.getKey();

            if (!desiredChunkCoords.contains(currentLoadedCoord))
            {
                if (!chunksToUnloadQueue.contains(currentLoadedCoord))
                {
                    chunksToUnloadQueue.add(currentLoadedCoord);
                }
            }
        }
    }

    /**
     * Orchestrates the asynchronous chunk generation and synchronous GPU upload. This method runs on the main thread.
     */
    private void processChunkQueuesAsync()
    {
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
                    // Debug.logInfo("Force updating chunk mesh: " + coordToUpdate);
                    submitChunkGenerationTask(chunk); // Submit to worker thread for regeneration
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
                Chunk chunk = loadedChunks.remove(coordToUnload); // Remove from map
                if (chunk != null)
                {
                    // Debug.logInfo("Unloading chunk: " + coordToUnload.toString());
                    this.renderer.disposeChunkMesh(coordToUnload); // Notify renderer to free GPU mesh data
                    chunk.dispose(); // Call the Chunk object's dispose method
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
                // Check if this chunk was already loaded by a direct setBlock call
                if (loadedChunks.containsKey(coord))
                {
                    // Skip if already loaded, it means it was force-loaded
                    // Debug.logInfo("Skipping generation of already loaded chunk: " + coord);
                    continue;
                }
                Chunk newChunk = new Chunk(coord); // Create the Chunk object (generates block data)
                loadedChunks.put(coord, newChunk); // Add to map immediately
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
                    // Debug.logInfo("Uploading chunk mesh to GPU for: " + result.coord);
                    // Use the pre-allocated ChunkMesh from the Chunk object
                    chunk.getMesh().uploadToGPU(result.vertices, result.normals);
                    this.renderer.registerChunkMesh(chunk.getCoord(), chunk.getMesh()); // <--- CALL IT HERE!

                    chunk.setIsDirty(false); // Mark as not dirty after successful upload
                    uploadedCount++;
                } else
                {
                    // This can happen if the chunk was unloaded while its mesh was being generated
                    Debug.logWarning("Skipping GPU upload for unloaded chunk: " + result.coord);
                    // No need to free buffers here, they are freed in uploadToGPU's finally block
                }
            }
        }
    }

    /**
     * Submits a chunk's mesh generation task to the background thread pool. This method runs on the main thread.
     *
     * @param chunk The chunk for which to generate mesh data.
     */
    private void submitChunkGenerationTask(Chunk chunk)
    {
        if (chunk == null)
            return;
        final ChunkCoord coord = chunk.getCoord();

        chunkGenerationThreadPool.submit(() -> {
            try
            {
                // These lists are thread-local and temporary for this job
                List<Float> positions = new ArrayList<>();
                List<Float> normals = new ArrayList<>();

                // Create a temporary ChunkMesh instance to call generateMeshData on.
                // It doesn't need to be the actual ChunkMesh object associated with the Chunk,
                // as we're just generating data here.
                // A static utility method could also work to avoid creating objects.
                chunk.getMesh().generateMeshData(chunk, positions, normals); // Pass the chunk itself for block data access

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

                // Add the result to the upload queue for the main thread
                chunksToUploadQueue.add(new ChunkMeshJobResult(coord, vertices, normalData, vertexCount));
            } catch (Exception e)
            {
                Debug.logError("Error generating chunk mesh asynchronously for " + coord + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Gets a chunk by its ChunkCoord.
     *
     * @param coord The coordinates of the chunk.
     * @return The Chunk object if loaded, null otherwise.
     */
    public Chunk getChunk(ChunkCoord coord)
    {
        return loadedChunks.get(coord);
    }

    // -----------------------------------//
    // ------ Block Management ---------//
    // -----------------------------------//

    /**
     * Sets a block at the given world coordinates. If the chunk containing the block is not loaded, it will be loaded. The chunk's mesh will be regenerated and re-uploaded to the renderer.
     *
     * @param blockCoords The block's world coordinates (e.g., from player interaction).
     * @param blockId The ID of the block to set (e.g., BLOCK_TYPE_AIR_ID, BLOCK_TYPE_SOLID_ID).
     */
    public void setBlock(Vector3i blockCoords, short blockId)
    {
        ChunkCoord chunkCoords = getChunkCoordinatesForBlock(blockCoords);
        Chunk chunkToModify = loadedChunks.get(chunkCoords);

        if (chunkToModify == null)
        {
            Debug.logWarning("Attempted to set block in unloaded chunk at: " + chunkCoords.toString() + ". Force loading it now.");
            // If the chunk is not loaded, create it and add to loadedChunks
            chunkToModify = new Chunk(chunkCoords);
            loadedChunks.put(chunkCoords, chunkToModify);
            // It will be added to the force update queue below
        }

        Vector3i blockLocalCoords = getLocalBlockCoordinatesInChunk(blockCoords);

        // Only update if the block ID actually changes
        if (chunkToModify.getBlock(blockLocalCoords) != blockId)
        {
            chunkToModify.setBlock(blockLocalCoords, blockId); // This marks the chunk as dirty
            // Add to force update queue for immediate (next frame) regeneration
            // Ensure it's not already in a generation queue to avoid duplicate work.
            if (!chunksToForceUpdateQueue.contains(chunkCoords) && !chunksToGenerateQueue.contains(chunkCoords) && !chunksToUploadQueue.stream().anyMatch(res -> res.coord.equals(chunkCoords)))
            {
                chunksToForceUpdateQueue.add(chunkCoords);
                // Debug.logInfo("Chunk " + chunkCoords + " added to force update queue.");
            }
        }
    }

    /**
     * Gets the block ID at the given world coordinates.
     *
     * @param blockCoords The block's world coordinates.
     * @return The block ID, or BLOCK_TYPE_AIR_ID if the chunk is not loaded (or an error occurs).
     */
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

    /**
     * Calculates the ChunkCoord for a given world block position. Note: This handles negative coordinates correctly.
     *
     * @param worldBlockPos The world coordinates of a block.
     * @return The ChunkCoord containing that block.
     */
    public ChunkCoord getChunkCoordinatesForBlock(Vector3i worldBlockPos)
    {
        int chunkX = (int) Math.floor((float) worldBlockPos.x / Chunk.CHUNK_SIZE);
        int chunkY = (int) Math.floor((float) worldBlockPos.y / Chunk.CHUNK_SIZE);
        int chunkZ = (int) Math.floor((float) worldBlockPos.z / Chunk.CHUNK_SIZE);
        return new ChunkCoord(chunkX, chunkY, chunkZ);
    }

    /**
     * Calculates the ChunkCoord for a given floating-point world position (e.g., player position).
     *
     * @param worldPos The floating-point world position.
     * @return The ChunkCoord containing that position.
     */
    public ChunkCoord getChunkCoordinatesForBlock(Vector3f worldPos)
    {
        int chunkX = (int) Math.floor(worldPos.x / Chunk.CHUNK_SIZE);
        int chunkY = (int) Math.floor(worldPos.y / Chunk.CHUNK_SIZE);
        int chunkZ = (int) Math.floor(worldPos.z / Chunk.CHUNK_SIZE);
        return new ChunkCoord(chunkX, chunkY, chunkZ);
    }

    /**
     * Calculates the local coordinates of a block within its chunk (0-15).
     *
     * @param worldBlockPos The world coordinates of the block.
     * @return A Vector3i representing the block's local position (0 to CHUNK_SIZE-1).
     */
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

    /**
     * Sets the player's current world position.
     *
     * @param playerPosition The new floating-point world position of the player.
     */
    public void setPlayerPosition(Vector3f playerPosition)
    {
        this.playerPosition.set(playerPosition);
    }
}