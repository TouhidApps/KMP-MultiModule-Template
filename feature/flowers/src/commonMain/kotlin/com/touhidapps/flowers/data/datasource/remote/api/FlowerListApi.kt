package com.touhidapps.flowers.data.datasource.remote.api

import com.touhidapps.flowers.data.datasource.remote.dto.FlowersDto


interface FlowerListApi {
    suspend fun getFlowers(): com.touhidapps.flowers.data.datasource.remote.dto.FlowersDto
}