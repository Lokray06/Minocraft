package com.juanpa.engine.components;

import com.juanpa.engine.GameObject; // Will create this soon

public abstract class Component {
	protected GameObject gameObject; // The GameObject this component is attached to

	public void setGameObject(GameObject gameObject) {
		this.gameObject = gameObject;
	}

	public GameObject getGameObject() {
		return gameObject;
	}

	/**
	 * Called when the component is first created and enabled.
	 * Use this for initialization that depends on other components being ready.
	 */
	public void onStart() {
		// Default empty implementation
	}

	/**
	 * Called once per frame.
	 * @param deltaTime The time in seconds since the last frame.
	 */
	public void onUpdate() {
		// Default empty implementation
	}

	/**
	 * Called at fixed time intervals, primarily for physics updates.
	 * Note: You'll need to set up a fixed timestep loop in your Game class for this.
	 */
	public void onFixedUpdate() {
		// Default empty implementation
	}

	/**
	 * Called when the component is about to be destroyed.
	 * Use this for cleanup.
	 */
	public void onDestroy() {
		// Default empty implementation
	}
}