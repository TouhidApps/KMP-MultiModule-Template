package com.touhidapps.flowers.data.repository

import com.touhidapps.flowers.data.datasource.remote.api.FlowerListApi
import com.touhidapps.flowers.data.mapper.toDomain
import com.touhidapps.flowers.domain.model.FlowerModel
import com.touhidapps.flowers.domain.model.FlowersModel
import com.touhidapps.flowers.domain.repository.FlowerListRepository
import com.touhidapps.network.NetworkError
import com.touhidapps.network.ResultNet
import com.touhidapps.network.safeApiCall

class FlowerListRepositoryImpl(private val api: FlowerListApi) :
    FlowerListRepository {

    // When no need to cache data
//    override suspend fun getFlowers(): ResultNet<FlowersModel, NetworkError> =
//        safeApiCall {
//            api.getFlowers().toDomain()
//        }


    // With cache as next page need to show specific flower from the list
    private var cachedFlowers: List<FlowerModel> = emptyList() // Simple cache

    override suspend fun getFlowers(): ResultNet<FlowersModel, NetworkError> = safeApiCall {
        val domainModel = api.getFlowers().toDomain()
        cachedFlowers = domainModel.flowers // Store in cache
        domainModel
    }

    override fun getFlowerById(id: Int): com.touhidapps.flowers.domain.model.FlowerModel? {
        return cachedFlowers.find { it.id == id }
    }
}