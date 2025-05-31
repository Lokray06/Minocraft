package com.juanpa.engine.world.chunk;

import com.juanpa.engine.Debug;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class ChunkMesh
{
    private int vaoID;
    private int vboID; // For positions
    private int normalVboID; // VBO for normals
    private int vertexCount; // Current vertex count on the GPU

    private static final short BLOCK_TYPE_AIR_ID = 0;

    // We'll define a set of static quads for each face, relative to a (0,0,0) origin block
    // and then apply offset and rotation/normal depending on the face.
    // Each quad has 6 vertices (2 triangles)
    // Front face (Z+): Vertices for the face at Z=1 for a unit cube
    private static final float[] QUAD_VERTICES_FRONT = { 0.0f, 0.0f, 1.0f, // BL
            1.0f, 0.0f, 1.0f, // BR
            1.0f, 1.0f, 1.0f, // TR
            1.0f, 1.0f, 1.0f, // TR (Duplicate for second triangle)
            0.0f, 1.0f, 1.0f, // TL
            0.0f, 0.0f, 1.0f // BL (Duplicate for second triangle)
    };
    private static final float[] NORMAL_FRONT = { 0.0f, 0.0f, 1.0f };

    // Back face (Z-): Vertices for the face at Z=0
    private static final float[] QUAD_VERTICES_BACK = { 0.0f, 0.0f, 0.0f, // BL
            0.0f, 1.0f, 0.0f, // TL
            1.0f, 1.0f, 0.0f, // TR
            1.0f, 1.0f, 0.0f, // TR (Duplicate for second triangle)
            1.0f, 0.0f, 0.0f, // BR
            0.0f, 0.0f, 0.0f // BL (Duplicate for second triangle)
    };
    private static final float[] NORMAL_BACK = { 0.0f, 0.0f, -1.0f };

    // Left face (X-): Vertices for the face at X=0
    private static final float[] QUAD_VERTICES_LEFT = { 0.0f, 0.0f, 0.0f, // BL
            0.0f, 1.0f, 0.0f, // TL
            0.0f, 1.0f, 1.0f, // TR
            0.0f, 1.0f, 1.0f, // TR (Duplicate for second triangle)
            0.0f, 0.0f, 1.0f, // BR
            0.0f, 0.0f, 0.0f // BL (Duplicate for second triangle)
    };
    private static final float[] NORMAL_LEFT = { -1.0f, 0.0f, 0.0f };

    // Right face (X+): Vertices for the face at X=1
    private static final float[] QUAD_VERTICES_RIGHT = { 1.0f, 0.0f, 0.0f, // BL
            1.0f, 0.0f, 1.0f, // BR
            1.0f, 1.0f, 1.0f, // TR
            1.0f, 1.0f, 1.0f, // TR (Duplicate for second triangle)
            1.0f, 1.0f, 0.0f, // TL
            1.0f, 0.0f, 0.0f // BL (Duplicate for second triangle)
    };
    private static final float[] NORMAL_RIGHT = { 1.0f, 0.0f, 0.0f };

    // Top face (Y+): Vertices for the face at Y=1
    private static final float[] QUAD_VERTICES_TOP = { 0.0f, 1.0f, 0.0f, // BL
            1.0f, 1.0f, 0.0f, // BR
            1.0f, 1.0f, 1.0f, // TR
            1.0f, 1.0f, 1.0f, // TR (Duplicate for second triangle)
            0.0f, 1.0f, 1.0f, // TL
            0.0f, 1.0f, 0.0f // BL (Duplicate for second triangle)
    };
    private static final float[] NORMAL_TOP = { 0.0f, 1.0f, 0.0f };

    // Bottom face (Y-): Vertices for the face at Y=0
    private static final float[] QUAD_VERTICES_BOTTOM = { 0.0f, 0.0f, 0.0f, // BL
            0.0f, 0.0f, 1.0f, // TL
            1.0f, 0.0f, 1.0f, // TR
            1.0f, 0.0f, 1.0f, // TR (Duplicate for second triangle)
            1.0f, 0.0f, 0.0f, // BR
            0.0f, 0.0f, 0.0f // BL (Duplicate for second triangle)
    };
    private static final float[] NORMAL_BOTTOM = { 0.0f, -1.0f, 0.0f };

    // Constructor: Only initializes GL objects, no data yet.
    public ChunkMesh()
    {
        this.vaoID = GL30.glGenVertexArrays();
        Debug.checkGLError("ChunkMesh: glGenVertexArrays (Constructor)");
        this.vboID = GL15.glGenBuffers(); // For positions
        Debug.checkGLError("ChunkMesh: glGenBuffers (VBO) (Constructor)");
        this.normalVboID = GL15.glGenBuffers(); // For normals
        Debug.checkGLError("ChunkMesh: glGenBuffers (Normal VBO) (Constructor)");
    }

    /**
     * Generates the mesh data (vertices and normals) for a chunk using greedy meshing. This is a CPU-intensive operation and should ideally run on a background thread.
     *
     * @param rawChunkData The Chunk object containing the block data.
     * @param positions List to populate with vertex positions.
     * @param normals List to populate with vertex normals.
     */
    public void generateMeshData(Chunk rawChunkData, List<Float> positions, List<Float> normals)
    {
        positions.clear();
        normals.clear();

        short[][][] blocks = rawChunkData.blocks; // Direct access to block data

        // Iterate over each axis (X, Y, Z) and direction (+/-)
        // This process needs to be done for each of the 3 axes (X, Y, Z).
        // For each axis, we sweep twice (once for positive, once for negative direction).

        // Sweep along X-axis
        greedyMesh(blocks, positions, normals, 0, 1, 2, // axis (0=X, 1=Y, 2=Z), u-dim (Y), v-dim (Z)
                QUAD_VERTICES_RIGHT, NORMAL_RIGHT); // +X faces
        greedyMesh(blocks, positions, normals, 0, 1, 2, // axis, u-dim, v-dim
                QUAD_VERTICES_LEFT, NORMAL_LEFT); // -X faces

        // Sweep along Y-axis
        greedyMesh(blocks, positions, normals, 1, 0, 2, // axis (1=Y), u-dim (X), v-dim (Z)
                QUAD_VERTICES_TOP, NORMAL_TOP); // +Y faces
        greedyMesh(blocks, positions, normals, 1, 0, 2, // axis, u-dim, v-dim
                QUAD_VERTICES_BOTTOM, NORMAL_BOTTOM); // -Y faces

        // Sweep along Z-axis
        greedyMesh(blocks, positions, normals, 2, 0, 1, // axis (2=Z), u-dim (X), v-dim (Y)
                QUAD_VERTICES_FRONT, NORMAL_FRONT); // +Z faces
        greedyMesh(blocks, positions, normals, 2, 0, 1, // axis, u-dim, v-dim
                QUAD_VERTICES_BACK, NORMAL_BACK); // -Z faces
    }

    private void greedyMesh(short[][][] blocks, List<Float> positions, List<Float> normals, int axis, int u_axis, int v_axis, float[] faceVerticesTemplate, float[] faceNormalTemplate)
    {

        int i_axis_size = Chunk.CHUNK_SIZE;
        int u_axis_size = Chunk.CHUNK_SIZE;
        int v_axis_size = Chunk.CHUNK_SIZE;

        // Mask to store block types for the current slice
        short[][] mask = new short[u_axis_size][v_axis_size];

        // --- Log 1: Start of greedyMesh pass ---
        // Identify the normal direction for clarity
        String normalDir = "";
        if (faceNormalTemplate[0] == 1.0f)
            normalDir = "+X";
        else if (faceNormalTemplate[0] == -1.0f)
            normalDir = "-X";
        else if (faceNormalTemplate[1] == 1.0f)
            normalDir = "+Y";
        else if (faceNormalTemplate[1] == -1.0f)
            normalDir = "-Y";
        else if (faceNormalTemplate[2] == 1.0f)
            normalDir = "+Z";
        else if (faceNormalTemplate[2] == -1.0f)
            normalDir = "-Z";
        // Debug.log("--- Starting greedyMesh pass for Normal: " + normalDir + " (Axis: " + axis + ", U-axis: " + u_axis + ", V-axis: " + v_axis + ") ---");

        // i represents the current slice along the 'axis'
        for (int i = 0; i < i_axis_size; i++)
        {
            // --- Log 2: Current slice ---
            // Debug.log("Processing slice 'i' (along axis " + axis + "): " + i);

            // Build mask for the current slice
            for (int u = 0; u < u_axis_size; u++)
            {
                for (int v = 0; v < v_axis_size; v++)
                {
                    // Map i, u, v to their respective world-space block coordinates (currX, currY, currZ)
                    int[] currBlockCoords = new int[3]; // [X, Y, Z]
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

                    if (currentBlock != BLOCK_TYPE_AIR_ID && adjacentBlock == BLOCK_TYPE_AIR_ID)
                    {
                        mask[u][v] = currentBlock;
                    } else
                    {
                        mask[u][v] = BLOCK_TYPE_AIR_ID;
                    }
                }
            }

            // --- Log 4: Print the generated mask for the current slice ---
            StringBuilder maskDebug = new StringBuilder("Mask for slice 'i'=" + i + " (Normal: " + normalDir + "):\n");
            for (int u = 0; u < u_axis_size; u++)
            {
                for (int v = 0; v < v_axis_size; v++)
                {
                    maskDebug.append(mask[u][v] == BLOCK_TYPE_AIR_ID ? "_" : "X"); // X for solid, _ for air
                }
                maskDebug.append("\n");
            }
            // Debug.log(maskDebug.toString());

            // Iterate through the mask to find quads
            for (int u = 0; u < u_axis_size; u++)
            {
                for (int v = 0; v < v_axis_size;)
                { // Notice: v is not incremented here
                    short blockType = mask[u][v];

                    if (blockType != BLOCK_TYPE_AIR_ID)
                    { // Found a potential face
                        // Greedily expand in 'v' direction (width)
                        int width = 1;
                        while (v + width < v_axis_size && mask[u][v + width] == blockType)
                        {
                            width++;
                        }

                        // Greedily expand in 'u' direction (height)
                        int height = 1;
                        boolean canExpandHeight = true;
                        while (u + height < u_axis_size && canExpandHeight)
                        {
                            for (int k = 0; k < width; k++)
                            {
                                if (mask[u + height][v + k] != blockType)
                                {
                                    canExpandHeight = false;
                                    break;
                                }
                            }
                            if (canExpandHeight)
                            {
                                height++;
                            }
                        }

                        // --- Log 5: Discovered Quad ---
                        // Debug.log("  Discovered Quad at mask[" + u + "][" + v + "] (blockType: " + blockType + ")");
                        // Debug.log("    Dimensions: Width = " + width + ", Height = " + height);
                        // Debug.log("    Calling addGreedyQuad with i_coord=" + i + ", u_coord=" + u + ", v_coord=" + v);

                        // Add its vertices and normals
                        addGreedyQuad(positions, normals, faceVerticesTemplate, faceNormalTemplate, i, u, v, // i = block position along axis, u = start u-coord, v = start v-coord
                                width, height, // dimensions of the merged quad
                                axis, u_axis, v_axis); // axes info

                        // Clear the mask for the found quad to prevent reprocessing
                        for (int eu = u; eu < u + height; eu++)
                        {
                            for (int ev = v; ev < v + width; ev++)
                            {
                                mask[eu][ev] = BLOCK_TYPE_AIR_ID;
                            }
                        }

                        // Advance 'v' by the width of the merged quad
                        v += width;

                    } else
                    {
                        // If no block found, just advance v by 1
                        v++;
                    }
                }
            }
        }
    }

    /**
     * Safely gets a block from the blocks array, returning AIR if out of bounds.
     */
    private short getBlockSafe(short[][][] blocks, int x, int y, int z)
    {
        if (x < 0 || x >= Chunk.CHUNK_SIZE || y < 0 || y >= Chunk.CHUNK_SIZE || z < 0 || z >= Chunk.CHUNK_SIZE)
        {
            return BLOCK_TYPE_AIR_ID; // Treat out-of-bounds as air for culling
        }
        return blocks[x][y][z];
    }

    /**
     * Adds the vertices and normals for a greedily merged quad. The faceVerticesTemplate defines the shape of a unit quad (0-1 range). We scale it by width/height and translate it to its correct world position.
     */
    private void addGreedyQuad(List<Float> positions, List<Float> normals, float[] faceVerticesTemplate, float[] faceNormalTemplate, int i_coord, int u_coord, int v_coord, int width, int height, int axis, int u_axis, int v_axis)
    {
        // --- Log 7: Start of addGreedyQuad for a quad ---
        // Debug.log("  addGreedyQuad called: i=" + i_coord + ", u=" + u_coord + ", v=" + v_coord + ", width=" + width + ", height=" + height + ", Normal=(" + faceNormalTemplate[0] + ", " + faceNormalTemplate[1] + ", " + faceNormalTemplate[2] + ")");

        for (int j = 0; j < faceVerticesTemplate.length; j += 3)
        {
            float vx = faceVerticesTemplate[j];
            float vy = faceVerticesTemplate[j + 1];
            float vz = faceVerticesTemplate[j + 2];

            // Scale and translate the quad vertex based on axis and dimensions
            float finalX = 0, finalY = 0, finalZ = 0;

            // Determine the "fixed" coordinate based on the normal and i_coord
            // For example, if Normal=(0,0,1) (+Z face), the Z coordinate is i_coord + 1.0f
            // If Normal=(0,0,-1) (-Z face), the Z coordinate is i_coord + 0.0f
            float fixedCoord = i_coord + (faceNormalTemplate[axis] == 1.0f ? 1.0f : 0.0f);

            // The U and V coordinates are scaled by width/height relative to their origin (u_coord, v_coord)
            if (axis == 0)
            { // Sweeping along X-axis (u_axis=Y, v_axis=Z)
                finalX = fixedCoord;
                finalY = u_coord + vy * height; // vy (template U) is scaled by height (quad's U-extent)
                finalZ = v_coord + vz * width; // vz (template V) is scaled by width (quad's V-extent)
            } else if (axis == 1)
            { // Sweeping along Y-axis (u_axis=X, v_axis=Z)
                finalX = u_coord + vx * height; // vx (template U) is scaled by height (quad's U-extent)
                finalY = fixedCoord;
                finalZ = v_coord + vz * width; // vz (template V) is scaled by width (quad's V-extent)
            } else if (axis == 2)
            { // Sweeping along Z-axis (u_axis=X, v_axis=Y)
                finalX = u_coord + vx * height; // vx (template U) is scaled by height (quad's U-extent)
                finalY = v_coord + vy * width; // vy (template V) is scaled by width (quad's V-extent)
                finalZ = fixedCoord;
            }

            positions.add(finalX);
            positions.add(finalY);
            positions.add(finalZ);

            // Normals are constant for the entire quad
            normals.add(faceNormalTemplate[0]);
            normals.add(faceNormalTemplate[1]);
            normals.add(faceNormalTemplate[2]);

            // --- Log 8 (Optional, highly verbose): Individual Vertex Coordinates ---
            // Debug.log("    Vertex (" + (j / 3) + ") original (" + vx + ", " + vy + ", " + vz + ") -> final (" + finalX + ", " + finalY + ", " + finalZ + ")");
        }
    }

    /**
     * Uploads the generated mesh data (vertices and normals) to the GPU. This method contains OpenGL calls and MUST be called on the main rendering thread.
     *
     * @param vertices The float array containing vertex position data.
     * @param normals The float array containing vertex normal data.
     */
    public void uploadToGPU(float[] vertices, float[] normals)
    {
        FloatBuffer verticesBuffer = null;
        FloatBuffer normalsBuffer = null;
        try
        {
            this.vertexCount = vertices.length / 3;

            verticesBuffer = MemoryUtil.memAllocFloat(vertices.length);
            verticesBuffer.put(vertices).flip();

            normalsBuffer = MemoryUtil.memAllocFloat(normals.length);
            normalsBuffer.put(normals).flip();

            GL30.glBindVertexArray(this.vaoID);

            // Bind and upload vertex position data to VBO
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vboID);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verticesBuffer, GL15.GL_STATIC_DRAW);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0);

            // Bind and upload normal data to a separate VBO
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.normalVboID);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, normalsBuffer, GL15.GL_STATIC_DRAW);
            GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, 0, 0);

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL30.glBindVertexArray(0);

            Debug.checkGLError("ChunkMesh: uploadToGPU");

        } finally
        {
            if (verticesBuffer != null)
                MemoryUtil.memFree(verticesBuffer);
            if (normalsBuffer != null)
                MemoryUtil.memFree(normalsBuffer);
        }
    }

    public void bind()
    {
        GL30.glBindVertexArray(this.vaoID);
        GL20.glEnableVertexAttribArray(0);
        GL20.glEnableVertexAttribArray(1);
    }

    public void render()
    {
        if (this.vertexCount > 0)
        {
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, this.vertexCount);
        }
    }

    public void unbind()
    {
        GL20.glDisableVertexAttribArray(0);
        GL20.glDisableVertexAttribArray(1);
        GL30.glBindVertexArray(0);
    }

    public void cleanup()
    {
        if (vaoID != 0)
        {
            GL30.glBindVertexArray(0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

            GL20.glDisableVertexAttribArray(0);
            GL20.glDisableVertexAttribArray(1);

            GL15.glDeleteBuffers(vboID);
            GL15.glDeleteBuffers(normalVboID);
            GL30.glDeleteVertexArrays(vaoID);

            vaoID = 0;
            vboID = 0;
            normalVboID = 0;
            vertexCount = 0;
        }
    }
}