package com.example.e2e.harness

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.ArcadeApiService
import com.example.data.database.AppDatabase
import com.example.data.repository.HostRepository
import com.example.data.security.SecureVault
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Base test harness initializing in-memory Room database, MockArcadeDispatcher,
 * Retrofit/Moshi bindings for ArcadeApiService, and coroutine test scopes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class E2eTestHarness {

    lateinit var context: Context
    lateinit var database: AppDatabase
    lateinit var hostRepository: HostRepository
    lateinit var secureVault: SecureVault
    lateinit var dispatcher: MockArcadeDispatcher
    lateinit var okHttpClient: OkHttpClient
    lateinit var retrofit: Retrofit
    lateinit var apiService: ArcadeApiService

    val testDispatcher = StandardTestDispatcher()
    val testScope = TestScope(testDispatcher)

    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Before
    open fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // 1. Initialize In-Memory Room DB v3
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        hostRepository = HostRepository(
            hostDao = database.hostDao(),
            rfcDao = database.rfcDao(),
            snippetDao = database.commandSnippetDao(),
            clipboardDao = database.clipboardDao()
        )

        // 2. Secure Vault
        FakeAndroidKeyStoreProvider.install()
        secureVault = SecureVault.getInstance(context)

        // 3. Mock Dispatcher & OkHttp
        dispatcher = MockArcadeDispatcher()
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(dispatcher)
            .connectTimeout(2L, TimeUnit.SECONDS)
            .readTimeout(2L, TimeUnit.SECONDS)
            .build()

        // 4. Retrofit pointing to simulated Arcade host
        retrofit = Retrofit.Builder()
            .baseUrl("http://100.111.123.93:8899/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        apiService = retrofit.create(ArcadeApiService::class.java)
    }

    @After
    open fun tearDown() {
        dispatcher.clearOverrides()
        FakeAndroidKeyStoreSpi.clear()
        database.close()
    }
}
