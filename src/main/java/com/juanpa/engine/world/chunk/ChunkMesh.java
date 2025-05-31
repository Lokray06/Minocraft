// Modify: com/juanpa/engine/world/chunk/ChunkMesh.java
package com.juanpa.engine.world.chunk;

import com.juanpa.engine.Debug;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31; // For glDrawArraysInstanced
import org.lwjgl.opengl.GL33; // For glVertexAttribDivisor
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList; // For generating lists of instance data
import java.util.List;

// Helper class to store data for each instance (each greedy quad)
// We'll pack this into a FloatBuffer for the VBO
// Layout: origin(3 floats), dimensions(2 floats), normalID(1 float), blockTypeID(1 float) = 7 floats
public class ChunkMesh
{
	private int vaoID; // This VAO will combine base quad VBO and instance VBO
	private int instanceVBOID;
	private int instanceCount;

	private static final short BLOCK_TYPE_AIR_ID = 0;

	// --- Static Base Quad for Instancing ---
	private static int baseQuadVBO = -1; // Shared VBO for a 2D unit quad
	// Vertices for a 1x1 quad (0,0) to (1,1) in 2D.
	// The vertex shader will use these 2D coords to construct the 3D quad.
	private static final float[] BASE_QUAD_VERTICES = {
			0.0f, 0.0f,  // Bottom-left
			1.0f, 0.0f,  // Bottom-right
			0.0f, 1.0f,  // Top-left
			1.0f, 1.0f   // Top-right
			// We'll draw this as GL_TRIANGLE_STRIP, so 4 vertices are enough.
	};

