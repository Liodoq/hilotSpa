package com.hilotspa.backend.entities;

/**
 * Which figure a pain marker was placed on. An x/y coordinate is ambiguous
 * without it — the same point means two different places front and back.
 */
public enum BodyView {
    FRONT, BACK
}
