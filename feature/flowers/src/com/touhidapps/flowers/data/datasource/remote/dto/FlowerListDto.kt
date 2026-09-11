package com.touhidapps.flowers.data.datasource.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// datasource/remote/dto/NoticesDto.kt
@Serializable
data class FlowersDto(

    @SerialName("myJsonObject" ) var myJsonObject : List<com.touhidapps.flowers.data.datasource.remote.dto.MyJsonObject> = arrayListOf()

)

@Serializable
data class MyJsonObject (

    @SerialName("id"         ) var id         : Int?              = null,
    @SerialName("isActive"   ) var isActive   : Boolean?          = null,
    @SerialName("name"       ) var name       : String?           = null,
    @SerialName("details"    ) var details    : String?           = null,
    @SerialName("imgData"    ) var imgData    : com.touhidapps.flowers.data.datasource.remote.dto.ImgData?          = com.touhidapps.flowers.data.datasource.remote.dto.ImgData(),
    @SerialName("attributes" ) var attributes : List<String>?     = arrayListOf()

)

@Serializable
data class ImgData (

    @SerialName("fileName" ) var fileName : String? = null,
    @SerialName("baseUrl"  ) var baseUrl  : String? = null

)