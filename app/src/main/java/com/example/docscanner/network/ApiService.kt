package com.example.docscanner.network

import com.example.docscanner.model.ImageResponse
import com.example.docscanner.network.DeleteResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*


interface ApiService {

    // GET danh sách ảnh theo userId
    // URL: /index.php/getupload/image?userId=<uid>
    @GET("index.php/getupload/image")
    suspend fun getImages(
        @Query("userId") userId: String
    ): Response<ImageResponse>

    // POST upload ảnh (multipart)
    // URL: /index.php/upload/image
    // Fields: userId (text), image (file), description (text optional)
    @Multipart
    @POST("index.php/upload/image")
    suspend fun uploadImage(
        @Part("userId") userId: RequestBody,
        @Part image: MultipartBody.Part,
        @Part("description") description: RequestBody
    ): Response<UploadResponse>
    // POST xoá ảnh (soft delete: active=0)
    // URL: /index.php/delete/image
    @FormUrlEncoded
    @POST("index.php/delete/image")
    suspend fun deleteImage(
        @Field("userId") userId: String,
        @Field("id") id: Int
    ): Response<DeleteResponse>
}
