package org.mr0btain.ticketman.ui.screens.counter

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager

/**
 * Whether someone is using a keyboard rather than touching the screen, so the counter's F-key shortcuts mean
 * something to them. Desktop starts out on the keyboard; a touch screen switches over once a key is pressed
 */
@Composable
fun usingKeyboard(): Boolean = LocalInputModeManager.current.inputMode == InputMode.Keyboard

/** The key to put after a label, like "  (F3)", or nothing on a touch screen */
@Composable
fun keyHint(key: String): String = if (usingKeyboard()) "  ($key)" else ""
