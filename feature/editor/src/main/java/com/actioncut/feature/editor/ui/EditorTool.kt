package com.actioncut.feature.editor.ui

/**
 * Editing tools shown in the bottom toolbar. Some are instant actions (split, delete,
 * reverse, rotate); others open a parameter [opensPanel] panel (speed, volume, filters…).
 */
enum class EditorTool(val label: String, val opensPanel: Boolean) {
    SPLIT("Split", false),
    DELETE("Delete", false),
    TRIM("Trim", true),
    SPEED("Speed", true),
    VOLUME("Volume", true),
    MUTE("Mute", false),
    AUDIO("Audio", false),
    PIP("PiP", false),
    EXTRACT_AUDIO("Detach", false),
    REVERSE("Reverse", false),
    ROTATE("Rotate", false),
    FILTERS("Filters", true),
    ADJUST("Adjust", true),
    CROP("Crop", true),
    CANVAS("Canvas", true),
    TEXT("Text", true),
    STICKER("Sticker", true),
    TRANSITIONS("Transition", true),
    EFFECTS("Effects", true),
}
