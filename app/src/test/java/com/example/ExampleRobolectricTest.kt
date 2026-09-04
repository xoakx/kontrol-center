package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.SshKeyManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Host Manager", appName)
  }

  @Test
  fun `verify ssh keypair generation for 1-click provisioning`() {
    val keyPair = SshKeyManager.generateHostKeyPair("hostmanager@android")
    assertNotNull(keyPair)
    assertTrue(keyPair.publicKeyString.contains("hostmanager@android"))
    assertTrue(keyPair.publicKeyString.startsWith("ssh-"))
    assertNotNull(keyPair.privateKeyPem)
    assertNotNull(keyPair.keyFingerprint)
  }
}
