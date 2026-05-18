package com.lastasylum.alliance.data.teams

import retrofit2.HttpException
import retrofit2.Response

/** Успех по HTTP-коду; тело может быть пустым или частичным (Moshi не валит всю операцию). */
internal fun <T> Response<T>.requireTeamsSuccess(): T? {
    if (!isSuccessful) {
        throw HttpException(this)
    }
    return body()
}
