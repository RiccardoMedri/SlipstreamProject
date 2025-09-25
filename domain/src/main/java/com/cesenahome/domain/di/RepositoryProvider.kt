package com.cesenahome.domain.di

import com.cesenahome.domain.repository.HomepageRepository
import com.cesenahome.domain.repository.LoginRepository
import com.cesenahome.domain.repository.SongsRepository
import com.cesenahome.domain.repository.PlayerRepository

interface RepositoryProvider {
    val loginRepository: LoginRepository
    val homeRepository: HomepageRepository
    val songsRepository: SongsRepository
    val playerRepository: PlayerRepository
}