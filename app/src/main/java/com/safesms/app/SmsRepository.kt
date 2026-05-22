package com.safesms.app

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

object SmsRepository {
    var isUiActive = false
    private val _incomingSmsChannel = Channel<String>(capacity = Channel.BUFFERED)
    val incomingSmsFlow = _incomingSmsChannel.receiveAsFlow()

    fun emitSms(message: String) {
        _incomingSmsChannel.trySend(message)
    }
}
