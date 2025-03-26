package com.example.success

data class AccelerometerData(
    val ax: Float,
    val ay: Float,
    val az: Float,
    val timestamp: Long = System.currentTimeMillis()
)

data class EspParameters(
    val parameter1: Int,
    val parameter2: String
    // Add more parameters as needed
)