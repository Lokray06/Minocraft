package com.juanpa.engine.renderer;

import com.juanpa.engine.Debug;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

public class ShaderProgram
{

	private final int programId;
	private int vertexShaderId;
	private int fragmentShaderId;

	// Cache uniform locations to avoid repeated lookups
	private final Map<String, Integer> uniforms;

	/**
	 * Creates a new shader program by loading, compiling, and linking shaders.
	 *
	 * @param vertexShaderPath   The path to the vertex shader source file (e.g., "shaders/vertex.glsl").
	 * @param fragmentShaderPath The path to the fragment shader source file (e.g., "shaders/fragment.glsl").
	 * @throws Exception If there's an error in loading, compiling, or linking the shaders.
	 */
	public ShaderProgram(String vertexShaderPath, String fragmentShaderPath)
	{
		programId = GL20.glCreateProgram();
		Debug.checkGLError("ShaderProgram: glCreateProgram"); // <-- ADDED LOG

		if(programId == 0)
		{
			Debug.logError("Could not create ShaderProgram. Program ID is 0."); // More specific message
			// Throw an exception here if you want to stop execution on failure
			throw new RuntimeException("Could not create ShaderProgram");
		}

		uniforms = new HashMap<>();

		// Load, compile, and attach vertex shader
		vertexShaderId = createShader(vertexShaderPath, GL20.GL_VERTEX_SHADER);
		// Load, compile, and attach fragment shader
		fragmentShaderId = createShader(fragmentShaderPath, GL20.GL_FRAGMENT_SHADER);

		link(); // Link also has its own checks
	}

	/**
	 * Reads shader source from a file, compiles it, and attaches it to the program.
	 *
	 * @param shaderPath The path to the shader source file.
	 * @param shaderType The type of shader (e.g., GL20.GL_VERTEX_SHADER, GL20.GL_FRAGMENT_SHADER).
	 * @return The ID of the compiled shader.
	 * @throws Exception If the shader cannot be loaded or compiled.
	 */
	private int createShader(String shaderPath, int shaderType)
	{
		String shaderSource = loadResource(shaderPath);
		int shaderId = GL20.glCreateShader(shaderType);
		Debug.checkGLError("createShader: glCreateShader (" + shaderPath + ", " + shaderType + ")"); // <-- ADDED LOG
		if(shaderId == 0)
		{
			Debug.logError("Error creating shader. Shader ID is 0. Type: " + shaderType + ", Path: " + shaderPath); // More specific
			throw new RuntimeException("Error creating shader for " + shaderPath); // Throw to stop if critical
		}

		GL20.glShaderSource(shaderId, shaderSource);
		Debug.checkGLError("createShader: glShaderSource (" + shaderPath + ")"); // <-- ADDED LOG
		GL20.glCompileShader(shaderId);
		Debug.checkGLError("createShader: glCompileShader (" + shaderPath + ")"); // <-- ADDED LOG

		if(GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == 0)
		{
			String infoLog = GL20.glGetShaderInfoLog(shaderId, 1024);
			Debug.logError("Error compiling shader [" + shaderPath + "]: " + infoLog); // <-- ADDED LOG for error info
			GL20.glDeleteShader(shaderId); // Delete the failed shader
			throw new RuntimeException("Shader compilation failed for " + shaderPath + ": " + infoLog); // Throw for critical error
		}

		GL20.glAttachShader(programId, shaderId);
		Debug.checkGLError("createShader: glAttachShader (" + shaderPath + ")"); // <-- ADDED LOG
		return shaderId;
	}

	/**
	 * Links the attached shaders into an executable program.
	 *
	 * @throws Exception If the program cannot be linked or validated.
	 */
	private void link()
	{
		GL20.glLinkProgram(programId);
		Debug.checkGLError("link: glLinkProgram"); // <-- ADDED LOG
		if(GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == 0)
		{
			String infoLog = GL20.glGetProgramInfoLog(programId, 1024);
			Debug.logError("Error linking ShaderProgram: " + infoLog); // <-- ADDED LOG for error info
			GL20.glDeleteProgram(programId); // Delete the failed program
			throw new RuntimeException("ShaderProgram linking failed: " + infoLog); // Throw for critical error
		}

		// Detach shaders after linking (they are part of the program now)
		if(vertexShaderId != 0)
		{
			GL20.glDetachShader(programId, vertexShaderId);
			Debug.checkGLError("link: glDetachShader (vertex)"); // <-- ADDED LOG
		}
		if(fragmentShaderId != 0)
		{
			GL20.glDetachShader(programId, fragmentShaderId);
			Debug.checkGLError("link: glDetachShader (fragment)"); // <-- ADDED LOG
		}

		// Validate program (optional, good for debugging)
		GL20.glValidateProgram(programId);
		Debug.checkGLError("link: glValidateProgram"); // <-- ADDED LOG
		if(GL20.glGetProgrami(programId, GL20.GL_VALIDATE_STATUS) == 0)
		{
			String infoLog = GL20.glGetProgramInfoLog(programId, 1024);
			Debug.logWarning("Warning validating ShaderProgram: " + infoLog); // <-- Use Debug.logWarning and log info
		}
	}

