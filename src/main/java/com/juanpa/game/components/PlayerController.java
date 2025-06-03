package com.juanpa.game.components;

import com.juanpa.engine.Debug;
import com.juanpa.engine.Time;
import com.juanpa.engine.components.Camera;
import com.juanpa.engine.components.Component;
import com.juanpa.engine.components.Transform;
import com.juanpa.engine.input.Input;
import com.juanpa.engine.input.KeyCode;
import com.juanpa.game.Game;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.lwjgl.glfw.GLFW;

public class PlayerController extends Component
{
	private final float MOVEMENT_SPEED = 5.0f;
	private final float SPRINT_SPEED_MULTIPLIER = 2.0f;
	private final float NORMAL_SPEED_MULTIPLIER = 1.0f;
	private final float CREATIVE_FLIGHT_SPEED = 20.0f; // Speed for creative flight

	// --- Gravity and Collision Variables ---
	private final float GRAVITY = -20.0f; // Adjust this value for stronger/weaker gravity
	private final float JUMP_POWER = 8.0f; // How high the player jumps
	private final float PLAYER_HEIGHT = 1.8f; // Approximate height of the player for collision detection
	private final float PLAYER_WIDTH = 0.6f; // Approximate width/depth of the player for collision detection

	// --- Raycasting Variables ---
	private final float REACH_DISTANCE = 1000.0f; // How far the player can reach blocks

	private Vector3f velocity = new Vector3f(0, 0, 0); // Current player velocity
	private boolean onGround = false; // To prevent multiple jumps

	// --- Creative Mode Variables ---
	private boolean creativeMode = false;
	private long lastSpacePressTime = 0;
	private final long DOUBLE_PRESS_TIME_THRESHOLD = 300; // Milliseconds for double-press detection

	public PlayerController()
	{
	}

	@Override
	public void onStart()
	{
		gameObject.getComponent(Camera.class).offset = new Vector3f(0, PLAYER_HEIGHT, 0);
	}

