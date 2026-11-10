package com.khojoai.app

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateChannelTest {
    @Test
    fun selfUpdateMatchesTheDistributionFlavor() {
        // Only the AWS OTA build may update itself; the Play build must leave updating to Google Play.
        assertEquals(BuildConfig.UPDATE_CHANNEL == "aws-ota", UpdateChannel.selfUpdates)
    }
}
