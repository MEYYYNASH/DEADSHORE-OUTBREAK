package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class SurvivorSaveRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    ),
    Firebase.auth,
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing cloud saves.")
  }

  fun observeSave(userId: String, saveId: String = DEFAULT_SAVE_ID): Flow<SurvivorSave?> = flow {
    val docRef = db.collection("users").document(userId).collection("saves").document(saveId)
    emitAll(
      docRef
        .snapshots()
        .map { snapshot ->
          if (!snapshot.exists()) {
            null
          } else {
            snapshot.toObject(
              SurvivorSave::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
          }
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path, auth)
          }
          throw error
        }
    )
  }

  suspend fun loadSave(saveId: String = DEFAULT_SAVE_ID): Result<SurvivorSave?> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("saves").document(saveId)
    return try {
      val snapshot = docRef.get().await()
      if (!snapshot.exists()) {
        Result.success(null)
      } else {
        Result.success(
          snapshot.toObject(
            SurvivorSave::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        )
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun saveProgress(save: SurvivorSave, saveId: String = DEFAULT_SAVE_ID): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("saves").document(saveId)
    return try {
      val existing = docRef.get().await()
      val normalizedHotbar =
        List(6) { idx -> save.hotbarSlots.getOrNull(idx)?.take(40) ?: "" }
      val normalizedEquipment =
        mapOf(
          "head" to (save.equipment["head"]?.take(40) ?: ""),
          "vest" to (save.equipment["vest"]?.take(40) ?: ""),
          "body" to (save.equipment["body"]?.take(40) ?: ""),
          "back" to (save.equipment["back"]?.take(40) ?: ""),
          "legs" to (save.equipment["legs"]?.take(40) ?: ""),
          "feet" to (save.equipment["feet"]?.take(40) ?: ""),
        )
      val payload =
        mutableMapOf<String, Any>(
          "userId" to uid,
          "displayName" to save.displayName.ifBlank { "Survivor" }.take(40),
          "wave" to save.wave.coerceIn(1L, 9999L),
          "highestWave" to save.highestWave.coerceIn(1L, 9999L),
          "totalKills" to save.totalKills.coerceIn(0L, 1_000_000L),
          "waveKills" to save.waveKills.coerceIn(0L, 10_000L),
          "health" to save.health.coerceIn(0.0, 100.0),
          "food" to save.food.coerceIn(0.0, 100.0),
          "water" to save.water.coerceIn(0.0, 100.0),
          "stamina" to save.stamina.coerceIn(0.0, 100.0),
          "activeHotbarSlot" to save.activeHotbarSlot.coerceIn(0L, 5L),
          "m4Mag" to save.m4Mag.coerceIn(0L, 30L),
          "glockMag" to save.glockMag.coerceIn(0L, 17L),
          "flashlightOn" to save.flashlightOn,
          "dayMode" to save.dayMode,
          "backpackItems" to save.backpackItems.take(24),
          "hotbarSlots" to normalizedHotbar,
          "equipment" to normalizedEquipment,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      if (!existing.exists()) {
        payload["createdAt"] = FieldValue.serverTimestamp()
        docRef.set(payload).await()
      } else {
        docRef.update(payload).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun deleteSave(saveId: String = DEFAULT_SAVE_ID): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("saves").document(saveId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
      Result.failure(e)
    }
  }

  companion object {
    const val DEFAULT_SAVE_ID = "current"
  }
}
