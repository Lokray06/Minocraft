// Modify: com/juanpa/engine/world/chunk/ChunkMesh.java
package com.juanpa.engine.world.chunk;

import com.juanpa.engine.Debug;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.ByteBuffer; // Import ByteBuffer
import java.util.List; // Keep List, but parameters to generateMeshData will change

public class ChunkMesh
{
    private int vaoID;
    private int vboPositionsID; // VBO for positions
    private int vboNormalsID;   // VBO for compressed normal IDs
    private int vboBlockTypesID; // VBO for block types
    private int vertexCount;

    private static final short BLOCK_TYPE_AIR_ID = 0;

    // Face normal templates (remains the same)
    private static final float[] NORMAL_FRONT = { 0.0f, 0.0f, 1.0f }; // ID 0
    private static final float[] NORMAL_BACK = { 0.0f, 0.0f, -1.0f };  // ID 1
    private static final float[] NORMAL_LEFT = { -1.0f, 0.0f, 0.0f };  // ID 2
    private static final float[] NORMAL_RIGHT = { 1.0f, 0.0f, 0.0f }; // ID 3
    private static final float[] NORMAL_TOP = { 0.0f, 1.0f, 0.0f };    // ID 4
    private static final float[] NORMAL_BOTTOM = { 0.0f, -1.0f, 0.0f };// ID 5

    // Quad vertices templates (remains the same)
    private static final float[] QUAD_VERTICES_FRONT = { 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f };
    private static final float[] QUAD_VERTICES_BACK = { 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f };
    private static final float[] QUAD_VERTICES_LEFT = { 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f };
    private static final float[] QUAD_VERTICES_RIGHT = { 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f };
    private static final float[] QUAD_VERTICES_TOP = { 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, 0.0f };
    private static final float[] QUAD_VERTICES_BOTTOM = { 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f };


    public ChunkMesh()
    {
        this.vaoID = GL30.glGenVertexArrays();
        Debug.checkGLError("ChunkMesh: glGenVertexArrays");
        this.vboPositionsID = GL15.glGenBuffers();
        Debug.checkGLError("ChunkMesh: glGenBuffers (Positions VBO)");
        this.vboNormalsID = GL15.glGenBuffers();
        Debug.checkGLError("ChunkMesh: glGenBuffers (Normals VBO)");
        this.vboBlockTypesID = GL15.glGenBuffers();
        Debug.checkGLError("ChunkMesh: glGenBuffers (BlockTypes VBO)");
    }

    // Helper to convert float normal to byte ID
    private byte encodeNormal(float[] normal) {
        if (normal == NORMAL_FRONT) return 0;
        if (normal == NORMAL_BACK) return 1;
        if (normal == NORMAL_LEFT) return 2;
        if (normal == NORMAL_RIGHT) return 3;
        if (normal == NORMAL_TOP) return 4;
        if (normal == NORMAL_BOTTOM) return 5;
        return 0; // Default/error
    }

    /**
     * Generates mesh data. Populates the provided lists.
     * @param rawChunkData The Chunk object.
     * @param outPositions List to populate with vertex positions (floats).
     * @param outNormalIDs List to populate with compressed normal IDs (bytes).
     * @param outBlockTypes List to populate with block type IDs (bytes).
     */
    public void generateMeshData(Chunk rawChunkData, List<Float> outPositions, List<Byte> outNormalIDs, List<Byte> outBlockTypes)
    {
        outPositions.clear();
        outNormalIDs.clear();
        outBlockTypes.clear();

        short[][][] blocks = rawChunkData.blocks;

        greedyMesh(blocks, outPositions, outNormalIDs, outBlockTypes, 0, 1, 2, QUAD_VERTICES_RIGHT, NORMAL_RIGHT);
        greedyMesh(blocks, outPositions, outNormalIDs, outBlockTypes, 0, 1, 2, QUAD_VERTICES_LEFT, NORMAL_LEFT);
        greedyMesh(blocks, outPositions, outNormalIDs, outBlockTypes, 1, 0, 2, QUAD_VERTICES_TOP, NORMAL_TOP);
        greedyMesh(blocks, outPositions, outNormalIDs, outBlockTypes, 1, 0, 2, QUAD_VERTICES_BOTTOM, NORMAL_BOTTOM);
        greedyMesh(blocks, outPositions, outNormalIDs, outBlockTypes, 2, 0, 1, QUAD_VERTICES_FRONT, NORMAL_FRONT);
        greedyMesh(blocks, outPositions, outNormalIDs, outBlockTypes, 2, 0, 1, QUAD_VERTICES_BACK, NORMAL_BACK);
    }

