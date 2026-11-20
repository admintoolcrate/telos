package cc.toolcrate.telos.di

import cc.toolcrate.telos.ChangeHook
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
abstract class HooksModule {
    /** Allows the set to be empty until a feature contributes a hook. */
    @Multibinds
    abstract fun changeHooks(): Set<ChangeHook>
}
