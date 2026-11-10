package com.khojoai.app

/**
 * `ota` flavor: updates come from our private AWS bucket (Sprint 2: AppUpdater + UpdateManager live in this source set).
 * Only this flavor's manifest requests REQUEST_INSTALL_PACKAGES, so the Play build contains no installer code.
 */
object UpdateChannel {
    const val selfUpdates: Boolean = true
}
