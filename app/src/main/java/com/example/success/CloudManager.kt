package com.example.success

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CloudManager {

    private val database = FirebaseDatabase.getInstance().reference

    fun sendAccelerometerData(data: List<AccelerometerData>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Push data to a new location in the database
                val dataRef = database.child("accelerometerData").push()
                val jsonData = data.map {
                    mapOf(
                        "ax" to it.ax,
                        "ay" to it.ay,
                        "az" to it.az,
                        "timestamp" to it.timestamp
                    )
                }
                dataRef.setValue(jsonData).await()
                Log.d("CloudManager", "Data sent successfully: $jsonData")
            } catch (e: Exception) {
                Log.e("CloudManager", "Error sending data to Firebase", e)
            }
        }
    }

    fun getParameters(callback: (EspParameters?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Retrieve parameters from Firebase
                val parametersRef = database.child("espParameters")

                val eventListener = object : ValueEventListener {
                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        // Parse parameters from the snapshot
                        val parameterMap = dataSnapshot.getValue(Map::class.java)
                        if (parameterMap != null) {
                            val intValue = (parameterMap["intValue"] as? Long)?.toInt() ?: 0
                            val stringValue = parameterMap["stringValue"] as? String ?: ""
                            val parameters = EspParameters(intValue, stringValue)
                            callback(parameters)
                        } else {
                            callback(null)
                        }
                    }

                    override fun onCancelled(databaseError: DatabaseError) {
                        Log.e("CloudManager", "Error getting parameters from Firebase", databaseError.toException())
                        callback(null)
                    }
                }

                parametersRef.addListenerForSingleValueEvent(eventListener)
            } catch (e: Exception) {
                Log.e("CloudManager", "Error getting parameters from Firebase", e)
                callback(null)
            }
        }
    }

    fun setParameters(parameters: EspParameters) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Set parameters in Firebase
                val parametersRef = database.child("espParameters")
                val parameterMap = mapOf(
                    "intValue" to parameters.intValue,
                    "stringValue" to parameters.stringValue
                )
                parametersRef.setValue(parameterMap).await()

                Log.d("CloudManager", "Parameters set successfully: $parameters")
            } catch (e: Exception) {
                Log.e("CloudManager", "Error setting parameters in Firebase", e)
            }
        }
    }
}