	/**
	 * Loads a resource (like a shader file) from the classpath as a String.
	 *
	 * @param fileName The path to the resource.
	 * @return The content of the resource as a String.
	 * @throws IOException If the file cannot be read.
	 */
	private String loadResource(String fileName)
	{
		StringBuilder result = new StringBuilder();
		try(InputStream in = Class.forName(ShaderProgram.class.getName()).getResourceAsStream(fileName);
			BufferedReader reader = new BufferedReader(new InputStreamReader(in)))
		{
			String line;
			while((line = reader.readLine()) != null)
			{
				result.append(line).append("\n");
			}
		}
		catch(ClassNotFoundException | IOException e)
		{
			Debug.logError("Error loading resource [" + fileName + "]: " + e.toString()); // More specific error log
			throw new RuntimeException(e);
		}
		return result.toString();
	}

	/**
	 * Gets the location of a uniform variable within the shader program.
	 * Caches the location to avoid repeated lookups.
	 *
	 * @param uniformName The name of the uniform variable.
	 * @return The uniform location ID.
	 */
	public int getUniformLocation(String uniformName)
	{
		// Use computeIfAbsent for efficient caching
		return uniforms.computeIfAbsent(uniformName, name ->
		{
			int location = GL20.glGetUniformLocation(programId, name);
			Debug.checkGLError("getUniformLocation: glGetUniformLocation ('" + name + "')"); // <-- ADDED LOG
			if(location < 0)
			{
				Debug.logWarning("Uniform '" + name + "' not found in shader program " + programId); // Use Debug.logWarning
			}
			return location;
		});
	}

	// --- Uniform Setting Methods ---

	public void setUniform(String uniformName, Matrix4f value)
	{
		try(MemoryStack stack = MemoryStack.stackPush())
		{
			FloatBuffer fb = stack.mallocFloat(16);
			value.get(fb);
			GL20.glUniformMatrix4fv(getUniformLocation(uniformName), false, fb);
			Debug.checkGLError("setUniform: glUniformMatrix4fv ('" + uniformName + "')"); // <-- ADDED LOG
		}
	}

	public void setUniform(String uniformName, Vector3f value)
	{
		try(MemoryStack stack = MemoryStack.stackPush())
		{
			FloatBuffer fb = stack.mallocFloat(3);
			value.get(fb);
			GL20.glUniform3fv(getUniformLocation(uniformName), fb);
			Debug.checkGLError("setUniform: glUniform3fv ('" + uniformName + "')"); // <-- ADDED LOG
		}
	}

	public void setUniform(String uniformName, int value)
	{
		GL20.glUniform1i(getUniformLocation(uniformName), value);
		Debug.checkGLError("setUniform: glUniform1i ('" + uniformName + "')"); // <-- ADDED LOG
	}

	public void setUniform(String uniformName, float value)
	{
		GL20.glUniform1f(getUniformLocation(uniformName), value);
		Debug.checkGLError("setUniform: glUniform1f ('" + uniformName + "')"); // <-- ADDED LOG
	}

	// Add more uniform setting methods as needed (e.g., Vector2f, Vector4f, arrays, etc.)

	/**
	 * Activates this shader program for rendering.
	 */
	public void use()
	{
		GL20.glUseProgram(programId);
		Debug.checkGLError("use: glUseProgram (" + programId + ")"); // <-- ADDED LOG
	}

	/**
	 * Deactivates this shader program.
	 */
	public void unuse()
	{
		GL20.glUseProgram(0);
		Debug.checkGLError("unuse: glUseProgram (0)"); // <-- ADDED LOG
	}

	/**
	 * Cleans up (deletes) the shader program and its associated shaders from OpenGL memory.
	 */
	public void cleanup()
	{
		unuse(); // Ensure it's not active before deleting

		if(programId != 0)
		{
			// Detach shaders if they were not already detached (though they should be after link)
			if(vertexShaderId != 0)
			{
				GL20.glDetachShader(programId, vertexShaderId);
				Debug.checkGLError("cleanup: glDetachShader (vertex)"); // <-- ADDED LOG
			}
			if(fragmentShaderId != 0)
			{
				GL20.glDetachShader(programId, fragmentShaderId);
				Debug.checkGLError("cleanup: glDetachShader (fragment)"); // <-- ADDED LOG
			}

			GL20.glDeleteProgram(programId);
			Debug.checkGLError("cleanup: glDeleteProgram"); // <-- ADDED LOG
		}
		if(vertexShaderId != 0)
		{
			GL20.glDeleteShader(vertexShaderId);
			Debug.checkGLError("cleanup: glDeleteShader (vertex)"); // <-- ADDED LOG
		}
		if(fragmentShaderId != 0)
		{
			GL20.glDeleteShader(fragmentShaderId);
			Debug.checkGLError("cleanup: glDeleteShader (fragment)"); // <-- ADDED LOG
		}
	}
}