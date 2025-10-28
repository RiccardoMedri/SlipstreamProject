package com.cesenahome.domain.usecases.favourites

import com.cesenahome.domain.repository.PlaylistRepository
import com.cesenahome.domain.repository.SongRepository

interface AddSongToFavouritesUseCase {
    suspend operator fun invoke(songId: String, makeFavourite: Boolean): Result<Unit>
}

class AddSongToFavouritesUseCaseImpl(
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
) : AddSongToFavouritesUseCase {
    override suspend fun invoke(songId: String, makeFavourite: Boolean): Result<Unit> {
        val playlistResult = playlistRepository.ensureFavouritePlaylistId()
        playlistResult.exceptionOrNull()?.let { return Result.failure(it) }
        val playlistId = playlistResult.getOrNull()
            ?: return Result.failure(IllegalStateException("Unable to resolve favourites playlist identifier"))

        return if (makeFavourite) {
            songRepository.addSongToFavourites(songId, true, playlistId)
        } else {
            songRepository.removeSongFromFavourites(songId, false, playlistId)
        }
    }
}