package com.touhidapps.flowers.data.datasource.remote.source

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.encodedPath

// datasource/remote/source/NoticeRemoteSource.kt
class FlowerListSource(
    private val client: HttpClient
) : com.touhidapps.flowers.data.datasource.remote.api.FlowerListApi {

    override suspend fun getFlowers(): com.touhidapps.flowers.data.datasource.remote.dto.FlowersDto {
        return client.get {
            url { encodedPath = com.touhidapps.flowers.data.datasource.remote.source.ApiUrls.FLOWER }
        }.body()
    }

}

