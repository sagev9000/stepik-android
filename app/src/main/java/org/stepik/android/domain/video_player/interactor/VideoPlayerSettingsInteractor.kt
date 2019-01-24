package org.stepik.android.domain.video_player.interactor

import io.reactivex.Single
import io.reactivex.rxkotlin.Singles.zip
import org.stepic.droid.preferences.UserPreferences
import org.stepic.droid.preferences.VideoPlaybackRate
import org.stepik.android.domain.video_player.repository.VideoTimestampRepository
import org.stepik.android.model.VideoUrl
import org.stepik.android.view.video_player.model.VideoPlayerData
import org.stepik.android.view.video_player.model.VideoPlayerMediaData
import javax.inject.Inject

class VideoPlayerSettingsInteractor
@Inject
constructor(
    private val userPreferences: UserPreferences,
    private val videoTimestampRepository: VideoTimestampRepository
) {
    fun getVideoPlayerData(videoPlayerMediaData: VideoPlayerMediaData): Single<VideoPlayerData> =
        zip(
            getVideoUrl(videoPlayerMediaData.cachedVideo?.urls ?: videoPlayerMediaData.externalVideo?.urls ?: emptyList()),
            getPlaybackRate(),
            getVideoTimestamp(videoPlayerMediaData.externalVideo?.id ?: videoPlayerMediaData.cachedVideo?.id ?: -1)
        ).map { (videoUrl, playbackRate, timestamp) ->
            VideoPlayerData(
                videoId = videoPlayerMediaData.externalVideo?.id ?: videoPlayerMediaData.cachedVideo?.id ?: -1,
                videoUrl = videoUrl,
                videoPlaybackRate = playbackRate,
                videoTimestamp = timestamp,
                mediaData = videoPlayerMediaData
            )
        }

    fun isRotateVideo(): Single<Boolean> =
        Single.fromCallable(userPreferences::isRotateVideo)

    private fun getVideoUrl(videoUrls: List<VideoUrl>): Single<String> =
        Single
            .fromCallable(userPreferences::getQualityVideoForPlaying)
            .map { quality ->
                val qualityInt = quality.toInt()
                videoUrls
                    .minBy { url ->
                        val urlQualityInt = Integer.parseInt(url.quality!!)
                        Math.abs(qualityInt - urlQualityInt)
                    }
                    ?.url!!
            }
            .onErrorReturnItem(videoUrls.lastOrNull()?.url ?: "")

    private fun getPlaybackRate(): Single<VideoPlaybackRate> =
        Single.fromCallable(userPreferences::getVideoPlaybackRate)

    private fun getVideoTimestamp(videoId: Long): Single<Long> =
        videoTimestampRepository.getVideoTimestamp(videoId)
}