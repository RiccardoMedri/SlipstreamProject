package com.cesenahome.domain.di

import com.cesenahome.domain.repository.HomepageRepository
import com.cesenahome.domain.repository.LoginRepository
import com.cesenahome.domain.repository.SongsRepository

interface RepositoryProvider {
    val loginRepository: LoginRepository
    val homeRepository: HomepageRepository
    val songsRepository: SongsRepository
}