package com.example.gaino.network

import com.example.gaino.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object PricesHttp {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val ok = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            android.util.Log.d("PricesHttp", "Request: ${request.method} ${request.url}")
            val response = chain.proceed(request)
            android.util.Log.d("PricesHttp", "Response: ${response.code} ${response.message}")
            response
        }
        .build()

    val api: PricesApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.APPS_SCRIPT_BASE)  // must end with '/'
            .client(ok)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(PricesApi::class.java)
    }
}
