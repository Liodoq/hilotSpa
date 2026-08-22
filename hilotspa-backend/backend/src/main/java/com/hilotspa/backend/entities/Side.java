package com.hilotspa.backend.entities;

/**
 * The client's own left or right — which is what the L and R columns on the
 * paper form's findings table record.
 *
 * Note this is the CLIENT's side, not the viewer's. On the back view the figure
 * is seen from behind, so their left appears on the right of the screen; the
 * Angular body map resolves that before sending.
 *
 * CENTRE is for midline regions such as Lumbar and Cervical, which have no side.
 */
public enum Side {
    LEFT, RIGHT, CENTRE
}
