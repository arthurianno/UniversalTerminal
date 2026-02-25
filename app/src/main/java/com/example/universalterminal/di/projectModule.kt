package com.example.universalterminal.di

import android.content.Context
import com.example.universalterminal.data.BLE.BleDeviceManager
import com.example.universalterminal.data.managers.BleRepositoryImpl
import com.example.universalterminal.data.managers.BleScanner
import com.example.universalterminal.data.managers.BleScannerWrapper
import com.example.universalterminal.data.managers.DeviceWorkingRepositoryImpl
import com.example.universalterminal.domain.repository.BleRepository
import com.example.universalterminal.domain.repository.DeviceWorkingRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object projectModule {

    @Provides
    @Singleton
    fun provideBleScannerWrapper(@ApplicationContext context: Context): BleScannerWrapper {
        return BleScannerWrapper(context)
    }

    @Provides
    @Singleton
    fun provideBleScanner(
        bleScannerWrapper: BleScannerWrapper,
        @ApplicationContext context: Context
    ): BleScanner {
        return BleScanner(bleScannerWrapper, context)
    }

    @Provides
    @Singleton
    fun provideBleRepository(bleScanner: BleScanner, bleDeviceManager: BleDeviceManager,@ApplicationContext context: Context): BleRepository {
        return BleRepositoryImpl(bleScanner,bleDeviceManager,context)
    }

    @Provides
    @Singleton
    fun provideDeviceWorkingRepository(@ApplicationContext context: Context): DeviceWorkingRepository {
        return DeviceWorkingRepositoryImpl(context)
    }

    @Provides
    @Singleton
    fun provideBleDeviceManager(@ApplicationContext context: Context): BleDeviceManager {
        return BleDeviceManager(context)
    }

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context {
        return context
    }








}
