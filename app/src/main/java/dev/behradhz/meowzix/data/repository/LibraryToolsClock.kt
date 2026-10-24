package dev.behradhz.meowzix.data.repository

import javax.inject.Inject
import javax.inject.Singleton

/** Injectable clock for metadata merge/unmerge timestamps. */
@Singleton
open class LibraryToolsClock @Inject constructor() {
    open fun millis(): Long = System.currentTimeMillis()
}
