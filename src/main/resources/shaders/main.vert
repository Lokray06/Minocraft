#version 460 core

// Input vertex attributes from VBOs
layout (location = 0) in vec3 a_position;    // Vertex position
layout (location = 1) in uint a_normalID;    // Compressed normal ID (0-5)
layout (location = 2) in uint a_blockTypeID; // Block Type ID

// Uniforms - matrices for transforming geometry
uniform mat4 modelMatrix;      // Transforms vertex from model space to world space
uniform mat4 viewMatrix;       // Transforms vertex from world space to view space
uniform mat4 projectionMatrix; // Transforms vertex from view space to clip space

// Outputs to the fragment shader
out vec3 v_worldPosition; // Vertex position in world space
out vec3 v_normal;        // Reconstructed and transformed normal vector in world space
flat out uint v_blockTypeID; // Block type ID (flat to prevent interpolation)

// Predefined normals corresponding to IDs (ensure this matches Java encoding)
// ID 0: +Z (Front)
// ID 1: -Z (Back)
// ID 2: -X (Left)
// ID 3: +X (Right)
// ID 4: +Y (Top)
// ID 5: -Y (Bottom)
const vec3 normals[6] = vec3[](
    vec3(0.0, 0.0, 1.0),  // ID 0: Front
    vec3(0.0, 0.0, -1.0), // ID 1: Back
    vec3(-1.0, 0.0, 0.0), // ID 2: Left
    vec3(1.0, 0.0, 0.0),  // ID 3: Right
    vec3(0.0, 1.0, 0.0),  // ID 4: Top
    vec3(0.0, -1.0, 0.0)  // ID 5: Bottom
);

void main()
{
    // Transform vertex position to world space
    vec4 worldPosition4 = modelMatrix * vec4(a_position, 1.0);
    v_worldPosition = worldPosition4.xyz;

    // Calculate the final clip space position
    gl_Position = projectionMatrix * viewMatrix * worldPosition4;

    // Reconstruct the normal vector from its ID
    // Clamp a_normalID to be safe, though it should always be in [0, 5]
    uint normalIndex = clamp(a_normalID, 0u, 5u);
    vec3 modelNormal = normals[normalIndex];

    // Transform normal to world space
    // Use the inverse transpose of the model matrix for normals if non-uniform scaling is possible.
    // If modelMatrix only involves rotation and uniform scaling, mat3(modelMatrix) is fine.
    v_normal = normalize(mat3(transpose(inverse(modelMatrix))) * modelNormal);
    // For simpler cases (only uniform scale/rotation):
    // v_normal = normalize(mat3(modelMatrix) * modelNormal);

    // Pass the block type ID to the fragment shader
    v_blockTypeID = a_blockTypeID;
}