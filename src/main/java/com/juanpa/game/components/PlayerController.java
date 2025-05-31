package com.juanpa.game.components; // CHANGED PACKAGE

import com.juanpa.engine.Debug;
import com.juanpa.engine.Time;
import com.juanpa.engine.components.Component;
import com.juanpa.engine.components.Transform;
import com.juanpa.engine.input.Input; // Get raw input
import com.juanpa.engine.input.KeyCode;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

public class PlayerController extends Component
{

    private final float MOVEMENT_SPEED = 5.0f; // Units per second
    private final float VERTICAL_SPEED = 5.0f;
    private final float SPRINT_SPEED = 10.0f; // Define your sprint multiplier here
    private final float NORMAL_SPEED = 5.0f; // Define your sprint multiplier here

    public PlayerController()
    {
        // Constructor for the component
    }

    @Override
    public void onUpdate()
    {
        Transform transform = gameObject.getTransform(); // Get the Transform of the GameObject this is attached to
        if(transform == null)
        {
            Debug.logError("PlayerController component's GameObject '" + gameObject.getName() + "' has no Transform!");
            return;
        }

        Vector3f movementInput = new Vector3f(0, 0, 0); // Use a temporary vector for input direction

        // --- Calculate movement based on player's current rotation ---
        // Get the forward vector relative to the current rotation (-Z is usually forward in JOML)

        Vector3f forwardDirection = new Vector3f(0, 0, -1);
        transform.rotation.transform(forwardDirection);
        forwardDirection.y = 0; // Flatten to horizontal plane
        if(forwardDirection.lengthSquared() > 0)
        {
            forwardDirection.normalize();
        }

        // Get the right vector relative to the current rotation (+X is usually right)
        Vector3f rightDirection = new Vector3f(1, 0, 0);
        transform.rotation.transform(rightDirection);
        rightDirection.y = 0; // Flatten to horizontal plane
        if(rightDirection.lengthSquared() > 0)
        {
            rightDirection.normalize();
        }

        // Determine raw directional input (do not multiply by speed yet)
        if(Input.getKey(KeyCode.W))
        {
            movementInput.add(forwardDirection);
        }
        if(Input.getKey(KeyCode.S))
        {
            movementInput.sub(forwardDirection);
        }
        if(Input.getKey(KeyCode.D))
        {
            movementInput.add(rightDirection);
        }
        if(Input.getKey(KeyCode.A))
        {
            movementInput.sub(rightDirection);
        }

        // --- Normalize horizontal movement and apply speed multiplier ---
        float currentMovementSpeed = MOVEMENT_SPEED;
        if(Input.getKey(KeyCode.LEFT_CONTROL))
        {
            currentMovementSpeed *= SPRINT_SPEED; // Apply sprint multiplier
        }
        else
        {
            currentMovementSpeed *= NORMAL_SPEED;
        }

        // --- Apply vertical movement (not relative to camera's forward/right) ---
        if(Input.getKey(KeyCode.SPACE))
        {
            movementInput.y += currentMovementSpeed / 10; // Move up along world Y
        }
        if(Input.getKey(KeyCode.LEFT_SHIFT))
        {
            movementInput.y -= currentMovementSpeed / 10; // Move down along world Y
        }

        // Separate horizontal and vertical components for normalization and speed application
        Vector3f horizontalMovement = new Vector3f(movementInput.x, 0, movementInput.z);
        if(horizontalMovement.lengthSquared() > 0.0001f) // Check for non-zero length to avoid division by zero
        {
            horizontalMovement.normalize().mul(currentMovementSpeed * Time.deltaTime);
        }

        // Apply vertical speed separately
        float verticalMovement = movementInput.y * VERTICAL_SPEED * Time.deltaTime;


        // Combine and apply to transform
        transform.position.x += horizontalMovement.x;
        transform.position.z += horizontalMovement.z;
        transform.position.y += verticalMovement; // Use the scaled vertical movement

        // Optional: Check mouse clicks for actions if needed
        if(Input.getMouseButtonDown(GLFW.GLFW_MOUSE_BUTTON_LEFT))
        {
            // Left click action
        }
        if(Input.getMouseButtonDown(GLFW.GLFW_MOUSE_BUTTON_RIGHT))
        {
            // Right click action
        }
    }
}