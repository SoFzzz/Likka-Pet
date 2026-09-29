package com.likkapet.domain.model

/** Where the overlay window goes and which pose it shows (documentación §9.6); the service applies it as is. */
data class OverlayMotion(
    // Top-left corner of the overlay window, in physical pixels
    val x: Int,
    val y: Int,
    val pose: MotionPose,
    val mode: MotionMode,
    // Level 1 only: the edge Likka peeks from or perches on; null while walking or dragged
    val edge: OverlayEdge?,
)

enum class MotionMode {
    // Level 1: peeking from a side edge or perched on the bottom one
    ON_EDGE,

    // Level 2
    WALKING,
    STOPPED,
    DRAGGED,
    WAITING_TO_RETURN,
}

/** The edges Level 1 may use; the top one is never used (design system §1.7). */
enum class OverlayEdge { LEFT, RIGHT, BOTTOM }

/** Poses chosen by the planner; [key] is the pose name in `likka_poses.json` (design system §1.7). */
enum class MotionPose(
    val key: String,
) {
    PEEK("peek"),
    PERCH("perch"),
    ANNOYED("annoyed"),
    DRAGGED("dragged"),
    WALK_RIGHT("walk_right"),
    WALK_LEFT("walk_left"),
    WALK_DOWN("walk_down"),
    WALK_UP("walk_up"),
    WALK_DIAG_DOWN_RIGHT("walk_diag_down_right"),
    WALK_DIAG_DOWN_LEFT("walk_diag_down_left"),
}
