package com.touhidapps.kmp.server

import kotlinx.serialization.json.Json
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.json.KotlinSerializationJsonHttpMessageConverter
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Spring Boot's web starter defaults to Jackson. This registers kotlinx.
 * serialization instead (ahead of Jackson) so `@Serializable` request/
 * response bodies — like [Greeting] — go through it, not reflection.
 */
@Configuration
class SerializationConfig : WebMvcConfigurer {
    override fun extendMessageConverters(converters: MutableList<HttpMessageConverter<*>>) {
        converters.add(0, KotlinSerializationJsonHttpMessageConverter(Json { ignoreUnknownKeys = true }))
    }
}
