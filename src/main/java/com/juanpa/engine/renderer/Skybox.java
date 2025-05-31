package com.juanpa.engine.renderer;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL30;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class Skybox
{
    private static int vao = -1;
    private static int vbo = -1;
    private static ShaderProgram shader;

    private static final float[] CUBE_VERTICES = { -1, 1, -1, -1, -1, -1, 1, -1, -1, 1, -1, -1, 1, 1, -1, -1, 1, -1, -1, -1, 1, -1, -1, -1, -1, 1, -1, -1, 1, -1, -1, 1, 1, -1, -1, 1, 1, -1, -1, 1, -1, 1, 1, 1, 1, 1, 1, 1, 1, 1, -1, 1, -1, -1, -1, -1, 1, -1, 1, 1, 1, 1, 1, 1, 1, 1, 1, -1, 1, -1, -1, 1, -1, 1, -1, 1, 1, -1, 1, 1, 1, 1, 1, 1, -1, 1, 1, -1, 1, -1, -1, -1, -1, -1, -1, 1, 1, -1, -1, 1, -1, -1, -1, -1, 1, 1, -1, 1 };

    public static void init()
    {
        vao = glGenVertexArrays();
        vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, CUBE_VERTICES, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        shader = new ShaderProgram("/shaders/skybox.vert", "/shaders/skybox.frag");
    }

    public static void render(Matrix4f viewMatrix, Matrix4f projectionMatrix)
    {
        glDepthFunc(GL_LEQUAL);
        glDepthMask(false);

        shader.use();
        Matrix4f viewNoTranslation = new Matrix4f(viewMatrix);
        viewNoTranslation.m30(0).m31(0).m32(0); // Remove translation
        shader.setUniform("view", viewNoTranslation);
        shader.setUniform("projection", projectionMatrix);
        shader.setUniform("skyColor", new Vector3f(0.4f, 0.6f, 0.9f));

        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 36);
        glBindVertexArray(0);

        shader.unuse();

        glDepthMask(true);
        glDepthFunc(GL_LESS);
    }
}
