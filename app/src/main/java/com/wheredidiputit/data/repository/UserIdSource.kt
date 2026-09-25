package com.wheredidiputit.data.repository

import com.wheredidiputit.domain.model.AuthState
import com.wheredidiputit.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal val AuthRepository.userIdFlow: Flow<String?>
    get() = authState.map { (it as? AuthState.SignedIn)?.userId }.distinctUntilChanged()

internal val AuthRepository.currentUserId: String?
    get() = (authState.value as? AuthState.SignedIn)?.userId
