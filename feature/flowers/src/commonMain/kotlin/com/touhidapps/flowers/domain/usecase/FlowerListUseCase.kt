package com.touhidapps.flowers.domain.usecase

import com.touhidapps.flowers.domain.model.FlowersModel
import com.touhidapps.flowers.domain.repository.FlowerListRepository
import com.touhidapps.network.NetworkError
import com.touhidapps.network.ResultNet

class FlowerListUseCase(private val repository: com.touhidapps.flowers.domain.repository.FlowerListRepository) {

    suspend operator fun invoke(): ResultNet<com.touhidapps.flowers.domain.model.FlowersModel, NetworkError> = repository.getFlowers()
//    suspend operator fun invoke(transaction: FlowersModel): ResultNet<FlowersModel, NetworkError> = repository.getFlowers()

}