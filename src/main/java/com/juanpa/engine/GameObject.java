package com.juanpa.engine;

import com.juanpa.engine.components.Component;
import com.juanpa.engine.components.Transform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map; // For getting components of type
import java.util.HashMap; // For storing components efficiently

public class GameObject
{
	private String name;
	private Transform transform; // Every GameObject should have a Transform
	private List<Component> components;
	private Map<Class<? extends Component>, Component> componentMap; // For quick lookup by type

	private GameObject parent;
	private List<GameObject> children;

	private boolean isActive = true; // For enabling/disabling GameObjects

	public GameObject(String name)
	{
		this.name = name;
		this.components = new ArrayList<>();
		this.componentMap = new HashMap<>();
		this.children = new ArrayList<>();
		// Add a Transform component by default, it's essential
		this.transform = new Transform();
		addComponent(this.transform); // Add it to the component list and map
	}

	public void addComponent(Component component)
	{
		// Ensure only one of each component type (if desired, like Transform or Camera)
		// For now, allow multiple unless explicitly handled by the component logic itself.
		// For Transform, we'll enforce it later.
		components.add(component);
		component.setGameObject(this); // Set the component's owner
		componentMap.put(component.getClass(), component); // Store for quick retrieval
	}

	@SuppressWarnings("unchecked")
	// Safe cast due to type checking in put()
	public <T extends Component> T getComponent(Class<T> componentClass)
	{
		return (T) componentMap.get(componentClass);
	}

	// You might want methods to remove components later

	public void addChild(GameObject child)
	{
		if(child.parent != null)
		{
			child.parent.removeChild(child); // Remove from old parent
		}
		child.setParent(this);
		this.children.add(child);
	}

	public void removeChild(GameObject child)
	{
		this.children.remove(child);
		child.setParent(null);
	}

	public Transform getTransform()
	{
		return transform;
	}

	public String getName()
	{
		return name;
	}

	public GameObject getParent()
	{
		return parent;
	}

	public void setParent(GameObject parent)
	{
		this.parent = parent;
		// When parent changes, update transform relationship (e.g., local vs. world position)
		// This is advanced and comes later. For now, assume simple hierarchy.
	}

	public List<GameObject> getChildren()
	{
		return children;
	}

	public boolean isActive()
	{
		return isActive;
	}

	public void setActive(boolean active)
	{
		this.isActive = active;
	}

	/**
	 * Initializes all components of this GameObject and its children.
	 */
	public void onStart()
	{
		if(!isActive)
			return;

		for(Component component : components)
		{
			component.onStart();
		}
		for(GameObject child : children)
		{
			child.onStart(); // Recursively call on children
		}
	}

	/**
	 * Updates all components of this GameObject and its children.
	 */
	public void onUpdate()
	{
		if(!isActive)
			return;

		for(Component component : components)
		{
			component.onUpdate();
		}
		for(GameObject child : children)
		{
			child.onUpdate(); // Recursively call on children
		}
	}

	/**
	 * Fixed updates all components of this GameObject and its children.
	 */
	public void onFixedUpdate()
	{
		if(!isActive)
			return;

		for(Component component : components)
		{
			component.onFixedUpdate();
		}
		for(GameObject child : children)
		{
			child.onFixedUpdate(); // Recursively call on children
		}
	}

	/**
	 * Cleans up all components of this GameObject and its children.
	 */
	public void onDestroy()
	{
		for(Component component : components)
		{
			component.onDestroy();
		}
		for(GameObject child : children)
		{
			child.onDestroy(); // Recursively call on children
		}
		// Remove from parent if exists
		if(parent != null)
		{
			parent.removeChild(this);
		}
	}

	// Helper to find a component of a specific type (e.g., for CameraComponent)
	// You'd typically find it in the GameObject that *has* the camera component.
	// This is more for general utility, not core update loop.
	public <T extends Component> T findComponentInSelfOrChildren(Class<T> componentClass)
	{
		T component = getComponent(componentClass);
		if(component != null)
		{
			return component;
		}
		for(GameObject child : children)
		{
			T foundInChild = child.findComponentInSelfOrChildren(componentClass);
			if(foundInChild != null)
			{
				return foundInChild;
			}
		}
		return null;
	}
}