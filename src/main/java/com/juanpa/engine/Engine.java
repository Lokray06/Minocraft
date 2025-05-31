package com.juanpa.engine;

import com.juanpa.engine.input.Input;
import com.juanpa.engine.renderer.Renderer;
import com.juanpa.game.Game;
import org.lwjgl.glfw.GLFW;

public class Engine
{
	private Game game;
	private Window window;
	public Renderer renderer;

	public static int width, height;
	private String name;
	private byte samples;
	private boolean vsync;

	private boolean requestQuit = false;

	// --- Variable Timestep Variables ---
	private double lastFrameTime; // Only need this for deltaTime calculation

	public Engine(int width, int height, String name)
	{
		this(width, height, name, (byte) 4, true);
	}

	public Engine(int width, int height, String name, byte samples)
	{
		this(width, height, name, samples, true);
	}

	public Engine(int width, int height, String name, boolean vsync)
	{
		this(width, height, name, (byte) 4, vsync);
	}

	public Engine(int width, int height, String name, byte samples, boolean vsync)
	{
		this.width = width;
		this.height = height;
		this.name = name;
		this.samples = samples;
		this.vsync = vsync;
	}

	public void init(Game gameInstance)
	{
		this.game = gameInstance;
		window = new Window(width, height, name, samples, vsync);
		Debug.logInfo(window.getWindowHandle());
		renderer = new Renderer();
		Input.init(window.getWindowHandle());

		// Initialize lastFrameTime at the start of the engine
		lastFrameTime = GLFW.glfwGetTime();
	}

	public void run()
	{
		while (!requestQuit && !window.shouldClose())
		{
			double currentTime = GLFW.glfwGetTime();
			double frameTime = currentTime - lastFrameTime;
			lastFrameTime = currentTime;

			// Cap frameTime to avoid very large deltas after pauses/loading
			if (frameTime > 0.25)
			{
				frameTime = 0.25;
			}

			// 1. Input Update: Process all pending input events (keyboard, mouse, etc.)
			Input.pollEvents();

			// Store the calculated delta time in the Time utility class
			Time.deltaTime = (float) frameTime; // Cast to float as deltaTime is float

			// 2. Game Update: Call the Game's update method as fast as possible
			// The game.update() method in your Game class will now implicitly use Time.deltaTime
			game.update(); // No parameter needed here anymore

			// 3. Display the rendered frame
			window.swapBuffers();
		}

		cleanup();
	}

	private void cleanup()
	{
		Debug.logInfo("Requested quitting, cleaning up.");
		if (renderer != null)
		{
			// Add any necessary renderer cleanup here, e.g., dispose of global shaders
		}
		Input.cleanup();
		game.dispose();
		window.cleanup();
	}

	public void requestQuit()
	{
		this.requestQuit = true;
	}

	public Window getWindow()
	{
		return window;
	}
}