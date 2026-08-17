# --- kotlinx-serialization (FIX-8) -----------------------------------------
# Las @Serializable de los payloads (HubRawPayload, LoginRequest/Response,
# CreateTripRequest, TripResponse, AttachmentUploadResponse) DEBEN sobrevivir
# a la minificación: sin estas reglas, R8 elimina los serializers generados y
# el login/ingest rompen SOLO en release — el tipo de bug que no se ve hasta
# la build firmada.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.rusertech.mobile.**$$serializer { *; }
-keepclassmembers class com.rusertech.mobile.** { *** Companion; }
-keepclasseswithmembers class com.rusertech.mobile.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Los nombres de los campos @SerialName no se tocan en las clases serializables
-keepclassmembers @kotlinx.serialization.Serializable class com.rusertech.mobile.** {
    <fields>;
}
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-keep class com.rusertech.mobile.data.remote.api.** { *; }
-keep class com.rusertech.mobile.domain.model.** { *; }
