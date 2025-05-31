package com.juanpa.engine.input;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWScrollCallback;

import com.juanpa.engine.Debug;

import org.joml.Vector2d; // For mouse position and delta

import java.util.Arrays;

public class Input
{
	private static long windowHandle;

	// Maximum number of keys supported by GLFW.
	private static final int MAX_KEYS = GLFW.GLFW_KEY_LAST + 1;
	private static boolean[] currentKeys = new boolean[MAX_KEYS];
	private static boolean[] previousKeys = new boolean[MAX_KEYS];

	// --- Mouse Button Fields ---
	private static final int MAX_MOUSE_BUTTONS = GLFW.GLFW_MOUSE_BUTTON_LAST + 1;
	private static boolean[] currentMouseButtons = new boolean[MAX_MOUSE_BUTTONS];
	private static boolean[] previousMouseButtons = new boolean[MAX_MOUSE_BUTTONS];

	// --- Mouse Position Fields ---
	private static Vector2d currentMousePos = new Vector2d();
	private static Vector2d previousMousePos = new Vector2d();
	private static Vector2d mouseDelta = new Vector2d(); // Change in mouse position since last frame

	// --- Mouse Scroll Field ---
	private static float scrollDeltaY; // Vertical scroll delta

	// --- Callbacks ---
	private static GLFWKeyCallback keyCallback;
	private static GLFWMouseButtonCallback mouseButtonCallback;
	private static GLFWCursorPosCallback cursorPosCallback;
	private static GLFWScrollCallback scrollCallback;

	// Private constructor to prevent instantiation
	private Input()
	{
	}

	public static void init(long handle)
	{
		windowHandle = handle;

		// Initialize key states
		Arrays.fill(currentKeys, false);
		Arrays.fill(previousKeys, false);

		// Initialize mouse button states
		Arrays.fill(currentMouseButtons, false);
		Arrays.fill(previousMouseButtons, false);

		// Setup key callback
		keyCallback = GLFWKeyCallback.create((window, key, scancode, action, mods) -> {
			if (key >= 0 && key < MAX_KEYS)
			{ // Ensure key is within bounds
				if (action == GLFW.GLFW_PRESS)
				{
					currentKeys[key] = true;
				} else if (action == GLFW.GLFW_RELEASE)
				{
					currentKeys[key] = false;
				}
			}
		});
		GLFW.glfwSetKeyCallback(windowHandle, keyCallback);

		// --- Setup Mouse Button Callback ---
		mouseButtonCallback = GLFWMouseButtonCallback.create((window, button, action, mods) -> {
			if (button >= 0 && button < MAX_MOUSE_BUTTONS)
			{
				if (action == GLFW.GLFW_PRESS)
					currentMouseButtons[button] = true;
				else if (action == GLFW.GLFW_RELEASE)
					currentMouseButtons[button] = false;
			}
		});
		GLFW.glfwSetMouseButtonCallback(windowHandle, mouseButtonCallback);

		// --- Setup Cursor Position Callback ---
		cursorPosCallback = GLFWCursorPosCallback.create((window, xpos, ypos) -> {
			currentMousePos.set(xpos, ypos);
		});
		GLFW.glfwSetCursorPosCallback(windowHandle, cursorPosCallback);

		// --- Setup Scroll Callback ---
		scrollCallback = GLFWScrollCallback.create((window, xoffset, yoffset) -> {
			scrollDeltaY = (float) yoffset; // Only tracking vertical scroll for now
		});
		GLFW.glfwSetScrollCallback(windowHandle, scrollCallback);

		// Get initial mouse position to avoid a large delta on the first frame
		double[] x = new double[1], y = new double[1];
		GLFW.glfwGetCursorPos(windowHandle, x, y);
		currentMousePos.set(x[0], y[0]);
		previousMousePos.set(x[0], y[0]);
	}

	public static void pollEvents()
	{
		// Update previous key states
		System.arraycopy(currentKeys, 0, previousKeys, 0, MAX_KEYS);

		// --- Update previous mouse button states ---
		System.arraycopy(currentMouseButtons, 0, previousMouseButtons, 0, MAX_MOUSE_BUTTONS);

		// --- Store current mouse pos as previous BEFORE polling for new events ---
		previousMousePos.set(currentMousePos);

		// Poll for window events. Callbacks will be invoked by this.
		GLFW.glfwPollEvents();

		// --- Calculate mouse delta AFTER polling for new position ---
		mouseDelta.set(currentMousePos.x - previousMousePos.x, currentMousePos.y - previousMousePos.y);

		// --- Reset scroll delta after processing for the current frame ---
		scrollDeltaY = 0.0f;
	}

	/**
	 * Checks if a specific key is currently held down.
	 *
	 * @param keyCode The key to check.
	 * @return true if the key is pressed, false otherwise.
	 */
	public static boolean getKey(KeyCode keyCode)
	{
		if (keyCode == KeyCode.UNKNOWN)
			return false;
		return currentKeys[keyCode.getGlfwCode()];
	}

	/**
	 * Checks if a specific key was pressed down in the current frame.
	 *
	 * @param keyCode The key to check.
	 * @return true if the key was just pressed, false otherwise.
	 */
	public static boolean getKeyDown(KeyCode keyCode)
	{
		if (keyCode == KeyCode.UNKNOWN)
			return false;
		int glfwCode = keyCode.getGlfwCode();
		return currentKeys[glfwCode] && !previousKeys[glfwCode];
	}