	@Override
	public void update()
	{
		Transform transform = gameObject.getTransform();
		if(transform == null)
		{
			Debug.logError("PlayerController component's GameObject '" + gameObject.getName() + "' has no Transform!");
			return;
		}

		// --- Toggle Creative Mode ---
		if(Input.getKeyDown(KeyCode.SPACE))
		{
			long currentTime = System.currentTimeMillis();
			if(currentTime - lastSpacePressTime <= DOUBLE_PRESS_TIME_THRESHOLD)
			{
				creativeMode = !creativeMode;
				if(creativeMode)
				{
					Debug.logInfo("Creative Mode ENABLED");
					velocity.y = 0; // Stop any current vertical movement immediately
				}
				else
				{
					Debug.logInfo("Creative Mode DISABLED");
				}
			}
			lastSpacePressTime = currentTime;
		}

		// --- Horizontal Movement Input (common logic, but speed depends on mode) ---
		Vector3f movementInput = new Vector3f(0, 0, 0);

		Vector3f forwardDirection = new Vector3f(0, 0, -1);
		transform.rotation.transform(forwardDirection);
		forwardDirection.y = 0; // Keep horizontal movement planar
		if(forwardDirection.lengthSquared() > 0)
		{
			forwardDirection.normalize();
		}

		Vector3f rightDirection = new Vector3f(1, 0, 0);
		transform.rotation.transform(rightDirection);
		rightDirection.y = 0; // Keep horizontal movement planar
		if(rightDirection.lengthSquared() > 0)
		{
			rightDirection.normalize();
		}

		if(Input.getKey(KeyCode.W))
		{
			movementInput.add(forwardDirection);
		}
		if(Input.getKey(KeyCode.S))
		{
			movementInput.sub(forwardDirection);
		}
		if(Input.getKey(KeyCode.D))
		{
			movementInput.add(rightDirection);
		}
		if(Input.getKey(KeyCode.A))
		{
			movementInput.sub(rightDirection);
		}

		// Determine the final movement speed based on mode and sprinting
		float baseSpeed = creativeMode ? CREATIVE_FLIGHT_SPEED : MOVEMENT_SPEED;
		float currentSpeedMultiplier = Input.getKey(KeyCode.LEFT_CONTROL) ? SPRINT_SPEED_MULTIPLIER : NORMAL_SPEED_MULTIPLIER;
		float finalMovementSpeed = baseSpeed * currentSpeedMultiplier;

		Vector3f horizontalMovement = new Vector3f(movementInput.x, 0, movementInput.z);
		if(horizontalMovement.lengthSquared() > 0.0001f)
		{
			horizontalMovement.normalize().mul(finalMovementSpeed);
		}
		velocity.x = horizontalMovement.x;
		velocity.z = horizontalMovement.z;


		if(creativeMode)
		{
			// --- Creative Mode Vertical Movement ---
			velocity.y = 0; // No gravity in creative mode

			if(Input.getKey(KeyCode.SPACE))
			{
				velocity.y = CREATIVE_FLIGHT_SPEED;
			}
			else if(Input.getKey(KeyCode.LEFT_SHIFT))
			{
				velocity.y = -CREATIVE_FLIGHT_SPEED;
			}
			else
			{
				velocity.y = 0; // Stop vertical movement if no key is pressed
			}

			// Apply total velocity (horizontal + vertical)
			transform.position.add(new Vector3f(velocity).mul(Time.deltaTime));

		}
		else
		{
			// --- Survival Mode (Original Logic) ---

			// --- Apply Gravity ---
			velocity.y += GRAVITY * Time.deltaTime;

			// --- Apply Velocity and Handle Collisions ---
			// Horizontal Collision (X and Z)
			float stepX = velocity.x * Time.deltaTime;
			float stepZ = velocity.z * Time.deltaTime;

			if(stepX != 0 || stepZ != 0)
			{
				// Check for X-axis collision
				if(checkCollision(transform.position.x + stepX, transform.position.y, transform.position.z))
				{
					velocity.x = 0; // Stop horizontal movement
				}
				else
				{
					transform.position.x += stepX;
				}

				// Check for Z-axis collision
				if(checkCollision(transform.position.x, transform.position.y, transform.position.z + stepZ))
				{
					velocity.z = 0; // Stop horizontal movement
				}
				else
				{
					transform.position.z += stepZ;
				}
			}

			// Vertical Collision (Y)
			float stepY = velocity.y * Time.deltaTime;
			// Assume player is not on ground at the start of each frame unless collision proves otherwise
			boolean currentlyCollidingBelow = false;

			if(stepY != 0)
			{
				if(checkCollision(transform.position.x, transform.position.y + stepY, transform.position.z))
				{
					// If moving down and hit ground
					if(velocity.y < 0)
					{
						// Snap to the block surface just above the player's feet
						transform.position.y = (float) Math.floor(transform.position.y + stepY) + 1.0f + 0.001f;
						currentlyCollidingBelow = true; // Player has hit ground
					}
					velocity.y = 0; // Stop vertical movement
				}
				else
				{
					transform.position.y += stepY;
					// If moving vertically and no collision, player is definitely not on ground
					currentlyCollidingBelow = false;
				}
			}
			else
			{ // stepY is 0, meaning no vertical velocity applied this frame
				// If not moving vertically, check if there's ground directly beneath the player's feet
				if(checkCollision(transform.position.x, transform.position.y - 0.01f, transform.position.z))
				{
					currentlyCollidingBelow = true;
				}
				else
				{
					currentlyCollidingBelow = false;
				}
			}

			// Update the onGround status once, at the end of vertical collision logic
			onGround = currentlyCollidingBelow;

			// --- Jumping ---
			if(Input.getKey(KeyCode.SPACE) && onGround)
			{ // Changed to getKeydown for single jump
				velocity.y = JUMP_POWER;
				onGround = false; // Player is now in the air
			}
		}

		// --- Block Interaction (Raycasting) (common to both modes) ---
		if(game.getWorld() != null)
		{
			// Fix: Ray origin should be where the camera is.
			// The camera offset is relative to the player's transform.position.
			Vector3f rayOrigin = new Vector3f(transform.position.x, transform.position.y + gameObject.getComponent(Camera.class).offset.y, transform.position.z);

			Vector3f rayDirection = new Vector3f(0, 0, -1);
			transform.rotation.transform(rayDirection); // Get the forward direction based on player rotation
			rayDirection.normalize();

			RaycastResult result = raycast(rayOrigin, rayDirection, REACH_DISTANCE);

			if(result != null)
			{
				if(Input.getMouseButton(GLFW.GLFW_MOUSE_BUTTON_LEFT))
				{
					// Break the hit block
					//Debug.logInfo("Breaking block at: " + result.hitBlock.x + ", " + result.hitBlock.y + ", " + result.hitBlock.z);
					game.getWorld().setBlock(result.hitBlock, com.juanpa.engine.world.World.BLOCK_TYPE_AIR_ID);
				}
				else if(Input.getMouseButton(GLFW.GLFW_MOUSE_BUTTON_RIGHT))
				{
					// Place a block on the hit face
					// This logic is already correct for placing adjacent to the hit block
					Vector3i placeBlockPos = new Vector3i(result.hitBlock).add(result.hitFaceNormal);
					//Debug.logInfo("Attempting to place block at: " + placeBlockPos.x + ", " + placeBlockPos.y + ", " + placeBlockPos.z);

					// Prevent placing block inside player
					if(!isPlayerOccupyingBlock(placeBlockPos))
					{
						game.getWorld().setBlock(placeBlockPos, com.juanpa.engine.world.World.BLOCK_TYPE_SOLID_ID);
						//Debug.logInfo("Block placed successfully!");
					}
					else
					{
						//Debug.logInfo("Cannot place block: space occupied by player.");
					}
				}
			}
		}
		else
		{
			//Debug.logWarning("Cannot perform block interaction: World instance not available.");
		}
	}

