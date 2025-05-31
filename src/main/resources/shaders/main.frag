#version 460 core

// Inputs from the vertex shader
in vec3 v_worldPosition; // Fragment's position in world space
in vec3 v_normal;        // Fragment's normal in world space (interpolated)
flat in uint v_blockTypeID; // Block type ID (not interpolated)

// Output color for the fragment
out vec4 FragColor;

// Simple lighting parameters (can be uniforms)
const vec3 lightDirection = normalize(vec3(0.5, 1.0, 0.75)); // Example light direction
const vec3 lightColor = vec3(1.0, 1.0, 0.9);
const float ambientStrength = 0.4; // Ambient light intensity

// Predefined base colors for block types (example)
// These would ideally come from a texture atlas lookup
const vec3 blockColors[4] = vec3[](
    vec3(0.6, 0.6, 0.6), // ID 0: Default/Stone (e.g., World.BLOCK_TYPE_STONE_ID = 3, but IDs start at 0 here)
    vec3(0.5, 0.35, 0.2),// ID 1: Dirt (e.g., World.BLOCK_TYPE_SOLID_ID = 1)
    vec3(0.2, 0.7, 0.1), // ID 2: Grass (e.g., World.BLOCK_TYPE_GRASS_ID = 2)
    vec3(0.3, 0.3, 0.3)  // ID 3: Bedrock (e.g., a new ID like 4)
    // Add more as needed, up to the max blockTypeID you use
);


void main()
{
    // Normalize the incoming normal (it's interpolated, so might not be unit length)
    vec3 normal = normalize(v_normal);

    // Determine base color based on block type
    vec3 baseColor = vec3(0.5, 0.5, 0.5); // Default color if ID is out of bounds
    if (v_blockTypeID < blockColors.length()) { // blockColors.length() is GLSL 4.3+
        baseColor = blockColors[v_blockTypeID];
    }
    // For older GLSL versions, you might need to pass array size as a uniform or use a fixed size.
    // Or, if your IDs map directly and you're sure they're in range:
    // baseColor = blockColors[v_blockTypeID];


    // Ambient component
    vec3 ambient = ambientStrength * lightColor;

    // Diffuse component
    float diff = max(dot(normal, lightDirection), 0.0);
    vec3 diffuse = diff * lightColor;

    // Final color
    vec3 resultColor = (ambient + diffuse) * baseColor;
    FragColor = vec4(resultColor, 1.0);

    // For debugging block types, you could output baseColor directly:
    // FragColor = vec4(baseColor, 1.0);

    // For debugging normals:
    //FragColor = vec4(normal * 0.5 + 0.5, 1.0);
}