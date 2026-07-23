package com.wavora.appdata.repository

import com.wavora.appdata.db.datasource.LocalDataSource
import com.wavora.appdata.mapping.toDomainAccountInfo
import com.wavora.domain.model.entities.GoogleAccountEntity
import com.wavora.domain.model.model.account.AccountInfo
import com.wavora.domain.repository.AccountRepository
import com.wavora.scraper.YouTube
import com.wavora.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

private const val TAG = "AccountRepositoryImpl"

internal class AccountRepositoryImpl(
    private val localDataSource: LocalDataSource,
    private val youTube: YouTube,
) : AccountRepository {
    override fun getYouTubeCookie() = youTube.cookie

    override fun getAccountInfo(cookie: String): Flow<List<AccountInfo>> =
        flow {
            youTube.cookie = cookie
            delay(1000)
            youTube
                .getAccountListWithPageId(cookie)
                .onSuccess {
                    // DIAGNOSTICO (auditoria persistencia de sesion Desktop): si esto
                    // imprime "count=0" sin excepcion, la request llego a Google y
                    // volvio OK pero sin cuentas (coherente con request sin
                    // Authorization -> tratada como no autenticada). Si tira excepcion,
                    // el detalle esta en el log "getAccountInfo: <mensaje>" de abajo.
                    Logger.d(TAG, "DIAG getAccountInfo: onSuccess count=${it.size}")
                    emit(it.map { account -> account.toDomainAccountInfo() })
                }.onFailure {
                    Logger.e(TAG, "getAccountInfo: ${it.message}", it)
                    emit(emptyList())
                }
        }.flowOn(Dispatchers.IO)

    override fun insertGoogleAccount(googleAccountEntity: GoogleAccountEntity) =
        flow {
            emit(localDataSource.insertGoogleAccount(googleAccountEntity))
        }.flowOn(Dispatchers.IO)

    override fun getGoogleAccounts(): Flow<List<GoogleAccountEntity>?> =
        flow<List<GoogleAccountEntity>?> { emit(localDataSource.getGoogleAccounts()) }.flowOn(
            Dispatchers.IO,
        )

    override fun getUsedGoogleAccount(): Flow<GoogleAccountEntity?> =
        flow<GoogleAccountEntity?> { emit(localDataSource.getUsedGoogleAccount()) }.flowOn(
            Dispatchers.IO,
        )

    override suspend fun deleteGoogleAccount(email: String) =
        withContext(Dispatchers.IO) {
            localDataSource.deleteGoogleAccount(email)
        }

    override fun updateGoogleAccountUsed(
        email: String,
        isUsed: Boolean,
    ): Flow<Int> = flow { emit(localDataSource.updateGoogleAccountUsed(email, isUsed)) }.flowOn(Dispatchers.IO)
}