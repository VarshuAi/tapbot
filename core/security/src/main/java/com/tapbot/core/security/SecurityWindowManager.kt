package com.tapbot.core.security

import android.app.Activity
import android.view.Window
import android.view.WindowManager

/**
 * Manages window security flags to prevent secrets from appearing in:
 * - App-generated or user-triggered screenshots
 * - Android OS Recent Apps / Task Switcher snapshot thumbnails
 * - Third-party screen recording or screen mirroring tools
 *
 * Employs [WindowManager.LayoutParams.FLAG_SECURE] to enforce hardware-backed display protection.
 */
object SecurityWindowManager {

    /**
     * Enables or disables [WindowManager.LayoutParams.FLAG_SECURE] on an Activity's window.
     */
    fun setSecureFlag(activity: Activity?, secure: Boolean) {
        setWindowSecure(activity?.window, secure)
    }

    /**
     * Enables or disables [WindowManager.LayoutParams.FLAG_SECURE] on a specific Window.
     */
    fun setWindowSecure(window: Window?, secure: Boolean) {
        window?.let {
            if (secure) {
                it.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            } else {
                it.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    /**
     * Checks if [WindowManager.LayoutParams.FLAG_SECURE] is currently enabled on the Window.
     */
    fun isWindowSecure(window: Window?): Boolean {
        return window?.let {
            (it.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        } ?: false
    }
}
