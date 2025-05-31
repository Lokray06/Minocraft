package com.juanpa.game;

import com.juanpa.engine.Debug;
import com.juanpa.engine.Engine;
import com.juanpa.engine.GameObject; // NEW: Import GameObject
import com.juanpa.engine.input.Input;
import com.juanpa.engine.input.KeyCode;
import com.juanpa.engine.world.World;
import com.juanpa.game.components.PlayerController;
import com.juanpa.engine.components.Camera; // NEW: Import Camera component
import com.juanpa.engine.components.Transform;

import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class Game
{
	private Engine engine;
	private World world;

	private List<GameObject> gameObjects; // NEW: List of top-level GameObjects
	private static Camera activeCamera; // NEW: Reference to the current active camera component

	GameObject player;

	public static byte renderDistance = 32;
	public static long seed = 12345L;

	public Game(int width, int height, String name)
	{
		engine = new Engine(width, height, name, (byte) 4, false);
		engine.init(this); // Initializes Input.java via Window

		gameObjects = new ArrayList<>(); // Initialize the GameObject list
		world = new World(seed, engine.renderer);

		// --- Create Player GameObject ---
		player = new GameObject("Player"); // Create a new GameObject
		player.getTransform().position.set(8, 130, 40); // Set initial position on its Transform
		player.addComponent(new PlayerController()); // Add the PlayerController component
		Camera playerCamera = new Camera();
		player.addComponent(playerCamera); // Add the Camera component
		gameObjects.add(player); // Add player GameObject to the scene graph

		// Set the active camera for rendering
		activeCamera = playerCamera;

		// --- Initialize all GameObjects and their Components (call onStart) ---
		for(GameObject go : gameObjects)
		{
			go.onStart();
		}

		// Lock mouse cursor to the window for FPS-style camera control
		Input.setCursorMode(GLFW.GLFW_CURSOR_DISABLED);

		engine.run();
	}

	public void update()
	{
		if(Input.getKey(KeyCode.ESCAPE))
		{
			engine.requestQuit();
		}

		for(GameObject go : gameObjects)
		{
			go.onUpdate();
		}

		// --- Update Camera Aspect Ratio (if window resized) ---
		if(activeCamera != null)
		{
			activeCamera.setAspectRatio((float) engine.getWindow().getWidth() / engine.getWindow().getHeight());
		}

		// --- Render the World and GameObjects ---
		// The Renderer now needs to get camera matrices directly from the activeCamera
		if(activeCamera != null)
		{
			engine.renderer.clear();
			engine.renderer.render(activeCamera.getViewMatrix(), activeCamera.getProjectionMatrix()); // World renders its chunks using the current camera matrices
			// TODO: Render any other GameObjects that have Renderable components
		}
		else
		{
			Debug.logWarning("No active camera found for rendering!");
		}


		// Update world's player position for chunk loading/unloading
		// Find the player's transform (assuming 'player' is still the ref to the GameObject)
		Transform playerTransform = player.getTransform(); // Direct access via GameObject reference
		if(playerTransform != null)
		{
			world.setPlayerPosition(playerTransform.position);
		}

		world.update(); // Update world logic (e.g., chunk visibility)
	}

	public void dispose()
	{
		// Call onDestroy for all GameObjects
		for(GameObject go : gameObjects)
		{
			go.onDestroy();
		}
		world.dispose();
		//engine.dispose();
	}

	public World getWorld()
	{
		return world;
	}

	public static Camera getActiveCamera()
	{
		return activeCamera;
	}
}