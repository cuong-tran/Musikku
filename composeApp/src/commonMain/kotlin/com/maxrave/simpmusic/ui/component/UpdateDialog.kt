package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxrave.simpmusic.Platform
import com.maxrave.simpmusic.expect.openInstallPermissionSettings
import com.maxrave.simpmusic.expect.openUrl
import com.maxrave.simpmusic.getPlatform
import com.maxrave.simpmusic.ui.theme.fontFamily
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.utils.update.AppRelease
import com.maxrave.simpmusic.utils.update.AppUpdateState
import com.maxrave.simpmusic.utils.update.AppUpdater
import com.maxrave.simpmusic.utils.update.MUSIKKU_RELEASES_PAGE
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.cancel
import simpmusic.composeapp.generated.resources.download
import simpmusic.composeapp.generated.resources.retry
import simpmusic.composeapp.generated.resources.unknown
import simpmusic.composeapp.generated.resources.update_available
import simpmusic.composeapp.generated.resources.update_close
import simpmusic.composeapp.generated.resources.update_downloading
import simpmusic.composeapp.generated.resources.update_failed
import simpmusic.composeapp.generated.resources.update_install
import simpmusic.composeapp.generated.resources.update_installing
import simpmusic.composeapp.generated.resources.update_installing_android
import simpmusic.composeapp.generated.resources.update_message
import simpmusic.composeapp.generated.resources.update_open_releases
import simpmusic.composeapp.generated.resources.update_open_settings
import simpmusic.composeapp.generated.resources.update_permission_intro
import simpmusic.composeapp.generated.resources.update_permission_step_1
import simpmusic.composeapp.generated.resources.update_permission_step_2
import simpmusic.composeapp.generated.resources.update_permission_step_3
import simpmusic.composeapp.generated.resources.update_restart_note
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime

/**
 * The update offer for a Musikku [release]. With an asset for this build it downloads and installs in
 * place, on this same panel; without one, Download opens the releases page as it always did.
 */
