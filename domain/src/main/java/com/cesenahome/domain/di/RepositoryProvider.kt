package com.cesenahome.domain.di

import com.cesenahome.domain.repository.HomepageRepository
import com.cesenahome.domain.repository.LoginRepository

interface RepositoryProvider {
    val loginRepository: LoginRepository
    val homeRepository: HomepageRepository
}