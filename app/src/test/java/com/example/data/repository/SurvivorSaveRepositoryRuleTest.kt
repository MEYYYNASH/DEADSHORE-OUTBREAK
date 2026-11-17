package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.SurvivorSave
import com.example.data.SurvivorSaveRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SurvivorSaveRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun saveProgress_andLoadSave_authenticatedOwner_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repository = SurvivorSaveRepository(firestore, auth)
    val save =
      SurvivorSave(
        userId = uid,
        displayName = "Capitan",
        wave = 3L,
        highestWave = 5L,
        totalKills = 27L,
        waveKills = 4L,
        health = 92.0,
        food = 75.0,
        water = 68.0,
        stamina = 100.0,
        activeHotbarSlot = 0L,
        m4Mag = 26L,
        glockMag = 17L,
        flashlightOn = true,
        dayMode = false,
        backpackItems =
          listOf(
            mapOf("slotIndex" to 0L, "itemId" to "ammo_556", "quantity" to 60L),
            mapOf("slotIndex" to 1L, "itemId" to "medkit", "quantity" to 2L),
          ),
      )

    val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) { repository.saveProgress(save) }
    assertTrue(saveResult.isSuccess)

    val loaded = withTimeout(DEFAULT_TIMEOUT_MS) { repository.loadSave().getOrThrow() }
    assertNotNull(loaded)
    assertEquals(3L, loaded!!.wave)
    assertEquals(27L, loaded.totalKills)

    val observed =
      withTimeout(FLOW_TIMEOUT_MS) {
        repository.observeSave(uid).first { it != null && it.wave == 3L }
      }
    assertNotNull(observed)
    assertEquals("Capitan", observed!!.displayName)
  }

  @Test
  fun crossUserRead_isDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = SurvivorSaveRepository(firestore, auth)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.saveProgress(SurvivorSave(userId = aliceUid, displayName = "Alice")).getOrThrow()
    }

    signInTestUser(BOB_EMAIL)
    var denied = false
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        firestore.collection("users").document(aliceUid).collection("saves").document("current").get().await()
      }
    } catch (e: FirebaseFirestoreException) {
      denied = e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }
    assertTrue("Expected PERMISSION_DENIED on cross-user read", denied)
  }

  @Test
  fun observeSave_unauthenticated_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    auth.signOut()
    val repository = SurvivorSaveRepository(firestore, auth)

    var denied = false
    try {
      withTimeout(FLOW_TIMEOUT_MS) { repository.observeSave(aliceUid).first() }
    } catch (e: Exception) {
      val firestoreException = e.unwrapFirestoreException()
      denied = firestoreException?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }
    assertTrue("Expected PERMISSION_DENIED when unauthenticated", denied)
  }

  private companion object {
    const val ALICE_EMAIL = "alice_pov@test.com"
    const val BOB_EMAIL = "bob_pov@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
