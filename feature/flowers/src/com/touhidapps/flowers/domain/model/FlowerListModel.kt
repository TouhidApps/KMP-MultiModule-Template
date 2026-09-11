package com.touhidapps.flowers.domain.model

data class FlowersModel(

    var flowers : List<com.touhidapps.flowers.domain.model.FlowerModel> = arrayListOf()

)

data class FlowerModel (

    var id         : Int?              = null,
    var isActive   : Boolean?          = null,
    var name       : String?           = null,
    var details    : String?           = null,
    var imgData    : com.touhidapps.flowers.domain.model.ImgData?          = com.touhidapps.flowers.domain.model.ImgData(),
    var attributes : List<String>?     = arrayListOf()

)

data class ImgData (

    var fileName : String? = null,
    var baseUrl  : String? = null

)




