// Modify: com/juanpa/engine/world/World.java
package com.juanpa.engine.world;

import com.juanpa.engine.Debug;
import com.juanpa.engine.components.Camera;
import com.juanpa.engine.renderer.Renderer;
import com.juanpa.engine.world.chunk.Chunk;
import com.juanpa.engine.world.chunk.ChunkCoord;
import com.juanpa.engine.world.chunk.ChunkMesh; // Needed for QuadInstanceData
import com.juanpa.engine.world.chunk.InstancedChunkMeshJobResult;
import com.juanpa.game.Game; // Import Game to access renderDistanceChunks
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
	private static final int WORLD_MAX_BLOCK_Y = 16 * 8; // Should be 140

	// Then, the chunk bounds will be:
	private static final int WORLD_MIN_CHUNK_Y = WORLD_MIN_BLOCK_Y / Chunk.CHUNK_SIZE; // 0
	private static final int WORLD_MAX_CHUNK_Y = WORLD_MAX_BLOCK_Y / Chunk.CHUNK_SIZE; // 140 / 16 = 8 (approx)
	// This will ensure chunks from Y=0 to Y=8 are loaded.

	// --- Fields ---
	private long seed;
	private Map<ChunkCoord, Chunk> loadedChunks;
	private Renderer renderer;

	private Vector3f playerPosition;
	private ChunkCoord lastPlayerChunkCoord; // This will now typically only store XZ, or just be updated to reflect current player's XZ chunk

	private Queue<ChunkCoord> chunksToUnloadQueue;
	private Queue<InstancedChunkMeshJobResult> chunksToUploadQueue;
	private Queue<ChunkCoord> chunksToForceUpdateQueue;
	private Queue<ChunkCoord> chunksToGenerateQueue; // Moved here for logical grouping

	private ExecutorService chunkGenerationThreadPool;
	private static final int CHUNKS_PER_FRAME_PROCESS_LIMIT = 2;
	private static final int CHUNKS_PER_FRAME_GENERATE_LIMIT = 2;

	// ----------------------//
	// ---- Constructor ----//
	// ----------------------//
	public World(long generationSeed, Renderer renderer)
	{
		this.seed = generationSeed;
		this.renderer = renderer;
		this.loadedChunks = new HashMap<>();

		this.playerPosition = new Vector3f(0.0f, 0.0f, 0.0f);
		// For lastPlayerChunkCoord, only consider XZ for movement updates, but store the full Y
		// so that it reflects the actual chunk the player is in.
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
		// Use Game.renderDistanceChunks which is calculated based on radial distance
		int renderDistance = Game.renderDistance;
		ChunkCoord playerChunkCoords = getChunkCoordinatesForBlock(playerPosition);

		List<ChunkCoord> potentialChunksToGenerate = new ArrayList<>();

		// Iterate over a slightly larger square bounding box to find all chunks within radial distance
		// Adding 2 to renderDistance to ensure we cover all corners when CHUNK_SIZE is large
		// This range is for X and Z
		for(int x = -renderDistance - 2; x <= renderDistance + 2; x++)
		{
			for(int z = -renderDistance - 2; z <= renderDistance + 2; z++)
			{
				// Calculate the 2D (XZ) squared distance from the player's XZ chunk
				double dx = (double) x;
				double dz = (double) z;
				double distSqXZ = dx * dx + dz * dz;

				// Square of the render distance to avoid sqrt calculation, which is faster
				if(distSqXZ > (renderDistance * renderDistance))
				{
					continue; // Skip if outside radial XZ distance
				}
				// -----------------------------------------

				// Loop through all relevant Y-levels for terrain
				// Y-level is independent of the XZ radial check
				for(int y = WORLD_MIN_CHUNK_Y; y <= WORLD_MAX_CHUNK_Y; y++)
				{
					ChunkCoord targetChunkCoord = new ChunkCoord(playerChunkCoords.x + x, y,
							playerChunkCoords.z + z);


					// Load/Generate chunk only if it's not already loaded and not already in a queue
					if(!loadedChunks.containsKey(targetChunkCoord) &&
							!chunksToGenerateQueue.contains(targetChunkCoord) &&
							!chunksToUploadQueue.stream().anyMatch(res -> res.coord.equals(targetChunkCoord)) &&
							!chunksToForceUpdateQueue.contains(targetChunkCoord))
					{
						potentialChunksToGenerate.add(targetChunkCoord);
					}
				}
			}
		}

		// Sort chunks by XZ distance from the player chunk (Euclidean distance for better radial prioritization)
		// The Y-coordinate in the sort comparator for distanceSqXZ will be ignored implicitly
		// because distanceSqXZ only calculates based on x and z.
		potentialChunksToGenerate.sort(Comparator.comparingDouble(coord -> {
			long dx = (long) coord.x - playerChunkCoords.x;
			long dz = (long) coord.z - playerChunkCoords.z;
			return (double) (dx * dx + dz * dz);
		}));

		for(ChunkCoord coord : potentialChunksToGenerate)
		{
			chunksToGenerateQueue.add(coord);
		}
		Debug.logInfo("Enqueued " + potentialChunksToGenerate.size() + " initial chunks for radial XZ generation.");
	}

	public void update()
	{
		Camera activeCamera = Game.getActiveCamera();
		if(activeCamera == null)
		{
			Debug.logWarning("No main camera found for frustum culling. Skipping culling for this frame.");
			return;
		}

		ChunkCoord currentPlayerChunk = getChunkCoordinatesForBlock(playerPosition);

		// Only update chunk queues if player has moved to a new XZ chunk
		// We create a temporary ChunkCoord for comparison that ignores Y,
		// or you can simply compare playerChunkCoords.x and playerChunkCoords.z
		// with lastPlayerChunkCoord.x and lastPlayerChunkCoord.z
		if(currentPlayerChunk.x != lastPlayerChunkCoord.x || currentPlayerChunk.z != lastPlayerChunkCoord.z)
		{
			updateChunkQueues();
			lastPlayerChunkCoord = currentPlayerChunk; // Update lastPlayerChunkCoord with the new XZ
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
			if(!chunkGenerationThreadPool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS))
			{
				Debug.logWarning("Chunk generation thread pool did not terminate in time, forcing shutdown.");
				chunkGenerationThreadPool.shutdownNow();
			}
		}
		catch(InterruptedException e)
		{
			Debug.logError("Chunk generation thread pool termination interrupted: " + e.getMessage());
			chunkGenerationThreadPool.shutdownNow();
		}

		Set<ChunkCoord> coordsToDispose = new HashSet<>(loadedChunks.keySet());

		for(ChunkCoord coord : coordsToDispose)
		{
			Chunk chunk = loadedChunks.remove(coord);
			if(chunk != null)
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
		int renderDistance = Game.renderDistance;
		ChunkCoord playerChunkCoords = getChunkCoordinatesForBlock(playerPosition); // Current player's chunk (includes Y)

		Set<ChunkCoord> desiredChunkCoords = new HashSet<>();
		List<ChunkCoord> newChunksToGenerate = new ArrayList<>();

		// Iterate over a slightly larger square bounding box for checking
		for(int x = -renderDistance - 2; x <= renderDistance + 2; x++)
		{
			for(int z = -renderDistance - 2; z <= renderDistance + 2; z++)
			{
				// Calculate the 2D (XZ) squared distance from the player's XZ chunk
				double dx = (double) x;
				double dz = (double) z;
				double distSqXZ = dx * dx + dz * dz;

				if(distSqXZ <= (renderDistance * renderDistance))
				{ // Include if within XZ radial distance
					// Loop through all relevant Y-levels for terrain
					for(int y = WORLD_MIN_CHUNK_Y; y <= WORLD_MAX_CHUNK_Y; y++)
					{
						ChunkCoord targetChunkCoord = new ChunkCoord(playerChunkCoords.x + x, y,
								playerChunkCoords.z + z);
						desiredChunkCoords.add(targetChunkCoord);

						if(!loadedChunks.containsKey(targetChunkCoord) &&
								!chunksToGenerateQueue.contains(targetChunkCoord) &&
								!chunksToUploadQueue.stream().anyMatch(res -> res.coord.equals(targetChunkCoord)) &&
								!chunksToForceUpdateQueue.contains(targetChunkCoord))
						{
							newChunksToGenerate.add(targetChunkCoord);
						}
					}
				}
			}
		}

		// Sort new chunks to generate by XZ distance from the player
		newChunksToGenerate.sort(Comparator.comparingDouble(coord -> {
			long dx = (long) coord.x - playerChunkCoords.x;
			long dz = (long) coord.z - playerChunkCoords.z;
			return (double) (dx * dx + dz * dz);
		}));

		for(ChunkCoord coord : newChunksToGenerate)
		{
			chunksToGenerateQueue.add(coord);
		}

		// Identify chunks to unload: those currently loaded but not in the desired set.
		Iterator<Map.Entry<ChunkCoord, Chunk>> iterator = loadedChunks.entrySet().iterator();
		while(iterator.hasNext())
		{
			Map.Entry<ChunkCoord, Chunk> entry = iterator.next();
			ChunkCoord currentLoadedCoord = entry.getKey();

			if(!desiredChunkCoords.contains(currentLoadedCoord))
			{
				if(!chunksToUnloadQueue.contains(currentLoadedCoord))
				{
					chunksToUnloadQueue.add(currentLoadedCoord);
				}
			}
		}
	}

	private void processChunkQueuesAsync()
	{
		int processedCount = 0;

		// 1. Process forced updates
		while(!chunksToForceUpdateQueue.isEmpty() && processedCount < CHUNKS_PER_FRAME_PROCESS_LIMIT)
		{
			ChunkCoord coordToUpdate = chunksToForceUpdateQueue.poll();
			if(coordToUpdate != null)
			{
				Chunk chunk = loadedChunks.get(coordToUpdate);
				if(chunk != null)
				{
					submitChunkGenerationTask(chunk); // Re-submit for mesh gen
					processedCount++;
				}
			}
		}

		// 2. Process unloading tasks
		while(!chunksToUnloadQueue.isEmpty() && processedCount < CHUNKS_PER_FRAME_PROCESS_LIMIT)
		{
			ChunkCoord coordToUnload = chunksToUnloadQueue.poll();
			if(coordToUnload != null)
			{
				Chunk chunk = loadedChunks.remove(coordToUnload);
				if(chunk != null)
				{
					this.renderer.disposeChunkMesh(coordToUnload); // Renderer needs to know about the ChunkMesh instance
					chunk.dispose(); // Chunk disposes its own ChunkMesh
					processedCount++;
				}
			}
		}

		// 3. Submit new chunk generation tasks
		int submittedCount = 0;
		while(!chunksToGenerateQueue.isEmpty() && submittedCount < CHUNKS_PER_FRAME_GENERATE_LIMIT)
		{
			ChunkCoord coord = chunksToGenerateQueue.poll();
			if(coord != null)
			{
				if(loadedChunks.containsKey(coord))
				{
					continue;
				}
				Chunk newChunk = new Chunk(coord);
				loadedChunks.put(coord, newChunk);
				submitChunkGenerationTask(newChunk);
				submittedCount++;
			}
		}

		// 4. Process completed mesh data ready for GPU upload
		int uploadedCount = 0;
		while(!chunksToUploadQueue.isEmpty() && uploadedCount < CHUNKS_PER_FRAME_PROCESS_LIMIT)
		{
			InstancedChunkMeshJobResult result = chunksToUploadQueue.poll(); // Use new result type
			if(result != null)
			{
				Chunk chunk = loadedChunks.get(result.coord);
				if(chunk != null)
				{
					// Pass the prepared instance data directly
					chunk.getMesh().uploadToGPU(result.instanceData); // New signature
					this.renderer.registerChunkMesh(chunk.getCoord(), chunk.getMesh());
					chunk.setIsDirty(false);
					uploadedCount++;
				}
			}
		}
	}

	private void submitChunkGenerationTask(Chunk chunk)
	{
		if(chunk == null)
			return;
		final ChunkCoord coord = chunk.getCoord();

		chunkGenerationThreadPool.submit(() ->
		{
			try
			{
				// Use the new signature for generateMeshData
				List<ChunkMesh.QuadInstanceData> instanceDataList = new ArrayList<>();
				chunk.getMesh().generateMeshData(chunk, instanceDataList); // New signature

				// Add to upload queue using the new result type
				chunksToUploadQueue.add(new InstancedChunkMeshJobResult(coord, instanceDataList)); // Add new result type
			}
			catch(Exception e)
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

		if(chunkToModify == null)
		{
			// If the chunk is not loaded, create it and add to loadedChunks
			Debug.logWarning("Attempted to set block in unloaded chunk at: " + chunkCoords.toString() + ". Force loading it now.");
			chunkToModify = new Chunk(chunkCoords);
			loadedChunks.put(chunkCoords, chunkToModify);
		}

		Vector3i blockLocalCoords = getLocalBlockCoordinatesInChunk(blockCoords);

		if(chunkToModify.getBlock(blockLocalCoords) != blockId)
		{
			chunkToModify.setBlock(blockLocalCoords, blockId);
			if(!chunksToForceUpdateQueue.contains(chunkCoords) && !chunksToGenerateQueue.contains(chunkCoords) && !chunksToUploadQueue.stream().anyMatch(res -> res.coord.equals(chunkCoords)))
			{
				chunksToForceUpdateQueue.add(chunkCoords);
			}
		}
	}

	public short getBlock(Vector3i blockCoords)
	{
		ChunkCoord chunkCoords = getChunkCoordinatesForBlock(blockCoords);
		Chunk chunk = loadedChunks.get(chunkCoords);

		if(chunk == null)
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