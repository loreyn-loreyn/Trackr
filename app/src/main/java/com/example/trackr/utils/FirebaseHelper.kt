package com.example.trackr.utils

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.example.trackr.models.*
import kotlinx.coroutines.tasks.await

object FirebaseHelper {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    // Collections
    const val USERS_COLLECTION = "users"
    const val ALARMS_COLLECTION = "alarms"
    const val TIMERS_COLLECTION = "timer_sets"
    const val ACTIVITIES_COLLECTION = "activities"
    const val FINISHED_ACTIVITIES_COLLECTION = "finished_activities"

    // Auth Functions
    fun getCurrentUserId(): String? = auth.currentUser?.uid

    fun isUserLoggedIn(): Boolean = auth.currentUser != null

    /**
     * Check if an account exists in Firestore database
     * This is the DEFINITIVE check - if not in Firestore, account doesn't exist in our system
     */
    suspend fun checkAccountExistsInDatabase(email: String): Boolean {
        return try {
            android.util.Log.d("FirebaseHelper", "=== CHECKING ACCOUNT IN DATABASE ===")
            android.util.Log.d("FirebaseHelper", "Email: $email")

            // Check Firestore for user with this email
            val snapshot = db.collection(USERS_COLLECTION)
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .await()

            val exists = !snapshot.isEmpty

            android.util.Log.d("FirebaseHelper", "Account exists in database: $exists")
            android.util.Log.d("FirebaseHelper", "Documents found: ${snapshot.size()}")

            if (!exists) {
                android.util.Log.d("FirebaseHelper", "❌ NO USER DOCUMENT FOUND FOR: $email")
            } else {
                android.util.Log.d("FirebaseHelper", "✅ USER DOCUMENT EXISTS FOR: $email")
            }

            exists
        } catch (e: Exception) {
            android.util.Log.e("FirebaseHelper", "❌ Error checking database: ${e.message}", e)
            // On error, return false to show "account doesn't exist"
            // This is safer than assuming it exists
            false
        }
    }

