package com.cesenahome.domain.di

import com.cesenahome.domain.repository.AlbumRepository
import com.cesenahome.domain.repository.ArtistRepository
import com.cesenahome.domain.repository.HomepageRepository
import com.cesenahome.domain.repository.LoginRepository
import com.cesenahome.domain.repository.SongRepository
import com.cesenahome.domain.repository.PlayerRepository
import com.cesenahome.domain.repository.PlaylistRepository
import com.cesenahome.domain.repository.DownloadRepository

interface RepositoryProvider {
    val loginRepository: LoginRepository
    val homeRepository: HomepageRepository
    val songRepository: SongRepository
    val playerRepository: PlayerRepository
    val albumRepository: AlbumRepository
    val artistRepository: ArtistRepository
    val playlistRepository: PlaylistRepository
    val downloadRepository: DownloadRepository
}