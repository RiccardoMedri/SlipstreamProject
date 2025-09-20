package com.cesenahome.domain.di

import com.cesenahome.domain.repository.LoginRepository

interface RepositoryProvider {
    val loginRepository: LoginRepository
}