    private void greedyMesh(short[][][] blocks, List<Float> outPositions, List<Byte> outNormalIDs, List<Byte> outBlockTypes,
                            int axis, int u_axis, int v_axis,
                            float[] faceVerticesTemplate, float[] faceNormalTemplate)
    {
        int i_axis_size = Chunk.CHUNK_SIZE;
        int u_axis_size = Chunk.CHUNK_SIZE;
        int v_axis_size = Chunk.CHUNK_SIZE;
        short[][] mask = new short[u_axis_size][v_axis_size]; // Stores block type for visible faces

        byte currentNormalID = encodeNormal(faceNormalTemplate);

        for (int i = 0; i < i_axis_size; i++) {
            for (int u = 0; u < u_axis_size; u++) {
                for (int v = 0; v < v_axis_size; v++) {
                    int[] currBlockCoords = new int[3];
                    currBlockCoords[axis] = i;
                    currBlockCoords[u_axis] = u;
                    currBlockCoords[v_axis] = v;
                    int currX = currBlockCoords[0];
                    int currY = currBlockCoords[1];
                    int currZ = currBlockCoords[2];

                    int nextX = currX + (int) faceNormalTemplate[0];
                    int nextY = currY + (int) faceNormalTemplate[1];
                    int nextZ = currZ + (int) faceNormalTemplate[2];

                    short currentBlock = getBlockSafe(blocks, currX, currY, currZ);
                    short adjacentBlock = getBlockSafe(blocks, nextX, nextY, nextZ);

                    if (currentBlock != BLOCK_TYPE_AIR_ID && adjacentBlock == BLOCK_TYPE_AIR_ID) {
                        mask[u][v] = currentBlock; // Store the type of the block whose face is visible
                    } else {
                        mask[u][v] = BLOCK_TYPE_AIR_ID;
                    }
                }
            }

            for (int u = 0; u < u_axis_size; u++) {
                for (int v = 0; v < v_axis_size;) {
                    short blockTypeForFace = mask[u][v]; // This is the type of the block creating the face

                    if (blockTypeForFace != BLOCK_TYPE_AIR_ID) {
                        int width = 1;
                        while (v + width < v_axis_size && mask[u][v + width] == blockTypeForFace) {
                            width++;
                        }

                        int height = 1;
                        boolean canExpandHeight = true;
                        while (u + height < u_axis_size && canExpandHeight) {
                            for (int k = 0; k < width; k++) {
                                if (mask[u + height][v + k] != blockTypeForFace) {
                                    canExpandHeight = false;
                                    break;
                                }
                            }
                            if (canExpandHeight) {
                                height++;
                            }
                        }

                        addGreedyQuad(outPositions, outNormalIDs, outBlockTypes,
                                faceVerticesTemplate, currentNormalID, (byte) blockTypeForFace,
                                i, u, v, width, height, axis, u_axis, v_axis);

                        for (int eu = u; eu < u + height; eu++) {
                            for (int ev = v; ev < v + width; ev++) {
                                mask[eu][ev] = BLOCK_TYPE_AIR_ID;
                            }
                        }
                        v += width;
                    } else {
                        v++;
                    }
                }
            }
        }
    }

    private short getBlockSafe(short[][][] blocks, int x, int y, int z) {
        if (x < 0 || x >= Chunk.CHUNK_SIZE || y < 0 || y >= Chunk.CHUNK_SIZE || z < 0 || z >= Chunk.CHUNK_SIZE) {
            return BLOCK_TYPE_AIR_ID;
        }
        return blocks[x][y][z];
    }

    private void addGreedyQuad(List<Float> positions, List<Byte> normalIDs, List<Byte> blockTypes,
                               float[] faceVerticesTemplate, byte normalID, byte blockType,
                               int i_coord, int u_coord, int v_coord, int width, int height,
                               int axis, int u_axis, int v_axis)
    {
        // The normal and blockType are the same for all 6 vertices of this quad
        float normalXForFixedCoord = 0, normalYForFixedCoord = 0, normalZForFixedCoord = 0;
        // Decode normalID to get the float vector for fixed coordinate calculation
        // This is a bit clunky but mirrors the logic in your original faceNormalTemplate usage for positioning
        if (normalID == 0) { normalZForFixedCoord = 1.0f; } // FRONT
        else if (normalID == 1) { normalZForFixedCoord = -1.0f; } // BACK
        else if (normalID == 2) { normalXForFixedCoord = -1.0f; } // LEFT
        else if (normalID == 3) { normalXForFixedCoord = 1.0f; } // RIGHT
        else if (normalID == 4) { normalYForFixedCoord = 1.0f; } // TOP
        else if (normalID == 5) { normalYForFixedCoord = -1.0f; } // BOTTOM


        for (int j = 0; j < faceVerticesTemplate.length; j += 3) {
            float vx = faceVerticesTemplate[j];
            float vy = faceVerticesTemplate[j + 1];
            float vz = faceVerticesTemplate[j + 2];

            float finalX = 0, finalY = 0, finalZ = 0;

            // Determine the "fixed" coordinate based on the normal and i_coord
            // If normal points along +axis, fixed coord is i_coord + 1. Else, it's i_coord.
            float fixedCoordOffset = 0;
            if (axis == 0 && normalXForFixedCoord > 0) fixedCoordOffset = 1.0f; // Right face
            else if (axis == 1 && normalYForFixedCoord > 0) fixedCoordOffset = 1.0f; // Top face
            else if (axis == 2 && normalZForFixedCoord > 0) fixedCoordOffset = 1.0f; // Front face

            float fixedCoord = i_coord + fixedCoordOffset;


            if (axis == 0) { // Sweeping along X-axis (u_axis=Y, v_axis=Z)
                finalX = fixedCoord;
                finalY = u_coord + vy * height;
                finalZ = v_coord + vz * width;
            } else if (axis == 1) { // Sweeping along Y-axis (u_axis=X, v_axis=Z)
                finalX = u_coord + vx * height;
                finalY = fixedCoord;
                finalZ = v_coord + vz * width;
            } else if (axis == 2) { // Sweeping along Z-axis (u_axis=X, v_axis=Y)
                finalX = u_coord + vx * height;
                finalY = v_coord + vy * width;
                finalZ = fixedCoord;
            }

            positions.add(finalX);
            positions.add(finalY);
            positions.add(finalZ);

            normalIDs.add(normalID);
            blockTypes.add(blockType);
        }
    }


