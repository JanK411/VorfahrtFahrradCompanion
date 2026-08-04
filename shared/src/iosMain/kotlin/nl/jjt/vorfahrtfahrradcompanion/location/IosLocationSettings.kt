package nl.jjt.vorfahrtfahrradcompanion.location

import kotlinx.coroutines.flow.Flow

class IosLocationSettings : LocationSettings {
    override val isEnabled: Flow<Boolean> get() = TODO("iOS not implemented")
    override fun open(): Unit = TODO("iOS not implemented")
}
