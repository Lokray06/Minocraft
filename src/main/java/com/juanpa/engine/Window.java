package com.juanpa.engine;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.GLFW.*;

public class Window
{
	private long windowHandle;

	int width, height;
	String name;
	byte samples;
	boolean vsync;

	// --- FPS Counter Variables ---
	private double lastFPSTime;
	private int frames;
	private String currentTitle; // To store the base title
	// -----------------------------

	public Window(int width, int height, String name, byte samples, boolean vsync)
	{
		this.width = width;
		this.height = height;
		this.name = name; // Store the original window name
		this.samples = samples;
		this.vsync = vsync;

		init();
	}

	//GLFW Window and context initialization given the config
	private void init()
	{
		GLFWErrorCallback.createPrint(System.err).set();

		//Init the GLFW context, if it fails, throw an IllegalStateException error
		if(!glfwInit())
			throw new IllegalStateException("Unable to initialize GLFW");

		glfwDefaultWindowHints();
		glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
		glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
		//Set the MSAA samples
		glfwWindowHint(GLFW_SAMPLES, samples);

		glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6);
		glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);


		//Create the window handle
		windowHandle = glfwCreateWindow(width, height, name, 0, 0);
		if(windowHandle == 0)
		{
			throw new RuntimeException("Failed to create the OpenGL window");
		}

		//Make the OpenGL context the active
		glfwMakeContextCurrent(windowHandle);
		//Makes OpenGL's functions available for the current context
		GL.createCapabilities();

		//Set the vsync state
		int swapInterval = vsync ? 1 : 0;
		glfwSwapInterval(swapInterval);

		//Show the window
		glfwShowWindow(windowHandle);

		Debug.logInfo("GLFW platform: " + org.lwjgl.glfw.GLFW.glfwGetPlatform());

		// Initialize FPS counter
		this.lastFPSTime = glfwGetTime();
		this.frames = 0;
		this.currentTitle = name; // Set initial title
	}

	/**
	 * Returns the raw GLFW window handle. Useful for Input and Renderer classes.
	 *
	 * @return The GLFW window handle.
	 */
	public long getWindowHandle()
	{
		return windowHandle;
	}

	/**
	 * Checks if the window should close (e.g., user clicked the close button).
	 *
	 * @return true if the window should close, false otherwise.
	 */
	public boolean shouldClose()
	{
		return glfwWindowShouldClose(windowHandle);
	}

	/**
	 * Swaps the front and back buffers, displaying the rendered frame.
	 * This method also updates the FPS counter in the window title.
	 */
	public void swapBuffers()
	{
		glfwSwapBuffers(windowHandle);

		// --- FPS Counter Logic ---
		frames++;
		double currentTime = glfwGetTime();
		if(currentTime - lastFPSTime >= 1.0)
		{ // If at least one second has passed
			int fps = frames;
			glfwSetWindowTitle(windowHandle, currentTitle + " | FPS: " + fps);
			frames = 0;
			lastFPSTime = currentTime;
		}
		// --------------------------
	}

	/**
	 * Cleans up GLFW window resources.
	 */
	public void cleanup()
	{
		glfwPollEvents(); // Process any pending events
		glfwSetWindowSize(windowHandle, 1, 1); // Optional: Resize to tiny before destroying
		// Free the window callbacks and destroy the window
		glfwDestroyWindow(windowHandle);
		// Terminate GLFW. This needs to be called when the application exits.
		glfwTerminate();
		// Free the error callback
		glfwSetErrorCallback(null).free();
	}

	public int getWidth()
	{
		return width;
	}

	public int getHeight()
	{
		return height;
	}

	/**
	 * Sets the base title of the window. The FPS counter will be appended to this.
	 *
	 * @param title The new base title.
	 */
	public void setTitle(String title)
	{
		this.currentTitle = title;
		glfwSetWindowTitle(windowHandle, this.currentTitle); // Update immediately
	}
}