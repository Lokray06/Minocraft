// Modify: com/juanpa/engine/components/Camera.java
package com.juanpa.engine.components;

import com.juanpa.engine.Debug;
import com.juanpa.engine.Engine;
import com.juanpa.engine.Window;
import com.juanpa.engine.input.Input;
import com.juanpa.engine.math.AABB;
import com.juanpa.engine.math.Frustum;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector2d;
import org.lwjgl.glfw.GLFW;

public class Camera extends Component
{
	public float fov = 90;
	public float nearPlane = 0.001f;
	public float farPlane = 1000f;
	public Vector3f offset = new Vector3f(0);
	public boolean isMainCamera = true;

	private Matrix4f viewMatrix = new Matrix4f();
	private Matrix4f projectionMatrix = new Matrix4f();
	private Matrix4f viewProjectionMatrix = new Matrix4f(); // Combined matrix

	private float mouseSensitivity = 0.1f;
	private float maxPitch = 89.0f;

	private float currentPitch = 0.0f;
	private float currentYaw = 0.0f;

	// --- Frustum Culling Integration ---
	private Frustum frustum; // Our custom Frustum object

	// Constructor
	public Camera()
	{
		this.frustum = new Frustum(); // Initialize the frustum
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
		this.viewProjectionMatrix = new Matrix4f();
		this.frustum = new Frustum(); // Initialize the frustum
	}

	@Override
	public void onStart()
	{
		// ... (existing onStart code) ...
	}

	@Override
	public void update()
	{
		if(!isMainCamera)
		{
			return;
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
			currentYaw += (float) -mouseDelta.x * mouseSensitivity;
			currentPitch += (float) -mouseDelta.y * mouseSensitivity;

			if(currentPitch > maxPitch)
			{
				currentPitch = maxPitch;
			}
			else if(currentPitch < -maxPitch)
			{
				currentPitch = -maxPitch;
			}

			transform.rotation.identity()
					.rotateY((float) Math.toRadians(currentYaw))
					.rotateX((float) Math.toRadians(currentPitch));
		}

		Vector3f cameraEyePosition = new Vector3f(transform.position).add(offset);

		// Update view matrix
		viewMatrix.identity();
		viewMatrix.rotation(transform.rotation.conjugate(new Quaternionf()))
				.translate(cameraEyePosition.negate(new Vector3f()));

		// Update projection matrix
		float aspectRatio = (float) Engine.width / (float) Engine.height;
		updateProjectionMatrix(aspectRatio);

		// --- NEW: Update the combined view-projection matrix ---
		projectionMatrix.mul(viewMatrix, viewProjectionMatrix);

		// --- NEW: Update the camera's frustum ---
		frustum.update(viewProjectionMatrix);
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

	public Matrix4f getViewProjectionMatrix()
	{
		return this.viewProjectionMatrix;
	}

	public void setAspectRatio(float aspectRatio)
	{
		updateProjectionMatrix(aspectRatio);
	}

	// --- NEW: Frustum Accessor for external classes ---
	public Frustum getFrustum()
	{
		return frustum;
	}
}