package com.romanianguy.klover.ui.common

import kotlinx.coroutines.CancellationException

sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Ready<T>(val value: T) : Loadable<T>
    data class Failed(val error: Throwable) : Loadable<Nothing>
}

inline fun <T> Loadable<T>.valueOrNull(): T? = (this as? Loadable.Ready<T>)?.value

suspend inline fun <T> loadable(block: () -> T): Loadable<T> = try {
    Loadable.Ready(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Loadable.Failed(e)
}
