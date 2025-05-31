// File: com/juanpa/engine/math/Frustum.java
package com.juanpa.engine.math; // Ensure this package is correct

import org.joml.Matrix4f;
import org.joml.Vector3f;

// Make sure com.juanpa.engine.math.AABB is also defined
// and available for use here (as it already is).

public class Frustum {
	private Plane[] planes; // Array to hold the 6 frustum planes

	public Frustum() {
		planes = new Plane[6];
		for (int i = 0; i < 6; i++) {
			planes[i] = new Plane();
		}
	}

	/**
	 * Updates the frustum planes based on the given view-projection matrix.
	 */
	public void update(Matrix4f viewProjectionMatrix) {
		float m00 = viewProjectionMatrix.m00(); float m01 = viewProjectionMatrix.m01(); float m02 = viewProjectionMatrix.m02(); float m03 = viewProjectionMatrix.m03();
		float m10 = viewProjectionMatrix.m10(); float m11 = viewProjectionMatrix.m11(); float m12 = viewProjectionMatrix.m12(); float m13 = viewProjectionMatrix.m13();
		float m20 = viewProjectionMatrix.m20(); float m21 = viewProjectionMatrix.m21(); float m22 = viewProjectionMatrix.m22(); float m23 = viewProjectionMatrix.m23();
		float m30 = viewProjectionMatrix.m30(); float m31 = viewProjectionMatrix.m31(); float m32 = viewProjectionMatrix.m32(); float m33 = viewProjectionMatrix.m33();

		// Right plane
		planes[0].normal.set(m03 - m00, m13 - m10, m23 - m20);
		planes[0].distance = m33 - m30;
		planes[0].normalize();

		// Left plane
		planes[1].normal.set(m03 + m00, m13 + m10, m23 + m20);
		planes[1].distance = m33 + m30;
		planes[1].normalize();

		// Bottom plane
		planes[2].normal.set(m03 + m01, m13 + m11, m23 + m21);
		planes[2].distance = m33 + m31;
		planes[2].normalize();

		// Top plane
		planes[3].normal.set(m03 - m01, m13 - m11, m23 - m21);
		planes[3].distance = m33 - m31;
		planes[3].normalize();

		// Far plane
		planes[4].normal.set(m03 - m02, m13 - m12, m23 - m22);
		planes[4].distance = m33 - m32;
		planes[4].normalize();

		// Near plane
		planes[5].normal.set(m03 + m02, m13 + m12, m23 + m22);
		planes[5].distance = m33 + m32;
		planes[5].normalize();
	}

	/**
	 * Enum for frustum test results.
	 */
	public enum FrustumResult {
		INSIDE,    // Bounding volume is fully inside the frustum
		OUTSIDE,   // Bounding volume is completely outside the frustum
		INTERSECT  // Bounding volume intersects the frustum (partially inside)
	}

	/**
	 * Checks if an AABB intersects the frustum.
	 *
	 * @param aabb The AABB to test.
	 * @return FrustumResult indicating the intersection status.
	 */
	public FrustumResult intersects(AABB aabb) { // Now AABB is directly imported
		boolean intersect = false;

		for (Plane plane : planes) {
			Vector3f nVertex = aabb.getNVertex(plane.normal);
			if (plane.getSignedDistance(nVertex) < 0) {
				return FrustumResult.OUTSIDE;
			}

			Vector3f pVertex = aabb.getPVertex(plane.normal);
			if (plane.getSignedDistance(pVertex) < 0) {
				intersect = true;
			}
		}
		return intersect ? FrustumResult.INTERSECT : FrustumResult.INSIDE;
	}
}