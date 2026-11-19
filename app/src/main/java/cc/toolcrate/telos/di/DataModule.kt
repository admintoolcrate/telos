package cc.toolcrate.telos.di

import android.content.Context
import androidx.room.Room
import cc.toolcrate.telos.data.DeviceIdentity
import cc.toolcrate.telos.data.SystemTimeSource
import cc.toolcrate.telos.data.TelosDatabase
import cc.toolcrate.telos.data.TimeSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** A scope that outlives screens, for work that must finish (writes after a swipe, reschedules). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): TelosDatabase =
        Room.databaseBuilder(context, TelosDatabase::class.java, TelosDatabase.NAME)
            .addMigrations(*TelosDatabase.MIGRATIONS)
            .build()

    @Provides
    @Singleton
    fun time(): TimeSource = SystemTimeSource

    @Provides
    @Singleton
    fun device(@ApplicationContext context: Context, time: TimeSource) = DeviceIdentity(context, time)

    @Provides
    @Singleton
    @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
