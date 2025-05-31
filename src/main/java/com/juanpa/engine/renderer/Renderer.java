// Modify: com/juanpa/engine/renderer/Renderer.java
package com.juanpa.engine.renderer;

import com.juanpa.engine.Debug;
import com.juanpa.engine.world.chunk.Chunk; // Still needed for Chunk.CHUNK_SIZE
import com.juanpa.engine.world.chunk.ChunkCoord;
import com.juanpa.engine.world.chunk.ChunkMesh;
import org.lwjgl.opengl.GL11;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

public class Renderer
{
	// Store loaded ChunkMeshes internally.
	// The ChunkMesh instances are *created and owned* by the Chunk objects,
	// and merely referenced here for rendering.
	private Map<ChunkCoord, ChunkMesh> loadedChunkMeshes;

	private ShaderProgram defaultShader;

	public Renderer()
	{
		this.loadedChunkMeshes = new HashMap<>(); // Initialize the map
		init();
	}

	public void init()
	{
		//GL11.glEnable(GL11.GL_CULL_FACE);
		//GL11.glCullFace(GL11.GL_BACK);
		Skybox.init();
		GL11.glClearColor(0.02f, 0.0f, 0.1f, 1f);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		defaultShader = new ShaderProgram("/shaders/main.vert", "/shaders/main.frag");
	}
	public void render(Matrix4f viewMatrix, Matrix4f projectionMatrix)
	{
		clear();
		Skybox.render(viewMatrix, projectionMatrix);
		renderChunks(viewMatrix, projectionMatrix);
	}

	public void clear()
	{
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
	}

	// --- Updated Method for Renderer ---

	/**
	 * Registers a Chunk's mesh with the Renderer for drawing. The World class is now responsible for ensuring the ChunkMesh has its GPU data uploaded via ChunkMesh.uploadToGPU() before this method is called. This method simply makes the Renderer aware of a mesh it can now draw.
	 *
	 * @param coord The coordinates of the chunk.
	 * @param mesh The ChunkMesh object (owned by the Chunk) that has already had its GPU data uploaded.
	 */
	public void registerChunkMesh(ChunkCoord coord, ChunkMesh mesh)
	{
		if (mesh == null)
		{
			Debug.logWarning("Attempted to register null ChunkMesh for coord: " + coord);
			return;
		}
		loadedChunkMeshes.put(coord, mesh);
		// Debug.log"Renderer registered mesh for chunk: " + coord);
	}

	/**
	 * Disposes of a chunk's mesh from GPU memory and removes it from the renderer's tracking. This method is called by the World when a chunk is fully unloaded.
	 *
	 * @param coord The coordinates of the chunk to remove.
	 */
	public void disposeChunkMesh(ChunkCoord coord)
	{
		ChunkMesh mesh = loadedChunkMeshes.remove(coord);
		if (mesh != null)
		{
			mesh.cleanup(); // Calls the ChunkMesh's OpenGL cleanup
			// Debug.log"Renderer disposed and unregistered mesh for chunk: " + coord);
		} else
		{
			//Debug.logWarning("Attempted to dispose unknown ChunkMesh for coord: " + coord);
		}
	}

	/**
	 * Renders all currently loaded chunks. The CameraSystem (or RenderingSystem) will pass the active camera matrices.
	 *
	 * @param viewMatrix The camera's view matrix.
	 * @param projectionMatrix The camera's projection matrix.
	 */
	public void renderChunks(Matrix4f viewMatrix, Matrix4f projectionMatrix)
	{
		// Activate shader program
		defaultShader.use();
		defaultShader.setUniform("viewMatrix", viewMatrix);
		defaultShader.setUniform("projectionMatrix", projectionMatrix);

		for (Map.Entry<ChunkCoord, ChunkMesh> entry : loadedChunkMeshes.entrySet())
		{
			ChunkCoord chunkCoord = entry.getKey();
			ChunkMesh mesh = entry.getValue();

			// Calculate model matrix for this chunk based on its world position
			Matrix4f modelMatrix = new Matrix4f().translate(chunkCoord.x * Chunk.CHUNK_SIZE, chunkCoord.y * Chunk.CHUNK_SIZE, chunkCoord.z * Chunk.CHUNK_SIZE);
			defaultShader.setUniform("modelMatrix", modelMatrix);

			mesh.bind();
			mesh.render(); // This will only draw if vertexCount > 0
			mesh.unbind();
		}
		defaultShader.unuse();
	}
}