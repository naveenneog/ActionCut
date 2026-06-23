package com.actioncut.core.media.di

import com.actioncut.core.domain.port.CaptionGenerator
import com.actioncut.core.domain.port.VideoExporter
import com.actioncut.core.media.caption.StubCaptionGenerator
import com.actioncut.core.media.export.Media3VideoExporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds media-layer ports to their default implementations. Swap [Media3VideoExporter]
 * for `FFmpegVideoEngine` here to change the export backend without touching features.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MediaModule {

    @Binds
    @Singleton
    abstract fun bindVideoExporter(impl: Media3VideoExporter): VideoExporter

    @Binds
    @Singleton
    abstract fun bindCaptionGenerator(impl: StubCaptionGenerator): CaptionGenerator
}
