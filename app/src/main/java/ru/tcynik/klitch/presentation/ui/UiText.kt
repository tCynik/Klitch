package ru.tcynik.klitch.presentation.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

sealed class UiText {
    data class Static(@StringRes val resId: Int) : UiText()
    data class Dynamic(@StringRes val resId: Int, val args: List<Any>) : UiText() {
        constructor(@StringRes resId: Int, vararg args: Any) : this(resId, args.toList())
    }
    data class Raw(val value: String) : UiText()

    @Composable
    fun resolve(): String = when (this) {
        is Static -> stringResource(resId)
        is Dynamic -> stringResource(resId, *args.toTypedArray())
        is Raw -> value
    }

    fun resolve(context: Context): String = when (this) {
        is Static -> context.getString(resId)
        is Dynamic -> context.getString(resId, *args.toTypedArray())
        is Raw -> value
    }
}
