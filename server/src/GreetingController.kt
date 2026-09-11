package com.touhidapps.kmp.server

import com.touhidapps.kmp.sayHello
import kotlinx.serialization.Serializable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@Serializable
data class Greeting(val message: String)

@RestController
class GreetingController {
    @GetMapping("/")
    fun greet(): Greeting = Greeting(sayHello("Spring Boot"))
}
