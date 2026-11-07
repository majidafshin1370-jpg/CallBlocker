package com.majid.callblocker

import android.telecom.Call
import android.telecom.CallScreeningService

class BlockScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val builder = CallResponse.Builder()

        if (callDetails.callDirection == Call.Details.DIRECTION_INCOMING) {
            val number = callDetails.handle?.schemeSpecificPart
            if (number != null &&
                BlockStore.isEnabled(this) &&
                BlockStore.isBlocked(this, number)
            ) {
                builder
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipNotification(true)
            }
        }

        respondToCall(callDetails, builder.build())
    }
}