    /**
     * Uploads mesh data to GPU.
     * @param finalPositions Array of vertex positions.
     * @param finalNormalIDs Array of normal IDs.
     * @param finalBlockTypes Array of block type IDs.
     */
    public void uploadToGPU(float[] finalPositions, byte[] finalNormalIDs, byte[] finalBlockTypes)
    {
        FloatBuffer positionsBuffer = null;
        ByteBuffer normalIDsBuffer = null;
        ByteBuffer blockTypesBuffer = null;
        try {
            this.vertexCount = finalPositions.length / 3;
            if (this.vertexCount == 0) {
                return; // No data to upload
            }

            positionsBuffer = MemoryUtil.memAllocFloat(finalPositions.length);
            positionsBuffer.put(finalPositions).flip();

            normalIDsBuffer = MemoryUtil.memAlloc(finalNormalIDs.length);
            normalIDsBuffer.put(finalNormalIDs).flip();

            blockTypesBuffer = MemoryUtil.memAlloc(finalBlockTypes.length);
            blockTypesBuffer.put(finalBlockTypes).flip();

            GL30.glBindVertexArray(this.vaoID);

            // Positions VBO (Attribute 0)
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vboPositionsID);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, positionsBuffer, GL15.GL_STATIC_DRAW);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0);

            // Normal IDs VBO (Attribute 1) - as integers
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vboNormalsID);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, normalIDsBuffer, GL15.GL_STATIC_DRAW);
            GL30.glVertexAttribIPointer(1, 1, GL11.GL_UNSIGNED_BYTE, 0, 0); // Use IPointer for integer attributes

            // Block Types VBO (Attribute 2) - as integers
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vboBlockTypesID);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, blockTypesBuffer, GL15.GL_STATIC_DRAW);
            GL30.glVertexAttribIPointer(2, 1, GL11.GL_UNSIGNED_BYTE, 0, 0); // Use IPointer for integer attributes


            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL30.glBindVertexArray(0);

            Debug.checkGLError("ChunkMesh: uploadToGPU");

        } finally {
            if (positionsBuffer != null) MemoryUtil.memFree(positionsBuffer);
            if (normalIDsBuffer != null) MemoryUtil.memFree(normalIDsBuffer);
            if (blockTypesBuffer != null) MemoryUtil.memFree(blockTypesBuffer);
        }
    }

    public void bind() {
        GL30.glBindVertexArray(this.vaoID);
        GL20.glEnableVertexAttribArray(0); // Position
        GL20.glEnableVertexAttribArray(1); // Normal ID
        GL20.glEnableVertexAttribArray(2); // Block Type ID
    }

    public void render() {
        if (this.vertexCount > 0) {
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, this.vertexCount);
        }
    }

    public void unbind() {
        GL20.glDisableVertexAttribArray(0);
        GL20.glDisableVertexAttribArray(1);
        GL20.glDisableVertexAttribArray(2);
        GL30.glBindVertexArray(0);
    }

    public void cleanup() {
        if (vaoID != 0) {
            GL30.glBindVertexArray(0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

            GL20.glDisableVertexAttribArray(0);
            GL20.glDisableVertexAttribArray(1);
            GL20.glDisableVertexAttribArray(2);

            GL15.glDeleteBuffers(vboPositionsID);
            GL15.glDeleteBuffers(vboNormalsID);
            GL15.glDeleteBuffers(vboBlockTypesID);
            GL30.glDeleteVertexArrays(vaoID);

            vaoID = 0;
            vboPositionsID = 0;
            vboNormalsID = 0;
            vboBlockTypesID = 0;
            vertexCount = 0;
        }
    }
}