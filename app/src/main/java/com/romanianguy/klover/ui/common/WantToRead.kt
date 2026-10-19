package com.romanianguy.klover.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.romanianguy.klover.data.repo.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Immutable
class WantToReadControl(
    // `null` when the server could not be asked (the option then stays disabled)
    val load: suspend () -> Boolean?,
    val set: (add: Boolean) -> Unit,
)

// runs on the app scope so changes finish even if the screen is left; onChanged lets the caller refresh
@Composable
fun rememberWantToReadControls(
    onChanged: (seriesId: Int, added: Boolean) -> Unit = { _, _ -> },
): (seriesId: Int) -> WantToReadControl {
    val repo = koinInject<LibraryRepository>()
    val scope = koinInject<CoroutineScope>()
    val callback = rememberUpdatedState(onChanged)
    return remember(repo, scope) {
        { seriesId ->
            WantToReadControl(
                load = { runCatching { repo.isWantToRead(seriesId) }.getOrNull() },
                set = { add ->
                    scope.launch {
                        if (runCatching { repo.setWantToRead(seriesId, add) }.isSuccess) {
                            callback.value(seriesId, add)
                        }
                    }
                },
            )
        }
    }
}
