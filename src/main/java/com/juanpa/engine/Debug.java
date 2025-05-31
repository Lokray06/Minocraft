package com.juanpa.engine;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public class Debug
{
	static LOG_LEVEL logLevel = LOG_LEVEL.ALL;

	public static void log(Object msg)
	{
		if(logLevel == LOG_LEVEL.NONE) return;
		if(logLevel.ordinal() >= LOG_LEVEL.ALL.ordinal())
		{
			System.out.println("[LOG]: " + msg);
		}
	}

	public static void logInfo(Object msg)
	{
		if(logLevel == LOG_LEVEL.NONE) return;
		if(logLevel.ordinal() >= LOG_LEVEL.INFO.ordinal())
		{
			System.out.println(BLUE + "[INF]: " + msg + RESET);
		}
	}

	public static void logWarning(Object msg)
	{
		if(logLevel == LOG_LEVEL.NONE) return;
		if(logLevel.ordinal() >= LOG_LEVEL.WARNING.ordinal())
		{
			System.out.println(YELLOW + "[WAR]: " + msg + RESET);
		}
	}

	public static void logError(Object msg)
	{
		if(logLevel == LOG_LEVEL.NONE) return;
		if(logLevel.ordinal() >= LOG_LEVEL.ERROR.ordinal())
		{
			System.out.println(RED_BOLD + "[ERR]: " + msg + RESET);
		}
	}

	/**
	 * Checks for OpenGL errors and logs them.
	 * Call this method after any OpenGL operation that might cause an error.
	 *
	 * @param location A string describing where the check is being performed (e.g., "Shader compilation", "VAO binding").
	 */
	public static void checkGLError(String location)
	{
		int error = GL11.glGetError(); // Get the current OpenGL error code
		if(error != GL11.GL_NO_ERROR)
		{
			// Convert the error code to a more readable string if possible
			String errorString;
			switch(error)
			{
				case GL11.GL_INVALID_ENUM:
					errorString = "GL_INVALID_ENUM";
					break;
				case GL11.GL_INVALID_VALUE:
					errorString = "GL_INVALID_VALUE";
					break;
				case GL11.GL_INVALID_OPERATION:
					errorString = "GL_INVALID_OPERATION";
					break;
				case GL11.GL_STACK_OVERFLOW:
					errorString = "GL_STACK_OVERFLOW";
					break;
				case GL11.GL_STACK_UNDERFLOW:
					errorString = "GL_STACK_UNDERFLOW";
					break;
				case GL11.GL_OUT_OF_MEMORY:
					errorString = "GL_OUT_OF_MEMORY";
					break;
				case GL30.GL_INVALID_FRAMEBUFFER_OPERATION: // For modern OpenGL
					errorString = "GL_INVALID_FRAMEBUFFER_OPERATION";
					break;
				default:
					errorString = "UNKNOWN_ERROR (" + error + ")";
					break;
			}
			logError("OpenGL Error at " + location + ": " + errorString);
			// Optionally, you might want to throw a RuntimeException here in debug builds
			// to immediately stop the program if an error occurs, as silent errors are hard to trace.
			// throw new RuntimeException("OpenGL Error at " + location + ": " + errorString);
		}
	}

	public enum LOG_LEVEL
	{
		NONE, ERROR, WARNING, INFO, ALL
	}

	// ANSI escape codes for colors
	public static final String RESET = "\033[0m";
	public static final String BLACK = "\033[0;30m";   // Black
	public static final String RED = "\033[0;31m";     // Red
	public static final String GREEN = "\033[0;32m";   // Green
	public static final String YELLOW = "\033[0;33m";  // Yellow
	public static final String BLUE = "\033[0;34m";    // Blue
	public static final String PURPLE = "\033[0;35m";  // Purple
	public static final String CYAN = "\033[0;36m";    // Cyan
	public static final String WHITE = "\033[0;37m";   // White

	// You can also use bold colors for more emphasis
	public static final String RED_BOLD = "\033[1;31m";
	public static final String YELLOW_BOLD = "\033[1;33m";
	public static final String GREEN_BOLD = "\033[1;32m";

	public static void setLogLevel(LOG_LEVEL newLogLevel)
	{
		logLevel = newLogLevel;
	}
}
