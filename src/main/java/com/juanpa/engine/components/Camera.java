package com.juanpa.engine.components;

import com.juanpa.engine.Debug;
import com.juanpa.engine.Engine;
import com.juanpa.engine.Window;
import com.juanpa.engine.input.Input; // Still need Input for mouse delta directly
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector2d;
import org.lwjgl.glfw.GLFW; // Import GLFW for mouse button constants if needed elsewhere

public class Camera extends Component
{
	public float fov = 90;
	public float nearPlane = 0.001f;
	public float farPlane = 1000f;
	public Vector3f offset = new Vector3f(0); // Offset from the GameObject's transform
	public boolean isMainCamera = true; // Flag for the active camera

	private Matrix4f viewMatrix = new Matrix4f();
	private Matrix4f projectionMatrix = new Matrix4f();

	private float mouseSensitivity = 0.1f;
	private float maxPitch = 89.0f; // Limit pitch to prevent flipping

	// NEW: Store raw Euler angles for pitch and yaw
	private float currentPitch = 0.0f; // In degrees
	private float currentYaw = 0.0f;   // In degrees

	// Constructor
	public Camera()
	{
	}

	public Camera(float fov, float nearPlane, float farPlane, Vector3f offset, boolean isMainCamera)
	{
		this.fov = fov;
		this.nearPlane = nearPlane;
		this.farPlane = farPlane;
		this.offset = offset;
		this.isMainCamera = isMainCamera;

		this.viewMatrix = new Matrix4f();
		this.projectionMatrix = new Matrix4f();
	}

	@Override
	public void onStart()
	{
		// Initialize pitch and yaw from the GameObject's initial rotation if needed
		// For now, assume it starts looking straight (0,0,0 Euler)
		// If your player's initial transform has a yaw, you'd want to get that:
		// Vector3f initialEuler = gameObject.getTransform().rotation.getEulerAnglesXYZ(new Vector3f());
		// currentYaw = (float) Math.toDegrees(initialEuler.y);
		// currentPitch = (float) Math.toDegrees(initialEuler.x); // Pitch is X-axis rotation
	}

	@Override
	public void onUpdate()
	{
		if(!isMainCamera)
		{
			return; // Only update the main camera
		}

		Transform transform = gameObject.getTransform();
		if(transform == null)
		{
			Debug.logError("Camera component's GameObject '" + gameObject.getName() + "' has no Transform!");
			return;
		}

		// --- Apply Mouse Look ---
		Vector2d mouseDelta = Input.getMouseDelta();
		if(mouseDelta.lengthSquared() > 0)
		{
			// Accumulate yaw and pitch directly
			currentYaw += (float) -mouseDelta.x * mouseSensitivity;   // Yaw around World Y
			currentPitch += (float) -mouseDelta.y * mouseSensitivity; // Pitch around Local X

			// Clamp pitch to prevent camera flipping
			if(currentPitch > maxPitch)
			{
				currentPitch = maxPitch;
			}
			else if(currentPitch < -maxPitch)
			{
				currentPitch = -maxPitch;
			}

			// Optional: Wrap yaw around 360 degrees if you ever need precise Euler angles for UI etc.
			// currentYaw = currentYaw % 360.0f;
			// if (currentYaw < 0) currentYaw += 360.0f;

			// Reconstruct the quaternion from yaw (around world Y) and pitch (around local X)
			// Order matters: usually yaw first, then pitch relative to the yawed direction.
			// For JOML, this means creating a yaw quaternion and multiplying a pitch quaternion to it.
			// Pitch around local X-axis (rotated by currentYaw)
			// Yaw around world Y-axis (vertical)
			transform.rotation.identity() // Reset rotation to avoid cumulative issues
					.rotateY((float) Math.toRadians(currentYaw))    // Apply yaw (world Y)
					.rotateX((float) Math.toRadians(currentPitch)); // Apply pitch (local X, relative to yawed)
		}

		// Calculate the actual camera position, applying the offset from its GameObject's transform
		Vector3f cameraEyePosition = new Vector3f(transform.position).add(offset);

		// Calculate the view matrix (inverse of camera's world transform)
		viewMatrix.identity();
		// The view matrix is the inverse of the camera's world transform.
		// It first translates by the negative camera position (since we move the world),
		// then rotates by the *conjugate* (inverse) of the camera's rotation.
		// The order of operations for view matrix is inverse translation * inverse rotation.
		// JOML's `lookAt` or `translationRotate` often handles this.
		// Let's manually apply inverse:
		viewMatrix.rotation(transform.rotation.conjugate(new Quaternionf()))
				.translate(cameraEyePosition.negate(new Vector3f()));

		// Update projection matrix (aspect ratio will be set by Game class)
		// This requires access to the window dimensions.
		// Assuming your Game class or Engine passes the aspect ratio correctly.
		float aspectRatio = (float) Engine.width / (float) Engine.height;
		updateProjectionMatrix(aspectRatio);
	}

	private void updateProjectionMatrix(float aspectRatio)
	{
		projectionMatrix.identity().perspective(
				(float) Math.toRadians(this.fov),
				aspectRatio,
				this.nearPlane,
				this.farPlane
		);
	}

	public Matrix4f getViewMatrix()
	{
		return this.viewMatrix;
	}

	public Matrix4f getProjectionMatrix()
	{
		return this.projectionMatrix;
	}

	public void setAspectRatio(float aspectRatio)
	{
		updateProjectionMatrix(aspectRatio);
	}
}