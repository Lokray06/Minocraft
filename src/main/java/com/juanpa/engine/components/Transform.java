package com.juanpa.engine.components;

import org.joml.Quaternionf; // Import Quaternionf
import org.joml.Vector3f;

public class Transform extends Component
{
	public Vector3f position;
	public Quaternionf rotation; // Changed from Vector3f to Quaternionf
	public Vector3f scale;


	public Transform(Vector3f pos)
	{
		// Default rotation is identity (no rotation)
		this(pos, new Quaternionf(), new Vector3f(1.0f, 1.0f, 1.0f)); // Default scale to 1.0f for visibility
	}

	// Use a Quaternionf for rotation in this constructor
	public Transform(Vector3f pos, Quaternionf rot, Vector3f sca)
	{
		this.position = pos;
		this.rotation = rot;
		this.scale = sca;
	}

	// You might want a helper constructor for initial Euler angles (e.g., for player setup)
	// This converts Euler angles (degrees) to a quaternion
	public Transform(Vector3f pos, Vector3f eulerRotDegrees, Vector3f sca) {
		this.position = pos;
		this.rotation = new Quaternionf().rotateXYZ(
				(float) Math.toRadians(eulerRotDegrees.x), // Pitch
				(float) Math.toRadians(eulerRotDegrees.y), // Yaw
				(float) Math.toRadians(eulerRotDegrees.z)  // Roll
		);
		this.scale = sca;
	}

	public Transform()
	{
		this.position = new Vector3f(0);
		this.rotation = new Quaternionf();
		this.scale = new Vector3f(0);
	}

	// Add a helper to apply a rotation (e.g., from mouse input)
	public void rotate(float angleX, float angleY, float angleZ) {
		rotation.rotateXYZ(
				(float) Math.toRadians(angleX),
				(float) Math.toRadians(angleY),
				(float) Math.toRadians(angleZ)
		);
	}
}