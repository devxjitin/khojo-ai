package com.khojoai.app

/**
 * `play` flavor: Google Play delivers updates (In-App Updates API) and billing (Play Billing).
 * It must never download or install APKs itself.
 */
object UpdateChannel {
    const val selfUpdates: Boolean = false
}
