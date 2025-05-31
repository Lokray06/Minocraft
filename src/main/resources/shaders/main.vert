#version 330 core

// Input vertex position (from your VAO's attribute 0)
layout (location = 0) in vec3 position;
// NEW: Input normal vector (from your VAO's attribute 1)
layout (location = 1) in vec3 normal;

// Uniforms for transforming the vertex
uniform mat4 modelMatrix;
uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;

// NEW: Output variable to pass the normal (in world space) to the fragment shader
out vec3 fragNormal;

void main()
{
    // Multiply the position by the model, view, and projection matrices
    gl_Position = projectionMatrix * viewMatrix * modelMatrix * vec4(position, 1.0);

    // NEW: Transform the normal vector to world space
    // For normals, you typically use the inverse transpose of the model-view matrix.
    // However, for simple directional lighting without non-uniform scaling,
    // just using the modelMatrix (or viewMatrix * modelMatrix) is often "good enough" for an illusion.
    // We'll transform it to world space for simplicity.
    fragNormal = mat3(modelMatrix) * normal;
    fragNormal = normalize(fragNormal); // Normalize to ensure it's a unit vector
}