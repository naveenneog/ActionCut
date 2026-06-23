package com.actioncut.core.common.id

import java.util.UUID

/** Central place to mint stable identifiers for projects, tracks and clips. */
object Ids {
    fun project(): String = "prj_${short()}"
    fun track(): String = "trk_${short()}"
    fun clip(): String = "clp_${short()}"

    private fun short(): String = UUID.randomUUID().toString().substring(0, 8)
}
