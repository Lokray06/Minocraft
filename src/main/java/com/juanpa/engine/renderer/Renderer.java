// Modify: com/juanpa/engine/renderer/Renderer.java
package com.juanpa.engine.renderer;

import com.juanpa.engine.Debug;
import com.juanpa.engine.world.chunk.Chunk;
import com.juanpa.engine.world.chunk.ChunkCoord;
import com.juanpa.engine.world.chunk.ChunkMesh; // Import ChunkMesh
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL31; // For glDrawArraysInstanced
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

public class Renderer
{
	private Map<ChunkCoord, ChunkMesh> loadedChunkMeshes;
	private ShaderProgram defaultShader;

	public Renderer() {
		this.loadedChunkMeshes = new HashMap<>();
		init();
	}

	public void init() {
		ChunkMesh.initBaseQuad(); // Initialize the shared base quad VBO

		Skybox.init(); // Assuming Skybox is separate
		GL11.glClearColor(0.02f, 0.0f, 0.1f, 1f);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		// GL11.glEnable(GL11.GL_CULL_FACE); // Optional: Good for performance
		// GL11.glCullFace(GL11.GL_BACK);

		defaultShader = new ShaderProgram("/shaders/main.vert", "/shaders/main.frag");
		Debug.logInfo("Renderer initialized.");
	}

	public void render(Matrix4f viewMatrix, Matrix4f projectionMatrix) {
		clear();
		Skybox.render(viewMatrix, projectionMatrix); // Render skybox first (usually)
		renderChunksInstanced(viewMatrix, projectionMatrix);
	}

	public void clear() {
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
	}

	public void registerChunkMesh(ChunkCoord coord, ChunkMesh mesh) {
		if (mesh == null) {
			Debug.logWarning("Attempted to register null ChunkMesh for coord: " + coord);
			return;
		}
		loadedChunkMeshes.put(coord, mesh);
	}

	public void disposeChunkMesh(ChunkCoord coord) {
		ChunkMesh mesh = loadedChunkMeshes.remove(coord);
		if (mesh != null) {
			mesh.cleanup(); // Calls the ChunkMesh's OpenGL cleanup
		}
	}

	public void renderChunksInstanced(Matrix4f viewMatrix, Matrix4f projectionMatrix) {
		defaultShader.use();
		defaultShader.setUniform("viewMatrix", viewMatrix);
		defaultShader.setUniform("projectionMatrix", projectionMatrix);

		for (Map.Entry<ChunkCoord, ChunkMesh> entry : loadedChunkMeshes.entrySet()) {
			ChunkCoord chunkCoord = entry.getKey();
			ChunkMesh mesh = entry.getValue();

			if (mesh.getInstanceCount() > 0) {
				// Calculate model matrix for this chunk based on its world position
				Matrix4f modelMatrix = new Matrix4f().translate(
						chunkCoord.x * Chunk.CHUNK_SIZE,
						chunkCoord.y * Chunk.CHUNK_SIZE,
						chunkCoord.z * Chunk.CHUNK_SIZE
				);
				defaultShader.setUniform("modelMatrix", modelMatrix);

				mesh.bindInstancedVAO(); // Binds the VAO configured for instancing

				// Draw 4 vertices (for the base quad, using TRIANGLE_STRIP)
				// repeated 'mesh.getInstanceCount()' times.
				GL31.glDrawArraysInstanced(GL11.GL_TRIANGLE_STRIP, 0, 4, mesh.getInstanceCount());

				mesh.unbindInstancedVAO();
			}
		}
		defaultShader.unuse();
	}

	// Call this from Engine's cleanup
	public void cleanup() {
		Debug.logInfo("Renderer cleaning up.");
		ChunkMesh.cleanupBaseQuad(); // Cleanup shared base quad VBO
		if (defaultShader != null) {
			defaultShader.cleanup(); // Assuming ShaderProgram has a cleanup method
		}
		// loadedChunkMeshes are cleaned up via disposeChunkMesh when world unloads them
	}
}