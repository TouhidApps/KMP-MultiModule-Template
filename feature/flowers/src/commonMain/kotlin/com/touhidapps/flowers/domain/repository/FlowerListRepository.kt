package com.touhidapps.flowers.domain.repository

import com.touhidapps.flowers.domain.model.FlowerModel
import com.touhidapps.flowers.domain.model.FlowersModel
import com.touhidapps.network.NetworkError
import com.touhidapps.network.ResultNet

interface FlowerListRepository {
    suspend fun getFlowers(): ResultNet<com.touhidapps.flowers.domain.model.FlowersModel, NetworkError>

    fun getFlowerById(id: Int): com.touhidapps.flowers.domain.model.FlowerModel?

}