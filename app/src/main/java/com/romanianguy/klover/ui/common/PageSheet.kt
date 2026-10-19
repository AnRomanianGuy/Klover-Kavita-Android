package com.romanianguy.klover.ui.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// a bottom sheet that opens on the right page in book posture, so it's never across the fold
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val hinge = rememberBookHinge()
    if (hinge == null) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            content = content,
        )
        return
    }
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    // The right page starts where the fold ends; the sheet is centred in the window, so it is moved by half of that
    // distance.
    val rightStart = hinge.right
    val rightWidth: Dp = with(density) { (window.width - rightStart).coerceAtLeast(0f).toDp() }
    val shift: Dp = with(density) { (rightStart / 2f).toDp() }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.offset(x = shift),
        sheetState = sheetState,
        sheetMaxWidth = rightWidth.coerceAtLeast(320.dp),
        content = content,
    )
}