	// Call this once at engine startup (e.g., from Renderer.init())
	public static void initBaseQuad()
	{
		if(baseQuadVBO != -1)
			return; // Already initialized

		FloatBuffer buffer = MemoryUtil.memAllocFloat(BASE_QUAD_VERTICES.length);
		buffer.put(BASE_QUAD_VERTICES).flip();

		baseQuadVBO = GL15.glGenBuffers();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, baseQuadVBO);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0); // Unbind
		MemoryUtil.memFree(buffer);
		Debug.logInfo("Base quad VBO initialized: " + baseQuadVBO);
	}

	// Call this once at engine shutdown
	public static void cleanupBaseQuad()
	{
		if(baseQuadVBO != -1)
		{
			GL15.glDeleteBuffers(baseQuadVBO);
			baseQuadVBO = -1;
			Debug.logInfo("Base quad VBO cleaned up.");
		}
	}
	// --- End Static Base Quad ---


	// Face normal templates (remains the same)
	private static final float[] NORMAL_FRONT_F = {0.0f, 0.0f, 1.0f};
	private static final float[] NORMAL_BACK_F = {0.0f, 0.0f, -1.0f};
	private static final float[] NORMAL_LEFT_F = {-1.0f, 0.0f, 0.0f};
	private static final float[] NORMAL_RIGHT_F = {1.0f, 0.0f, 0.0f};
	private static final float[] NORMAL_TOP_F = {0.0f, 1.0f, 0.0f};
	private static final float[] NORMAL_BOTTOM_F = {0.0f, -1.0f, 0.0f};

	// Corresponding Normal IDs
	private static final byte NORMAL_ID_FRONT = 0;
	private static final byte NORMAL_ID_BACK = 1;
	private static final byte NORMAL_ID_LEFT = 2;
	private static final byte NORMAL_ID_RIGHT = 3;
	private static final byte NORMAL_ID_TOP = 4;
	private static final byte NORMAL_ID_BOTTOM = 5;


	public ChunkMesh()
	{
		this.vaoID = GL30.glGenVertexArrays();
		this.instanceVBOID = GL15.glGenBuffers();
		this.instanceCount = 0;
	}

	// generateMeshData now populates a list of QuadInstanceData objects
	public void generateMeshData(Chunk rawChunkData, List<QuadInstanceData> outInstanceData)
	{
		outInstanceData.clear();
		short[][][] blocks = rawChunkData.blocks;

		// Parameters for greedyMesh: blocks, outInstanceData, axis, u_axis, v_axis, normal_ID, normal_float_vec_for_origin_calc
		greedyMesh(blocks, outInstanceData, 0, 1, 2, NORMAL_ID_RIGHT, NORMAL_RIGHT_F);  // +X
		greedyMesh(blocks, outInstanceData, 0, 1, 2, NORMAL_ID_LEFT, NORMAL_LEFT_F);   // -X
		greedyMesh(blocks, outInstanceData, 1, 0, 2, NORMAL_ID_TOP, NORMAL_TOP_F);      // +Y
		greedyMesh(blocks, outInstanceData, 1, 0, 2, NORMAL_ID_BOTTOM, NORMAL_BOTTOM_F);// -Y
		greedyMesh(blocks, outInstanceData, 2, 0, 1, NORMAL_ID_FRONT, NORMAL_FRONT_F);  // +Z
		greedyMesh(blocks, outInstanceData, 2, 0, 1, NORMAL_ID_BACK, NORMAL_BACK_F);   // -Z
	}

	private void greedyMesh(short[][][] blocks, List<QuadInstanceData> outInstanceData,
							int axis, int u_axis, int v_axis,
							byte normalID, float[] normalFloatVec)
	{
		// (Greedy meshing logic to find width, height, start_u, start_v, start_i for each quad)
		// ... (similar to before, but calls addGreedyQuadInstance at the end) ...
		int i_axis_size = Chunk.CHUNK_SIZE;
		int u_axis_size = Chunk.CHUNK_SIZE;
		int v_axis_size = Chunk.CHUNK_SIZE;
		short[][] mask = new short[u_axis_size][v_axis_size];

		for(int i_slice = 0; i_slice < i_axis_size; i_slice++)
		{
			// Build mask for the current slice
			for(int u = 0; u < u_axis_size; u++)
			{
				for(int v = 0; v < v_axis_size; v++)
				{
					int[] currBlockCoords = new int[3];
					currBlockCoords[axis] = i_slice;
					currBlockCoords[u_axis] = u;
					currBlockCoords[v_axis] = v;

					int nextX = currBlockCoords[0] + (int) normalFloatVec[0];
					int nextY = currBlockCoords[1] + (int) normalFloatVec[1];
					int nextZ = currBlockCoords[2] + (int) normalFloatVec[2];

					short currentBlock = getBlockSafe(blocks, currBlockCoords[0], currBlockCoords[1], currBlockCoords[2]);
					short adjacentBlock = getBlockSafe(blocks, nextX, nextY, nextZ);

					if(currentBlock != BLOCK_TYPE_AIR_ID && adjacentBlock == BLOCK_TYPE_AIR_ID)
					{
						mask[u][v] = currentBlock;
					}
					else
					{
						mask[u][v] = BLOCK_TYPE_AIR_ID;
					}
				}
			}

			// Iterate through the mask to find quads
			for(int u_start = 0; u_start < u_axis_size; u_start++)
			{
				for(int v_start = 0; v_start < v_axis_size; )
				{
					short blockTypeForFace = mask[u_start][v_start];
					if(blockTypeForFace != BLOCK_TYPE_AIR_ID)
					{
						int quadWidth = 1; // Extent along v_axis
						while(v_start + quadWidth < v_axis_size && mask[u_start][v_start + quadWidth] == blockTypeForFace)
						{
							quadWidth++;
						}

						int quadHeight = 1; // Extent along u_axis
						boolean canExpandHeight = true;
						while(u_start + quadHeight < u_axis_size && canExpandHeight)
						{
							for(int k = 0; k < quadWidth; k++)
							{
								if(mask[u_start + quadHeight][v_start + k] != blockTypeForFace)
								{
									canExpandHeight = false;
									break;
								}
							}
							if(canExpandHeight)
							{
								quadHeight++;
							}
						}

						// Add one instance for this merged quad
						addGreedyQuadInstance(outInstanceData, i_slice, u_start, v_start,
								quadHeight, quadWidth, // Note: height along u_axis, width along v_axis
								axis, u_axis, v_axis,
								normalID, (byte) blockTypeForFace, normalFloatVec);

						for(int eu = u_start; eu < u_start + quadHeight; eu++)
						{
							for(int ev = v_start; ev < v_start + quadWidth; ev++)
							{
								mask[eu][ev] = BLOCK_TYPE_AIR_ID;
							}
						}
						v_start += quadWidth;
					}
					else
					{
						v_start++;
					}
				}
			}
		}
	}

	private short getBlockSafe(short[][][] blocks, int x, int y, int z)
	{
		if(x < 0 || x >= Chunk.CHUNK_SIZE || y < 0 || y >= Chunk.CHUNK_SIZE || z < 0 || z >= Chunk.CHUNK_SIZE)
		{
			return BLOCK_TYPE_AIR_ID;
		}
		return blocks[x][y][z];
	}


	private void addGreedyQuadInstance(List<QuadInstanceData> outInstanceData,
									   int i_slice, int u_start, int v_start,
									   int quadHeight, int quadWidth, // height along u_axis, width along v_axis
									   int axis, int u_axis, int v_axis,
									   byte normalID, byte blockTypeID, float[] normalFloatVec)
	{
		float ox = 0, oy = 0, oz = 0;

		// Determine origin (bottom-left corner of the quad in chunk space)
		// The origin is the (0,0) point for the 2D base quad.
		float[] originCoords = new float[3];
		originCoords[u_axis] = u_start;
		originCoords[v_axis] = v_start;

		// If normal points in positive direction of 'axis', plane is at i_slice + 1
		// If normal points in negative direction of 'axis', plane is at i_slice
		if(normalFloatVec[axis] > 0)
		{ // Positive normal direction
			originCoords[axis] = i_slice + 1.0f;
		}
		else
		{ // Negative normal direction
			originCoords[axis] = i_slice;
		}
		ox = originCoords[0];
		oy = originCoords[1];
		oz = originCoords[2];

		outInstanceData.add(new QuadInstanceData(ox, oy, oz, (float) quadHeight, (float) quadWidth, normalID, blockTypeID));
	}


	// uploadToGPU now takes the list of instance data
	public void uploadToGPU(List<QuadInstanceData> instanceDataList)
	{
		this.instanceCount = instanceDataList.size();
		if(this.instanceCount == 0)
		{
			return; // No instances to render
		}

		FloatBuffer instanceBuffer = MemoryUtil.memAllocFloat(this.instanceCount * QuadInstanceData.FLOAT_COUNT);
		for(QuadInstanceData data : instanceDataList)
		{
			data.writeToBuffer(instanceBuffer);
		}
		instanceBuffer.flip();

		GL30.glBindVertexArray(this.vaoID);

		// 1. Base Quad Vertex Buffer (Attribute 0 for vertex position)
		// This VBO is static and shared, bound externally or assumed to be bound
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, baseQuadVBO);
		GL20.glEnableVertexAttribArray(0); // Shader location 0: vec2 a_baseVertexPos
		GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 0, 0);

		// 2. Instance Data Buffer (Attributes 1 onwards)
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.instanceVBOID);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, instanceBuffer, GL15.GL_STATIC_DRAW); // Use STATIC_DRAW if mesh changes infrequently, DYNAMIC_DRAW if often

		int stride = QuadInstanceData.FLOAT_COUNT * Float.BYTES; // 7 floats * 4 bytes each
		long offset = 0;

		// Attribute 1: vec3 i_origin (originX, originY, originZ)
		GL20.glEnableVertexAttribArray(1);
		GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, stride, offset);
		GL33.glVertexAttribDivisor(1, 1); // Data per instance
		offset += 3 * Float.BYTES;

		// Attribute 2: vec2 i_dimensions (dimensionH, dimensionW)
		GL20.glEnableVertexAttribArray(2);
		GL20.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, stride, offset);
		GL33.glVertexAttribDivisor(2, 1);
		offset += 2 * Float.BYTES;

		// Attribute 3: uint i_normalID (normalID_float)
		GL20.glEnableVertexAttribArray(3);
		GL20.glVertexAttribPointer(3, 1, GL11.GL_FLOAT, false, stride, offset); // Sending as float, shader can cast to uint
		GL33.glVertexAttribDivisor(3, 1);
		offset += 1 * Float.BYTES;

		// Attribute 4: uint i_blockTypeID (blockTypeID_float)
		GL20.glEnableVertexAttribArray(4);
		GL20.glVertexAttribPointer(4, 1, GL11.GL_FLOAT, false, stride, offset); // Sending as float, shader can cast to uint
		GL33.glVertexAttribDivisor(4, 1);
		// offset += 1 * Float.BYTES; // No more attributes after this for now

		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		GL30.glBindVertexArray(0);

		MemoryUtil.memFree(instanceBuffer);
		Debug.checkGLError("ChunkMesh: uploadToGPU (Instanced)");
	}

	// Renamed to reflect it's for instanced setup
	public void bindInstancedVAO()
	{
		GL30.glBindVertexArray(this.vaoID);
	}

	public void unbindInstancedVAO()
	{
		GL30.glBindVertexArray(0);
	}

	public int getInstanceCount()
	{
		return instanceCount;
	}

	public void cleanup()
	{
		if(vaoID != 0)
		{ // Check vaoID as it's created in constructor
			GL30.glBindVertexArray(0); // Unbind before deleting
			GL15.glDeleteBuffers(this.instanceVBOID);
			GL30.glDeleteVertexArrays(this.vaoID);
			vaoID = 0;
			instanceVBOID = 0;
			instanceCount = 0;
		}
		// Static baseQuadVBO is cleaned up separately by `cleanupBaseQuad()`
	}

	public class QuadInstanceData
	{
		float originX, originY, originZ;
		float dimensionH; // Corresponds to 'height' in greedy meshing (u-axis extent)
		float dimensionW; // Corresponds to 'width' in greedy meshing (v-axis extent)
		float normalID_float; // Store as float for easy VBO
		float blockTypeID_float; // Store as float for easy VBO

		public static final int FLOAT_COUNT = 7;

		public QuadInstanceData(float ox, float oy, float oz, float dH, float dW, byte normalID, byte blockTypeID)
		{
			this.originX = ox;
			this.originY = oy;
			this.originZ = oz;
			this.dimensionH = dH; // Greedy height
			this.dimensionW = dW; // Greedy width
			this.normalID_float = normalID;
			this.blockTypeID_float = blockTypeID;
		}

		public void writeToBuffer(FloatBuffer buffer)
		{
			buffer.put(originX).put(originY).put(originZ);
			buffer.put(dimensionH).put(dimensionW);
			buffer.put(normalID_float).put(blockTypeID_float);
		}
	}

}