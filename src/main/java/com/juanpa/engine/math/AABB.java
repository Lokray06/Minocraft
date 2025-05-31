package com.juanpa.engine.math; // Or wherever you put math utilities

import org.joml.Vector3f; // Or your own Vector3f implementation

public class AABB {
    public Vector3f min; // Minimum x, y, z coordinates
    public Vector3f max; // Maximum x, y, z coordinates

    public AABB() {
        this.min = new Vector3f();
        this.max = new Vector3f();
    }

    public AABB(Vector3f min, Vector3f max) {
        this.min = new Vector3f(min);
        this.max = new Vector3f(max);
    }

    // Helper to calculate the P-vertex for a given plane normal
    public Vector3f getPVertex(Vector3f planeNormal) {
        float pX = (planeNormal.x >= 0) ? max.x : min.x;
        float pY = (planeNormal.y >= 0) ? max.y : min.y;
        float pZ = (planeNormal.z >= 0) ? max.z : min.z;
        return new Vector3f(pX, pY, pZ);
    }

    // Helper to calculate the N-vertex for a given plane normal
    public Vector3f getNVertex(Vector3f planeNormal) {
        float nX = (planeNormal.x >= 0) ? min.x : max.x;
        float nY = (planeNormal.y >= 0) ? min.y : max.y;
        float nZ = (planeNormal.z >= 0) ? min.z : max.z;
        return new Vector3f(nX, nY, nZ);
    }
}