	/**
	 * Checks for collision at a given world position.
	 * This is a basic AABB (Axis-Aligned Bounding Box) collision check
	 * against individual blocks in the world.
	 * The player's bounding box starts at `y` (feet) and extends up to `y + PLAYER_HEIGHT`.
	 *
	 * @param x The target X position (player's center X).
	 * @param y The target Y position (player's feet Y).
	 * @param z The target Z position (player's center Z).
	 * @return True if a collision occurs, false otherwise.
	 */
	private boolean checkCollision(float x, float y, float z)
	{
		if(game.getWorld() == null)
		{
			return false;
		}

		// Define the player's bounding box in world coordinates
		float playerMinX = x - PLAYER_WIDTH / 2.0f;
		float playerMaxX = x + PLAYER_WIDTH / 2.0f;
		float playerMinY = y; // Player's feet
		float playerMaxY = y + PLAYER_HEIGHT; // Player's head
		float playerMinZ = z - PLAYER_WIDTH / 2.0f;
		float playerMaxZ = z + PLAYER_WIDTH / 2.0f;

		// Iterate through all blocks that the player's bounding box *could* overlap with
		for(int blockX = (int) Math.floor(playerMinX); blockX < (int) Math.ceil(playerMaxX); blockX++)
		{
			for(int blockY = (int) Math.floor(playerMinY); blockY < (int) Math.ceil(playerMaxY); blockY++)
			{
				for(int blockZ = (int) Math.floor(playerMinZ); blockZ < (int) Math.ceil(playerMaxZ); blockZ++)
				{

					short blockType = game.getWorld().getBlock(new Vector3i(blockX, blockY, blockZ));

					// If the block is solid (not air), check for collision
					if(blockType != com.juanpa.engine.world.World.BLOCK_TYPE_AIR_ID)
					{
						// Define the current block's bounding box (each block is 1x1x1)
						float blockMinX = (float) blockX;
						float blockMaxX = (float) blockX + 1.0f;
						float blockMinY = (float) blockY;
						float blockMaxY = (float) blockY + 1.0f;
						float blockMinZ = (float) blockZ;
						float blockMaxZ = (float) blockZ + 1.0f;

						// Check for overlap between player and block bounding boxes
						boolean overlapX = playerMaxX > blockMinX && playerMinX < blockMaxX;
						boolean overlapY = playerMaxY > blockMinY && playerMinY < blockMaxY;
						boolean overlapZ = playerMaxZ > blockMinZ && playerMinZ < blockMaxZ;

						if(overlapX && overlapY && overlapZ)
						{
							return true; // Collision detected
						}
					}
				}
			}
		}
		return false; // No collision
	}

