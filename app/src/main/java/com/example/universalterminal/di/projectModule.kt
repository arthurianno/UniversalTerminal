package com.example.universalterminal.di

import com.example.universalterminal.data.managers.BleRepositoryImpl
import com.example.universalterminal.data.managers.BleScanner
import com.example.universalterminal.data.managers.BleScannerWrapper
import com.example.universalterminal.domain.repository.BleRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object projectModule {

    @Provides
    @Singleton
    fun provideBleScannerWrapper(): BleScannerWrapper {
        return BleScannerWrapper()
    }

    @Provides
    @Singleton
    fun provideCoroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)

    }

    @Provides
    @Singleton
    fun provideBleScanner(bleScannerWrapper: BleScannerWrapper, coroutineScope: CoroutineScope): BleScanner {
        return BleScanner(bleScannerWrapper, coroutineScope)
    }

    @Provides
    @Singleton
    fun provideBleRepository(bleScanner: BleScanner): BleRepository {
        return BleRepositoryImpl(bleScanner)
    }





}