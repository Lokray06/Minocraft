// File: com/juanpa/engine/math/Plane.java
package com.juanpa.engine.math;

import org.joml.Vector3f;

public class Plane {
	public Vector3f normal; // (A, B, C)
	public float distance;  // D

	public Plane() {
		this.normal = new Vector3f();
		this.distance = 0.0f;
	}

	public Plane(Vector3f normal, float distance) {
		this.normal = new Vector3f(normal);
		this.distance = distance;
	}

	// Calculates the signed distance from a point to this plane
	public float getSignedDistance(Vector3f point) {
		return normal.dot(point) + distance;
	}

	// Normalizes the plane's normal vector and distance
	public void normalize() {
		float magnitude = normal.length();
		if (magnitude > 0.0f) {
			normal.div(magnitude);
			distance /= magnitude;
		}
	}
}