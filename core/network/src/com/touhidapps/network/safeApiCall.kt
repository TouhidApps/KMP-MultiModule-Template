package com.touhidapps.network

import io.ktor.util.network.UnresolvedAddressException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

suspend inline fun <T> safeApiCall(
    crossinline apiCall: suspend () -> T
): ResultNet<T, NetworkError> {
    return try {
        ResultNet.Success(apiCall())
    } catch (e: UnresolvedAddressException) {
        ResultNet.Error(
            NetworkError.NoInternet(
                message = "No Internet Connection",
                log = e.message ?: "-"
            )
        )
    } catch (e: SerializationException) {
        ResultNet.Error(
            NetworkError.Serialization(
                message = "Serialization Error",
                log = e.message ?: "-"
            )
        )
    }  catch (e: IOException) {
        ResultNet.Error(
            NetworkError.NoInternet(
                message = "No internet",
                log = e.message ?: "-"
            )
        )
    } catch (e: Exception) {
        ResultNet.Error(
            NetworkError.Unknown(
                message = e.message ?: "Unknown error",
                log = e.stackTraceToString()
            )
        )
    }
}
