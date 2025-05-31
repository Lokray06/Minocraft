#version 460 core

// Base Quad Vertex Attribute (from shared VBO)
layout (location = 0) in vec2 a_baseVertexPos; // 2D coords of the base 1x1 quad (0,0 to 1,1)

// Per-Instance Attributes (from instance VBO)
layout (location = 1) in vec3 i_origin;        // Origin of the greedy quad in chunk space
layout (location = 2) in vec2 i_dimensions;    // i_dimensions.x = dimensionH (height), i_dimensions.y = dimensionW (width)
layout (location = 3) in float i_normalID_float;  // Normal ID for the quad (0-5)
layout (location = 4) in float i_blockTypeID_float;// Block Type ID for the quad

// Uniforms
uniform mat4 modelMatrix;      // Chunk's world transform
uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;

// Outputs to Fragment Shader
out vec3 v_worldPosition;
out vec3 v_normal;
flat out uint v_blockTypeID;

// Predefined normals (same as before)
const vec3 normals[6] = vec3[](
    vec3(0.0, 0.0, 1.0),  // ID 0: Front (+Z)
    vec3(0.0, 0.0, -1.0), // ID 1: Back (-Z)
    vec3(-1.0, 0.0, 0.0), // ID 2: Left (-X)
    vec3(1.0, 0.0, 0.0),  // ID 3: Right (+X)
    vec3(0.0, 1.0, 0.0),  // ID 4: Top (+Y)
    vec3(0.0, -1.0, 0.0)  // ID 5: Bottom (-Y)
);

// These define the world-space axes for U and V dimensions of the quad on each face type
// U_direction corresponds to the greedy meshing 'u_axis' (used with i_dimensions.x / height)
// V_direction corresponds to the greedy meshing 'v_axis' (used with i_dimensions.y / width)

const vec3 face_U_direction[6] = vec3[]( // Direction for dimensionH (height, maps to a_baseVertexPos.y)
    vec3(1.0, 0.0, 0.0), // Front (+Z): Height along X
    vec3(1.0, 0.0, 0.0), // Back (-Z): Height along X
    vec3(0.0, 1.0, 0.0), // Left (-X): Height along Y
    vec3(0.0, 1.0, 0.0), // Right (+X): Height along Y
    vec3(1.0, 0.0, 0.0), // Top (+Y): Height along X
    vec3(1.0, 0.0, 0.0)  // Bottom (-Y): Height along X
);

const vec3 face_V_direction[6] = vec3[]( // Direction for dimensionW (width, maps to a_baseVertexPos.x)
    vec3(0.0, 1.0, 0.0), // Front (+Z): Width along Y
    vec3(0.0, 1.0, 0.0), // Back (-Z): Width along Y
    vec3(0.0, 0.0, 1.0), // Left (-X): Width along Z
    vec3(0.0, 0.0, 1.0), // Right (+X): Width along Z
    vec3(0.0, 0.0, 1.0), // Top (+Y): Width along Z
    vec3(0.0, 0.0, 1.0)  // Bottom (-Y): Width along Z
);


void main()
{
    uint nID = uint(i_normalID_float); // Convert from float
    uint bID = uint(i_blockTypeID_float); // Convert from float

    nID = clamp(nID, 0u, 5u); // Safety clamp

    vec3 N_modelSpace = normals[nID];
    vec3 U_dir_modelSpace = face_U_direction[nID]; // Direction of quad's height dimension (corresponds to i_dimensions.x)
    vec3 V_dir_modelSpace = face_V_direction[nID]; // Direction of quad's width dimension (corresponds to i_dimensions.y)

    // a_baseVertexPos.x is for the horizontal extent of the base quad (0-1)
    // a_baseVertexPos.y is for the vertical extent of the base quad (0-1)
    // i_dimensions.x is quadHeight (H)
    // i_dimensions.y is quadWidth (W)
    vec3 quadPointOffset = (V_dir_modelSpace * a_baseVertexPos.x * i_dimensions.y) + // Scale horizontal by quadWidth (i_dimensions.y)
                           (U_dir_modelSpace * a_baseVertexPos.y * i_dimensions.x);  // Scale vertical by quadHeight (i_dimensions.x)

    vec3 finalPosition_inChunk = i_origin + quadPointOffset;

    // Transform to world and clip space
    vec4 worldPosition4 = modelMatrix * vec4(finalPosition_inChunk, 1.0);
    v_worldPosition = worldPosition4.xyz;
    gl_Position = projectionMatrix * viewMatrix * worldPosition4;

    // Transform normal to world space
    v_normal = normalize(mat3(transpose(inverse(modelMatrix))) * N_modelSpace);

    v_blockTypeID = bID;
}