package com.touhidapps.flowers.data.mapper

import com.touhidapps.flowers.data.datasource.remote.dto.FlowersDto
import com.touhidapps.flowers.data.datasource.remote.dto.MyJsonObject
import com.touhidapps.flowers.domain.model.FlowerModel
import com.touhidapps.flowers.domain.model.FlowersModel
import com.touhidapps.flowers.domain.model.ImgData


// datasource/mapper/NoticeMapper.kt
fun FlowersDto.toDomain(): FlowersModel =
    FlowersModel(

        flowers = myJsonObject.map {
            FlowerModel(
                id = it.id,
                isActive = it.isActive,
                name = it.name,
                details = it.details,
                imgData = ImgData(
                    fileName = it.imgData?.fileName,
                    baseUrl = it.imgData?.baseUrl,
                ),
                attributes = it.attributes,
            )
        }

    )

//Use Entity for Local DB, Dto for network. for postfix and also for mapping toDto/toEntity
fun FlowersModel.toDto(): FlowersDto {

    return FlowersDto(
        myJsonObject = flowers.map {
            MyJsonObject(
                id = it.id,
                isActive = it.isActive,
                name = it.name,
                details = it.details,
                imgData = _root_ide_package_.com.touhidapps.flowers.data.datasource.remote.dto.ImgData(
                    fileName = it.imgData?.fileName,
                    baseUrl = it.imgData?.baseUrl,
                ),
                attributes = it.attributes.orEmpty(),
            )
        }
    )

}

/**
 * Example: For local db
 */
//fun SummaryEntity.toDomain(): Summary = Summary(
//    totalIncome = totalIncome,
//    totalExpense = totalExpense,
//    balance = balance
//)
//
////Use Entity for DB, Dto for network. for postfix and also for mapping toDto/toEntity
//fun Summary.toEntity(): SummaryEntity {
//    return SummaryEntity(
//        totalIncome = totalIncome,
//        totalExpense = totalExpense,
//        balance = balance
//    )
//}