	/**
	 * Checks if the player's current bounding box overlaps with a specific target block position.
	 * This is used to prevent placing blocks inside the player.
	 *
	 * @param blockPosition The integer coordinates of the block to check for overlap.
	 * @return True if the player is currently occupying the space of the given block, false otherwise.
	 */
	private boolean isPlayerOccupyingBlock(Vector3i blockPosition)
	{
		Transform transform = gameObject.getTransform();
		if(transform == null)
		{
			// Should not happen if PlayerController is attached to a GameObject with Transform
			return false;
		}

		// Player's current bounding box
		float playerMinX = transform.position.x - PLAYER_WIDTH / 2.0f;
		float playerMaxX = transform.position.x + PLAYER_WIDTH / 2.0f;
		float playerMinY = transform.position.y; // Player's feet Y
		float playerMaxY = transform.position.y + PLAYER_HEIGHT; // Player's head Y
		float playerMinZ = transform.position.z - PLAYER_WIDTH / 2.0f;
		float playerMaxZ = transform.position.z + PLAYER_WIDTH / 2.0f;

		// Proposed new block's bounding box (1x1x1 unit cube)
		float blockMinX = (float) blockPosition.x;
		float blockMaxX = (float) blockPosition.x + 1.0f;
		float blockMinY = (float) blockPosition.y;
		float blockMaxY = (float) blockPosition.y + 1.0f;
		float blockMinZ = (float) blockPosition.z;
		float blockMaxZ = (float) blockPosition.z + 1.0f;

		// Check for overlap between player's bounding box and the proposed new block's bounding box
		boolean overlapX = playerMaxX > blockMinX && playerMinX < blockMaxX;
		boolean overlapY = playerMaxY > blockMinY && playerMinY < blockMaxY;
		boolean overlapZ = playerMaxZ > blockMinZ && playerMinZ < blockMaxZ;

		return overlapX && overlapY && overlapZ;
	}