	/**
	 * Checks if a specific key was released in the current frame.
	 *
	 * @param keyCode The key to check.
	 * @return true if the key was just released, false otherwise.
	 */
	public static boolean getKeyUp(KeyCode keyCode)
	{
		if (keyCode == KeyCode.UNKNOWN)
			return false;
		int glfwCode = keyCode.getGlfwCode();
		return !currentKeys[glfwCode] && previousKeys[glfwCode];
	}

	/**
	 * Gets the value of the specified input axis. For keyboard, this is typically -1, 0, or 1.
	 *
	 * @param axis The axis to check.
	 * @return The axis value.
	 */
	public static float getAxis(Axis axis)
	{
		switch (axis)
		{
		case HORIZONTAL:
			float horizontal = 0.0f;
			if (getKey(KeyCode.D) || getKey(KeyCode.RIGHT))
			{
				horizontal += 1.0f;
			}
			if (getKey(KeyCode.A) || getKey(KeyCode.LEFT))
			{
				horizontal -= 1.0f;
			}
			return horizontal;
		case VERTICAL:
			float vertical = 0.0f;
			if (getKey(KeyCode.W) || getKey(KeyCode.UP))
			{
				vertical += 1.0f;
			}
			if (getKey(KeyCode.S) || getKey(KeyCode.DOWN))
			{
				vertical -= 1.0f;
			}
			return vertical;
		// Add other axes as needed
		default:
			return 0.0f;
		}
	}

	// --- NEW: Mouse Input Methods ---

	/**
	 * Checks if a specific mouse button is currently held down.
	 * 
	 * @param button The GLFW mouse button code (e.g., GLFW.GLFW_MOUSE_BUTTON_LEFT).
	 * @return true if the button is pressed, false otherwise.
	 */
	public static boolean getMouseButton(int button)
	{
		if (button >= 0 && button < MAX_MOUSE_BUTTONS)
		{
			return currentMouseButtons[button];
		}
		return false;
	}

	/**
	 * Checks if a specific mouse button was pressed down in the current frame.
	 * 
	 * @param button The GLFW mouse button code.
	 * @return true if the button was just pressed, false otherwise.
	 */
	public static boolean getMouseButtonDown(int button)
	{
		if (button >= 0 && button < MAX_MOUSE_BUTTONS)
		{
			return currentMouseButtons[button] && !previousMouseButtons[button];
		}
		return false;
	}

	/**
	 * Checks if a specific mouse button was released in the current frame.
	 * 
	 * @param button The GLFW mouse button code.
	 * @return true if the button was just released, false otherwise.
	 */
	public static boolean getMouseButtonUp(int button)
	{
		if (button >= 0 && button < MAX_MOUSE_BUTTONS)
		{
			return !currentMouseButtons[button] && previousMouseButtons[button];
		}
		return false;
	}

	/**
	 * Gets the current absolute mouse position.
	 * 
	 * @return A Vector2d representing the (x, y) coordinates of the mouse cursor.
	 */
	public static Vector2d getMousePos()
	{
		return currentMousePos;
	}

	/**
	 * Gets the change in mouse position since the last frame. This is crucial for mouse look (camera rotation).
	 * 
	 * @return A Vector2d representing the (deltaX, deltaY) of the mouse.
	 */
	public static Vector2d getMouseDelta()
	{
		return mouseDelta;
	}

	/**
	 * Gets the vertical scroll wheel delta for the current frame.
	 * 
	 * @return The vertical scroll amount. Positive for scrolling up, negative for scrolling down.
	 */
	public static float getScrollDeltaY()
	{
		return scrollDeltaY;
	}

	/**
	 * Sets the cursor mode (e.g., normal, hidden, disabled/locked). Useful for FPS camera control.
	 * 
	 * @param mode The GLFW cursor mode constant (e.g., GLFW.GLFW_CURSOR_NORMAL, GLFW.GLFW_CURSOR_HIDDEN, GLFW.GLFW_CURSOR_DISABLED).
	 */
	public static void setCursorMode(int mode)
	{
		GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, mode);
	}

	public static void cleanup()
{
    // First, unregister callbacks from GLFW.
    // This tells GLFW to no longer use the associated native pointers.
    if (windowHandle != 0L)
    {
        GLFW.glfwSetKeyCallback(windowHandle, null);
        GLFW.glfwSetMouseButtonCallback(windowHandle, null);
        GLFW.glfwSetCursorPosCallback(windowHandle, null);
        GLFW.glfwSetScrollCallback(windowHandle, null);
    }

    // Now, free the Java callback objects.
    // This releases the internal native resources held by the LWJGL Callback objects.
    if (keyCallback != null)
    {
        // Debug.log"Key callback GLFW Object: " + keyCallback + ", disposing it");
        keyCallback.free();
        keyCallback = null;
    }
    if (mouseButtonCallback != null)
    {
        mouseButtonCallback.free();
        mouseButtonCallback = null;
    }
    if (cursorPosCallback != null)
    {
        cursorPosCallback.free();
        cursorPosCallback = null;
    }
    if (scrollCallback != null)
    {
        scrollCallback.free();
        scrollCallback = null;
    }
}
}