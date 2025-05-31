package com.juanpa.game.components; // CHANGED PACKAGE

import org.joml.Vector2d;

import com.juanpa.engine.components.Component;

public class PlayerInput extends Component // EXTENDS COMPONENT
{
    // Movement flags
    public boolean moveForward;
    public boolean moveBackward;
    public boolean moveLeft;
    public boolean moveRight;
    public boolean moveUp;
    public boolean moveDown;

    // Mouse Look data
    public Vector2d mouseLookDelta = new Vector2d();

    // Mouse button actions
    public boolean leftClick;
    public boolean rightClick;

    public PlayerInput() {
        reset();
    }

    public void reset() {
        moveForward = false;
        moveBackward = false;
        moveLeft = false;
        moveRight = false;
        moveUp = false;
        moveDown = false;
        mouseLookDelta.zero();
        leftClick = false;
        rightClick = false;
    }

    // This component won't have update logic here anymore,
    // it's just the data. A separate PlayerController component will use it.
    // Or, we can rename this to PlayerController and put logic here.
    // For simplicity, let's keep PlayerInput as data, and make a new PlayerController.
}