	/**
	 * Performs a raycast to find the first solid block hit by a ray.
	 * This is a simplified DDA (Digital Differential Analyzer) raycasting algorithm.
	 *
	 * @param origin      The starting point of the ray (e.g., player's eye position).
	 * @param direction   The normalized direction vector of the ray.
	 * @param maxDistance The maximum distance to cast the ray.
	 * @return A RaycastResult object if a block is hit, null otherwise.
	 */
	private RaycastResult raycast(Vector3f origin, Vector3f direction, float maxDistance)
	{
		if(game.getWorld() == null)
		{
			return null;
		}

		Vector3f currentRayPos = new Vector3f(origin);
		Vector3i lastBlockCoords = null; // Stores the integer coordinates of the block the ray was *just in*

		float stepIncrement = 0.05f; // Smaller step for more precision

		for(float dist = 0; dist < maxDistance; dist += stepIncrement)
		{
			// Calculate the position *before* the current step, which was still in the previous block
			Vector3f prevRayPos = new Vector3f(currentRayPos);
			currentRayPos.add(new Vector3f(direction).mul(stepIncrement));

			int blockX = (int) Math.floor(currentRayPos.x);
			int blockY = (int) Math.floor(currentRayPos.y);
			int blockZ = (int) Math.floor(currentRayPos.z);

			Vector3i currentBlockCoords = new Vector3i(blockX, blockY, blockZ);

			// If we just entered a new block (or for the very first block hit)
			if(lastBlockCoords == null || !currentBlockCoords.equals(lastBlockCoords))
			{
				short blockType = game.getWorld().getBlock(currentBlockCoords);

				if(blockType != com.juanpa.engine.world.World.BLOCK_TYPE_AIR_ID)
				{
					// Collision detected with a solid block!
					Vector3i hitBlock = currentBlockCoords;
					Vector3i hitFaceNormal = new Vector3i();

					// Determine the hit face normal.
					// This is done by comparing the current block (hitBlock) with the block
					// that the ray was in *just before* entering hitBlock.
					// The difference `hitBlock - lastBlockCoords` (or `currentBlockCoords - floor(prevRayPos)`)
					// gives the direction of traversal *into* the hit block.
					// The normal of the hit face is the *opposite* of this direction.

					// Get the block coordinates of the previous position of the ray.
					int prevBlockX = (int) Math.floor(prevRayPos.x);
					int prevBlockY = (int) Math.floor(prevRayPos.y);
					int prevBlockZ = (int) Math.floor(prevRayPos.z);
					Vector3i prevBlockCoords = new Vector3i(prevBlockX, prevBlockY, prevBlockZ);

					// If prevBlockCoords is the same as hitBlock (meaning we started inside or very close)
					// then we fall back to inferring from ray direction.
					if(prevBlockCoords.equals(hitBlock))
					{
						// Ray started inside the hit block. Infer normal from ray direction.
						// The normal should point AWAY from the ray's movement.
						if(Math.abs(direction.x) > Math.abs(direction.y) && Math.abs(direction.x) > Math.abs(direction.z))
						{
							hitFaceNormal.x = (direction.x > 0) ? -1 : 1;
						}
						else if(Math.abs(direction.y) > Math.abs(direction.x) && Math.abs(direction.y) > Math.abs(direction.z))
						{
							hitFaceNormal.y = (direction.y > 0) ? -1 : 1;
						}
						else
						{
							hitFaceNormal.z = (direction.z > 0) ? -1 : 1;
						}
					}
					else
					{
						// The ray moved from prevBlockCoords into hitBlock.
						// The normal of the hit face is the direction *from* the hitBlock *back towards* the prevBlockCoords.
						// Example: If ray moves from (0,0,0) to (1,0,0), it hit the -X face of (1,0,0).
						// The normal of the -X face is (-1,0,0).
						// (prevBlockCoords - hitBlock) gives us this.
						hitFaceNormal.x = prevBlockCoords.x - hitBlock.x;
						hitFaceNormal.y = prevBlockCoords.y - hitBlock.y;
						hitFaceNormal.z = prevBlockCoords.z - hitBlock.z;

						// Ensure it's a valid axis-aligned normal.
						// This handles cases where it might have jumped a corner, forcing it to one axis.
						if(hitFaceNormal.lengthSquared() > 1.001f)
						{
							if(Math.abs(hitFaceNormal.x) > Math.abs(hitFaceNormal.y) && Math.abs(hitFaceNormal.x) > Math.abs(hitFaceNormal.z))
							{
								hitFaceNormal.set(prevBlockCoords.x - hitBlock.x, 0, 0);
							}
							else if(Math.abs(hitFaceNormal.y) > Math.abs(hitFaceNormal.x) && Math.abs(hitFaceNormal.y) > Math.abs(hitFaceNormal.z))
							{
								hitFaceNormal.set(0, prevBlockCoords.y - hitBlock.y, 0);
							}
							else
							{
								hitFaceNormal.set(0, 0, prevBlockCoords.z - hitBlock.z);
							}
						}
					}

					return new RaycastResult(hitBlock, hitFaceNormal);
				}
			}
			lastBlockCoords = currentBlockCoords; // Update lastBlockCoords for the next iteration
		}
		return null; // No block hit within maxDistance
	}

	/**
	 * Helper class to store raycast results.
	 */
	private static class RaycastResult
	{
		public Vector3i hitBlock;
		public Vector3i hitFaceNormal; // Normal of the face that was hit

		public RaycastResult(Vector3i hitBlock, Vector3i hitFaceNormal)
		{
			this.hitBlock = hitBlock;
			this.hitFaceNormal = hitFaceNormal;
		}
	}
}