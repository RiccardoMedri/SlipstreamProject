package com.cesenahome.domain.usecases.playback

import com.cesenahome.domain.models.song.Song
import com.cesenahome.domain.repository.SongRepository
import kotlin.Result

interface GetRandomSongUseCase {
    suspend operator fun invoke(): Result<Song?>
}

class GetRandomSongUseCaseImpl(
    private val songRepository: SongRepository
) : GetRandomSongUseCase {
    override suspend fun invoke(): Result<Song?> {
        return songRepository.getRandomSong()
    }
}