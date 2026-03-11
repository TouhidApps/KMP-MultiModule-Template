package com.touhidapps.flowers.domain.usecase

import com.touhidapps.flowers.domain.model.FlowerModel
import com.touhidapps.flowers.domain.repository.FlowerListRepository

class GetFlowerByIdUseCase(private val repository: FlowerListRepository) {
    operator fun invoke(id: Int): FlowerModel? = repository.getFlowerById(id)
}