    suspend fun registerUser(email: String, password: String, user: User): Result<String> {
        return try {
            // Check if account already exists in our database
            val accountExists = checkAccountExistsInDatabase(email)
            if (accountExists) {
                android.util.Log.d("FirebaseHelper", "❌ Registration failed: Account already exists")
                return Result.failure(Exception("An account with this email already exists"))
            }

            android.util.Log.d("FirebaseHelper", "Creating new Firebase Auth account...")
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw Exception("User ID not found")

            android.util.Log.d("FirebaseHelper", "Saving user to Firestore database...")
            val userWithId = user.copy(uid = uid)
            db.collection(USERS_COLLECTION).document(uid).set(userWithId.toMap()).await()

            android.util.Log.d("FirebaseHelper", "✅ User registered successfully: $email")
            Result.success(uid)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseHelper", "❌ Registration error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun loginUser(email: String, password: String): Result<String> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw Exception("User ID not found")
            Result.success(uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logoutUser() {
        auth.signOut()
    }

    /**
     * Get pending password reset for an email (if exists)
     * Returns the new password if there's a pending reset, null otherwise
     */
    suspend fun getPendingPasswordReset(email: String): String? {
        return try {
            val resetDoc = db.collection("password_resets")
                .document(email)
                .get()
                .await()

            if (resetDoc.exists() && resetDoc.getBoolean("used") == false) {
                resetDoc.getString("newPassword")
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseHelper", "Error getting pending reset: ${e.message}")
            null
        }
    }

    /**
     * Mark password reset as used
     */
    suspend fun markPasswordResetAsUsed(email: String): Result<Unit> {
        return try {
            db.collection("password_resets")
                .document(email)
                .update("used", true)
                .await()

            android.util.Log.d("FirebaseHelper", "Password reset marked as used for $email")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseHelper", "Error marking reset as used: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Update current user's password (user must be logged in)
     */
    suspend fun updateCurrentUserPassword(newPassword: String): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                currentUser.updatePassword(newPassword).await()
                android.util.Log.d("FirebaseHelper", "Password updated successfully")
                Result.success(Unit)
            } else {
                Result.failure(Exception("No user logged in"))
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseHelper", "Error updating password: ${e.message}")
            Result.failure(e)
        }
    }

    // User Functions
    suspend fun getUserData(userId: String): Result<User> {
        return try {
            val document = db.collection(USERS_COLLECTION).document(userId).get().await()
            val user = document.toObject(User::class.java)
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(userId: String, updates: Map<String, Any>): Result<Unit> {
        return try {
            db.collection(USERS_COLLECTION)
                .document(userId)
                .update(updates)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Alarm Functions
    suspend fun saveAlarm(alarm: Alarm): Result<String> {
        return try {
            val alarmId = if (alarm.id.isEmpty()) {
                db.collection(ALARMS_COLLECTION).document().id
            } else {
                alarm.id
            }

            val alarmWithId = alarm.copy(id = alarmId)

            db.collection(ALARMS_COLLECTION)
                .document(alarmId)
                .set(alarmWithId.toMap())
                .await()

            Result.success(alarmId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAlarms(userId: String): Result<List<Alarm>> {
        return try {
            val snapshot = db.collection(ALARMS_COLLECTION)
                .whereEqualTo("userId", userId)
                .get()
                .await()

            val alarms = snapshot.documents.mapNotNull { it.toObject(Alarm::class.java) }
                .sortedWith(compareBy({ it.hour }, { it.minute }))

            Result.success(alarms)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateAlarm(alarmId: String, isEnabled: Boolean): Result<Unit> {
        return try {
            db.collection(ALARMS_COLLECTION)
                .document(alarmId)
                .update("isEnabled", isEnabled)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAlarm(alarmId: String): Result<Unit> {
        return try {
            db.collection(ALARMS_COLLECTION).document(alarmId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Timer Methods
    suspend fun saveTimerSet(timerSet: TimerSet): Result<String> {
        return try {
            val timerId = if (timerSet.id.isEmpty()) {
                db.collection(TIMERS_COLLECTION).document().id
            } else {
                timerSet.id
            }

            val timerWithId = timerSet.copy(id = timerId)
            db.collection(TIMERS_COLLECTION).document(timerId).set(timerWithId.toMap()).await()

            Result.success(timerId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTimerSets(userId: String): Result<List<TimerSet>> {
        return try {
            val snapshot = db.collection(TIMERS_COLLECTION)
                .whereEqualTo("userId", userId)
                .get()
                .await()

            val timers = snapshot.documents.mapNotNull { it.toObject(TimerSet::class.java) }
            Result.success(timers)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTimerSet(timerId: String): Result<Unit> {
        return try {
            db.collection(TIMERS_COLLECTION).document(timerId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveActivity(activity: Activity): Result<String> {
        return try {
            android.util.Log.d("FirebaseHelper", "=== SAVING ACTIVITY ===")
            android.util.Log.d("FirebaseHelper", "Activity title: ${activity.title}")
            android.util.Log.d("FirebaseHelper", "Activity ID (before): ${activity.id}")

            val docRef = if (activity.id.isEmpty()) {
                val newRef = db.collection(ACTIVITIES_COLLECTION).document()
                android.util.Log.d("FirebaseHelper", "Creating new document with ID: ${newRef.id}")
                newRef
            } else {
                android.util.Log.d("FirebaseHelper", "Using existing ID: ${activity.id}")
                db.collection(ACTIVITIES_COLLECTION).document(activity.id)
            }

            val activityWithId = activity.copy(id = docRef.id)
            android.util.Log.d("FirebaseHelper", "Saving with ID: ${activityWithId.id}")

            docRef.set(activityWithId.toMap()).await()

            android.util.Log.d("FirebaseHelper", "✅ Activity saved successfully!")
            Result.success(docRef.id)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseHelper", "❌ Error saving activity: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getActivities(userId: String, filter: String = "All"): Result<List<Activity>> {
        return try {
            android.util.Log.d("FirebaseHelper", "=== GETTING ACTIVITIES ===")
            android.util.Log.d("FirebaseHelper", "User ID: $userId")

            val snapshot = db.collection(ACTIVITIES_COLLECTION)
                .whereEqualTo("userId", userId)
                .get()
                .await()

            android.util.Log.d("FirebaseHelper", "Found ${snapshot.size()} documents")

            val activities = snapshot.documents.mapNotNull { doc ->
                try {
                    val activity = Activity(
                        id = doc.id,
                        userId = doc.getString("userId") ?: "",
                        title = doc.getString("title") ?: "",
                        description = doc.getString("description") ?: "",
                        category = doc.getString("category") ?: "Work",
                        dateTimeInMillis = doc.getLong("dateTimeInMillis") ?: 0L,
                        isCompleted = doc.getBoolean("isCompleted") ?: false,
                        completedAt = doc.getLong("completedAt") ?: 0L,
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                    )

                    android.util.Log.d(
                        "FirebaseHelper",
                        "Loaded activity: ${activity.title}, ID: ${activity.id}"
                    )
                    activity
                } catch (e: Exception) {
                    android.util.Log.e("FirebaseHelper", "Error parsing activity: ${e.message}")
                    null
                }
            }

            android.util.Log.d("FirebaseHelper", "✅ Returning ${activities.size} activities")
            Result.success(activities)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseHelper", "❌ Error getting activities: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deleteActivity(activityId: String): Result<Unit> {
        return try {
            if (activityId.isEmpty()) {
                throw Exception("Activity ID is empty - cannot delete")
            }

            android.util.Log.d(
                "FirebaseHelper",
                "Deleting activity from ACTIVITIES collection: $activityId"
            )

            db.collection(ACTIVITIES_COLLECTION)
                .document(activityId)
                .delete()
                .await()

            android.util.Log.d("FirebaseHelper", "✅ Activity deleted successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseHelper", "❌ Error deleting activity: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun markActivityAsFinished(activity: Activity): Result<Unit> {
        return try {
            if (activity.id.isEmpty()) {
                throw Exception("Activity ID is empty - cannot mark as finished")
            }

            android.util.Log.d("FirebaseHelper", "=== MARKING AS FINISHED ===")
            android.util.Log.d("FirebaseHelper", "Activity: ${activity.title}")
            android.util.Log.d("FirebaseHelper", "Activity ID: ${activity.id}")

            val finishedActivity = activity.copy(
                isCompleted = true,
                completedAt = System.currentTimeMillis()
            )

            db.collection(FINISHED_ACTIVITIES_COLLECTION)
                .document(activity.id)
                .set(finishedActivity.toMap())
                .await()

            android.util.Log.d("FirebaseHelper", "✅ Saved to finished_activities")

            db.collection(ACTIVITIES_COLLECTION)
                .document(activity.id)
                .delete()
                .await()

            android.util.Log.d("FirebaseHelper", "✅ Removed from activities")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseHelper", "❌ Error marking as finished: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun unmarkActivityAsFinished(activity: Activity): Result<Unit> {
        return try {
            if (activity.id.isEmpty()) {
                throw Exception("Activity ID is empty - cannot unmark as finished")
            }

            android.util.Log.d("FirebaseHelper", "=== UNMARKING AS FINISHED ===")
            android.util.Log.d("FirebaseHelper", "Activity: ${activity.title}")
            android.util.Log.d("FirebaseHelper", "Activity ID: ${activity.id}")

            val activeActivity = activity.copy(
                isCompleted = false,
                completedAt = 0
            )

            db.collection(ACTIVITIES_COLLECTION)
                .document(activity.id)
                .set(activeActivity.toMap())
                .await()

            android.util.Log.d("FirebaseHelper", "✅ Saved to activities")

            db.collection(FINISHED_ACTIVITIES_COLLECTION)
                .document(activity.id)
                .delete()
                .await()

            android.util.Log.d("FirebaseHelper", "✅ Removed from finished_activities")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseHelper", "❌ Error unmarking as finished: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getFinishedActivities(userId: String): Result<List<Activity>> {
        return try {
            android.util.Log.d("FirebaseHelper", "=== GETTING FINISHED ACTIVITIES ===")
            android.util.Log.d("FirebaseHelper", "User ID: $userId")

            val snapshot = db.collection(FINISHED_ACTIVITIES_COLLECTION)
                .whereEqualTo("userId", userId)
                .get()
                .await()

            android.util.Log.d("FirebaseHelper", "Found ${snapshot.size()} finished activities")

            val activities = snapshot.documents.mapNotNull { doc ->
                try {
                    val activity = Activity(
                        id = doc.id,
                        userId = doc.getString("userId") ?: "",
                        title = doc.getString("title") ?: "",
                        description = doc.getString("description") ?: "",
                        category = doc.getString("category") ?: "Work",
                        dateTimeInMillis = doc.getLong("dateTimeInMillis") ?: 0L,
                        isCompleted = doc.getBoolean("isCompleted") ?: false,
                        completedAt = doc.getLong("completedAt") ?: 0L,
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                    )

                    android.util.Log.d(
                        "FirebaseHelper",
                        "Loaded finished: ${activity.title}, ID: ${activity.id}"
                    )
                    activity
                } catch (e: Exception) {
                    android.util.Log.e(
                        "FirebaseHelper",
                        "Error parsing finished activity: ${e.message}"
                    )
                    null
                }
            }.sortedByDescending { it.completedAt }

            android.util.Log.d(
                "FirebaseHelper",
                "✅ Returning ${activities.size} finished activities"
            )
            Result.success(activities)
        } catch (e: Exception) {
            android.util.Log.e(
                "FirebaseHelper",
                "❌ Error getting finished activities: ${e.message}"
            )
            Result.failure(e)
        }
    }

    suspend fun deleteFinishedActivity(activityId: String): Result<Unit> {
        return try {
            if (activityId.isEmpty()) {
                throw Exception("Activity ID is empty - cannot delete")
            }

            android.util.Log.d(
                "FirebaseHelper",
                "Deleting from FINISHED_ACTIVITIES collection: $activityId"
            )

            db.collection(FINISHED_ACTIVITIES_COLLECTION)
                .document(activityId)
                .delete()
                .await()

            android.util.Log.d("FirebaseHelper", "✅ Finished activity deleted successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e(
                "FirebaseHelper",
                "❌ Error deleting finished activity: ${e.message}",
                e
            )
            Result.failure(e)
        }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        return try {
            val user = auth.currentUser
            if (user != null && user.email != null) {
                val credential = com.google.firebase.auth.EmailAuthProvider
                    .getCredential(user.email!!, currentPassword)

                user.reauthenticate(credential).await()
                user.updatePassword(newPassword).await()
                Result.success(Unit)
            } else {
                Result.failure(Exception("User not logged in"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteUserAccount(): Result<Unit> {
        return try {
            val user = auth.currentUser
            val userId = user?.uid

            if (user != null && userId != null) {
                android.util.Log.d("FirebaseHelper", "=== DELETING USER ACCOUNT ===")
                android.util.Log.d("FirebaseHelper", "User ID: $userId")

                try {
                    android.util.Log.d("FirebaseHelper", "Deleting user document...")
                    db.collection(USERS_COLLECTION).document(userId).delete().await()

                    android.util.Log.d("FirebaseHelper", "Deleting alarms...")
                    val alarmsSnapshot = db.collection(ALARMS_COLLECTION)
                        .whereEqualTo("userId", userId)
                        .get()
                        .await()
                    alarmsSnapshot.documents.forEach {
                        it.reference.delete().await()
                    }
                    android.util.Log.d("FirebaseHelper", "Deleted ${alarmsSnapshot.size()} alarms")

                    android.util.Log.d("FirebaseHelper", "Deleting activities...")
                    val activitiesSnapshot = db.collection(ACTIVITIES_COLLECTION)
                        .whereEqualTo("userId", userId)
                        .get()
                        .await()
                    activitiesSnapshot.documents.forEach {
                        it.reference.delete().await()
                    }
                    android.util.Log.d("FirebaseHelper", "Deleted ${activitiesSnapshot.size()} activities")

                    android.util.Log.d("FirebaseHelper", "Deleting finished activities...")
                    val finishedSnapshot = db.collection(FINISHED_ACTIVITIES_COLLECTION)
                        .whereEqualTo("userId", userId)
                        .get()
                        .await()
                    finishedSnapshot.documents.forEach {
                        it.reference.delete().await()
                    }
                    android.util.Log.d("FirebaseHelper", "Deleted ${finishedSnapshot.size()} finished activities")

                    android.util.Log.d("FirebaseHelper", "Deleting timers...")
                    val timersSnapshot = db.collection(TIMERS_COLLECTION)
                        .whereEqualTo("userId", userId)
                        .get()
                        .await()
                    timersSnapshot.documents.forEach {
                        it.reference.delete().await()
                    }
                    android.util.Log.d("FirebaseHelper", "Deleted ${timersSnapshot.size()} timers")

                    android.util.Log.d("FirebaseHelper", "All Firestore data deleted successfully")

                } catch (e: Exception) {
                    android.util.Log.e("FirebaseHelper", "Error deleting Firestore data: ${e.message}", e)
                    return Result.failure(Exception("Failed to delete user data: ${e.message}"))
                }

                try {
                    android.util.Log.d("FirebaseHelper", "Deleting Firebase Auth account...")
                    user.delete().await()
                    android.util.Log.d("FirebaseHelper", "✅ Account deleted successfully!")
                    Result.success(Unit)
                } catch (e: Exception) {
                    android.util.Log.e("FirebaseHelper", "Error deleting auth account: ${e.message}", e)
                    if (e.message?.contains("recent authentication", ignoreCase = true) == true) {
                        Result.failure(Exception("REAUTH_REQUIRED"))
                    } else {
                        Result.failure(Exception("Failed to delete authentication: ${e.message}"))
                    }
                }

            } else {
                android.util.Log.e("FirebaseHelper", "Cannot delete: User not logged in")
                Result.failure(Exception("User not logged in"))
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseHelper", "Unexpected error in deleteUserAccount: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteUserAccountWithReauth(password: String): Result<Unit> {
        return try {
            val user = auth.currentUser
            val email = user?.email

            if (user == null || email == null) {
                return Result.failure(Exception("User not logged in"))
            }

            android.util.Log.d("FirebaseHelper", "Re-authenticating user before deletion...")

            val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
            user.reauthenticate(credential).await()

            android.util.Log.d("FirebaseHelper", "✅ Re-authentication successful")

            deleteUserAccount()
        } catch (e: Exception) {
            android.util.Log.e("FirebaseHelper", "Re-authentication failed: ${e.message}", e)
            if (e.message?.contains("password", ignoreCase = true) == true) {
                Result.failure(Exception("Incorrect password"))
            } else {
                Result.failure(Exception("Authentication failed: ${e.message}"))
            }
        }
    }
}