@OptIn(ExperimentalTime::class)
@Composable
fun UpdateDialog(
    release: AppRelease,
    onDismiss: () -> Unit,
) {
    val state by AppUpdater.state.collectAsStateWithLifecycle()
    val isAndroid = getPlatform() == Platform.Android

    if (state is AppUpdateState.NeedsPermission) {
        // Back from the system screen: install if the permission is now there.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            AppUpdater.continueAfterPermission()
        }
    }

    val dismiss = {
        AppUpdater.reset()
        onDismiss()
    }

    AlertDialog(
        properties =
            DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        onDismissRequest = {
            if (state !is AppUpdateState.Downloading) dismiss()
        },
        confirmButton = {
            when (state) {
                AppUpdateState.Idle -> {
                    DialogButton(stringResource(Res.string.download)) {
                        if (release.asset == null) {
                            onDismiss()
                            openUrl(MUSIKKU_RELEASES_PAGE)
                        } else {
                            AppUpdater.start(release)
                        }
                    }
                }

                is AppUpdateState.Downloading -> {}

                is AppUpdateState.NeedsPermission -> {
                    DialogButton(stringResource(Res.string.update_open_settings)) { openInstallPermissionSettings() }
                }

                is AppUpdateState.Installing -> {
                    if (isAndroid) {
                        DialogButton(stringResource(Res.string.update_install)) { AppUpdater.installAgain() }
                    }
                }

                is AppUpdateState.Failed -> {
                    Row {
                        DialogButton(stringResource(Res.string.update_open_releases)) { openUrl(MUSIKKU_RELEASES_PAGE) }
                        if (release.asset != null) {
                            DialogButton(stringResource(Res.string.retry)) { AppUpdater.start(release) }
                        }
                    }
                }
            }
        },
        dismissButton = {
            when (state) {
                is AppUpdateState.Downloading -> DialogButton(stringResource(Res.string.cancel)) { AppUpdater.cancel() }
                is AppUpdateState.Installing -> DialogButton(stringResource(Res.string.update_close)) { dismiss() }
                else -> DialogButton(stringResource(Res.string.cancel)) { dismiss() }
            }
        },
        title = {
            Text(
                stringResource(Res.string.update_available),
                style = typo().labelSmall,
            )
        },
        text = {
            Column(
                Modifier
                    .heightIn(
                        max = 400.dp,
                    ).verticalScroll(
                        rememberScrollState(),
                    ),
            ) {
                Text(
                    text = releaseMessage(release),
                    style = typo().labelMedium,
                    modifier =
                        Modifier.padding(
                            vertical = 8.dp,
                        ),
                )
                when (val current = state) {
                    AppUpdateState.Idle -> {}

                    is AppUpdateState.Downloading -> {
                        if (current.total > 0) {
                            LinearProgressIndicator(
                                progress = { (current.downloaded.toFloat() / current.total).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        StatusText(stringResource(Res.string.update_downloading, downloadProgressText(current)))
                    }

                    is AppUpdateState.NeedsPermission -> {
                        StatusText(stringResource(Res.string.update_permission_intro))
                        StatusText(stringResource(Res.string.update_permission_step_1))
                        StatusText(stringResource(Res.string.update_permission_step_2))
                        StatusText(stringResource(Res.string.update_permission_step_3))
                    }

                    is AppUpdateState.Installing -> {
                        StatusText(stringResource(Res.string.update_installing))
                        StatusText(
                            stringResource(
                                if (isAndroid) Res.string.update_installing_android else Res.string.update_restart_note,
                            ),
                        )
                    }

                    is AppUpdateState.Failed -> {
                        StatusText(stringResource(Res.string.update_failed), error = true)
                        StatusText(current.message, error = true)
                    }
                }
                Markdown(
                    release.body,
                    typography =
                        markdownTypography(
                            h1 = typo().labelLarge,
                            h2 = typo().labelMedium,
                            h3 = typo().labelSmall,
                            text = typo().bodySmall,
                            bullet = typo().bodySmall,
                            paragraph = typo().bodySmall,
                            textLink =
                                TextLinkStyles(
                                    SpanStyle(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Normal,
                                        fontFamily = fontFamily(),
                                        textDecoration = TextDecoration.Underline,
                                    ),
                                ),
                        ),
                )
            }
        },
    )
}

@Composable
private fun DialogButton(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        // typo() styles carry their own colour, which would override the button's content colour.
        Text(
            text,
            style = typo().bodySmall,
            color = LocalContentColor.current,
        )
    }
}

@Composable
private fun StatusText(
    text: String,
    error: Boolean = false,
) {
    Text(
        text = text,
        style = typo().bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else Color.Unspecified,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@OptIn(ExperimentalTime::class)
@Composable
private fun releaseMessage(release: AppRelease): String {
    val formatted =
        release.releaseTime?.let { input ->
            try {
                val instant = kotlin.time.Instant.parse(input)
                val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                dateTime.format(
                    LocalDateTime.Format {
                        day()
                        char(' ')
                        monthName(MonthNames.ENGLISH_ABBREVIATED)
                        char(' ')
                        year()
                        char(' ')
                        hour()
                        char(':')
                        minute()
                        char(':')
                        second()
                    },
                )
            } catch (e: Exception) {
                stringResource(Res.string.unknown)
            }
        } ?: stringResource(Res.string.unknown)
    return runBlocking {
        getString(
            Res.string.update_message,
            release.tagName,
            formatted,
            // values-iw/values-in still carry an old %3$s; Compose Resources indexes
            // args without a bounds check, so omitting it crashes the dialog
            "",
        )
    }
}

/** "12.3 MB / 45.6 MB (27%)", or just what has arrived when the size is unknown. */
private fun downloadProgressText(state: AppUpdateState.Downloading): String {
    val done = megabytes(state.downloaded)
    if (state.total <= 0) return done
    val percent = (state.downloaded * 100.0 / state.total).roundToInt().coerceIn(0, 100)
    return "$done / ${megabytes(state.total)} ($percent%)"
}

private fun megabytes(bytes: Long): String {
    val tenths = (bytes / 1024.0 / 1024.0 * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10} MB"
}
