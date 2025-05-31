#version 330 core

// The final output color for the fragment
out vec4 fragColor;

// NEW: Input normal vector (interpolated from vertex shader)
in vec3 fragNormal;

// NEW: Define a simple light direction (e.g., pointing down-right-forward)
// This is a directional light, so it's the same for all fragments.
// Must be normalized!
const vec3 lightDirection = normalize(vec3(0.5, -1.0, 0.2)); // Example: top-front-right light

void main()
{
    // Calculate the dot product between the fragment's normal and the light direction.
    // The dot product gives us a value between -1 and 1, representing the cosine of the angle.
    // -1 means surface points directly away from light, 1 means directly towards.
    float diffuseFactor = dot(-fragNormal, lightDirection);

    // Clamp the diffuse factor to be between 0 and 1 (no negative light)
    diffuseFactor = clamp(diffuseFactor, 0.1, 1.0); // Minimum brightness to prevent fully black faces

    // Choose a base color for your blocks (e.g., light gray)
    vec3 baseColor = vec3(0.8, 0.8, 0.8);

    // Apply the diffuse factor to the base color
    fragColor = vec4(baseColor * diffuseFactor, 1.0);
    
    // Debug the normals
    //fragColor = vec4(fragNormal, 1);
}