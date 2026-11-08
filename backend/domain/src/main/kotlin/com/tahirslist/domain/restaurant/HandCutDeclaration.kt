package com.tahirslist.domain.restaurant

/**
 * A certifier's published position on hand-cutting.
 *
 * This is the certifier's own policy, not a property of its tier. The product
 * boolean is TRUE only for [HAND_CUT]; both [NOT_HAND_CUT] and [UNSPECIFIED]
 * yield FALSE. `NULL` is reserved for rows with no certifier claim at all.
 */
enum class HandCutDeclaration {
    HAND_CUT,
    NOT_HAND_CUT,
    UNSPECIFIED,
    ;

    val isHandCut: Boolean get() = this == HAND_CUT
}
