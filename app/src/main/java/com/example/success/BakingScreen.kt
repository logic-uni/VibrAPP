package com.example.success

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

val images = arrayOf(
    // Image generated using Gemini from the prompt "cupcake image"
    R.drawable.baked_goods_1,
    // Image generated using Gemini from the prompt "cookies images"
    R.drawable.baked_goods_2,
    // Image generated using Gemini from the prompt "cake images"
    R.drawable.baked_goods_3,
)
val imageDescriptions = arrayOf(
    R.string.image1_description,
    R.string.image2_description,
    R.string.image3_description,
)

@Composable
fun BakingScreen(
    bakingViewModel: BakingViewModel = viewModel(),
    bluetoothManager: BluetoothManager,
    cloudManager: CloudManager
) {
    val selectedImage = remember { mutableIntStateOf(0) }
    val placeholderPrompt = stringResource(R.string.prompt_placeholder)
    val placeholderResult = stringResource(R.string.results_placeholder)
    var prompt by rememberSaveable { mutableStateOf(placeholderPrompt) }
    var result by rememberSaveable { mutableStateOf(placeholderResult) }
    val uiState by bakingViewModel.uiState.collectAsState()
    val context = LocalContext.current
    var isBluetoothEnabled by remember { mutableStateOf(false) }
    val receivedData by bluetoothManager.receivedData.collectAsState()
    val isConnected by bluetoothManager.isConnected.collectAsState()
    var espParameters by remember { mutableStateOf<EspParameters?>(null) }
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = stringResource(R.string.baking_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(images) { index, image ->
                var imageModifier = Modifier
                    .padding(start = 8.dp, end = 8.dp)
                    .requiredSize(200.dp)
                    .clickable {
                        selectedImage.intValue = index
                    }
                if (index == selectedImage.intValue) {
                    imageModifier =
                        imageModifier.border(BorderStroke(4.dp, MaterialTheme.colorScheme.primary))
                }
                Image(
                    painter = painterResource(image),
                    contentDescription = stringResource(imageDescriptions[index]),
                    modifier = imageModifier
                )
            }
        }

        Row(
            modifier = Modifier.padding(all = 16.dp)
        ) {
            TextField(
                value = prompt,
                label = { Text(stringResource(R.string.label_prompt)) },
                onValueChange = { prompt = it },
                modifier = Modifier
                    .weight(0.8f)
                    .padding(end = 16.dp)
                    .align(Alignment.CenterVertically)
            )

            Button(
                onClick = {
                    val bitmap = BitmapFactory.decodeResource(
                        context.resources,
                        images[selectedImage.intValue]
                    )
                    bakingViewModel.sendPrompt(bitmap, prompt)
                },
                enabled = prompt.isNotEmpty(),
                modifier = Modifier
                    .align(Alignment.CenterVertically)
            ) {
                Text(text = stringResource(R.string.action_go))
            }
        }

        if (uiState is UiState.Loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            var textColor = MaterialTheme.colorScheme.onSurface
            if (uiState is UiState.Error) {
                textColor = MaterialTheme.colorScheme.error
                result = (uiState as UiState.Error).errorMessage
            } else if (uiState is UiState.Success) {
                textColor = MaterialTheme.colorScheme.onSurface
                result = (uiState as UiState.Success).outputText
            }
            val scrollState = rememberScrollState()
            Text(
                text = result,
                textAlign = TextAlign.Start,
                color = textColor,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(16.dp)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            )
        }

        // Bluetooth Section
        Text(text = "Bluetooth", modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {
            val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
            isBluetoothEnabled = bluetoothAdapter?.isEnabled ?: false

            if (!isBluetoothEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                context.startActivity(enableBtIntent)
            }
        }) {
            Text(text = "Enable Bluetooth")
        }

        Button(onClick = { bluetoothManager.startDiscovery() }) {
            Text(text = "Start Discovery")
        }

        Button(onClick = {
            if (isConnected) {
                bluetoothManager.disconnect()
            } else {
                val deviceAddress = "your_bluetooth_device_address" // Replace with the actual device address
                bluetoothManager.connectToDevice(deviceAddress)
            }
        }) {
            Text(text = if (isConnected) "Disconnect" else "Connect")
        }

        if (isConnected) {
            Button(onClick = {
                val messageToSend = "Hello ESP32"
                bluetoothManager.sendData(messageToSend)
            }) {
                Text(text = "Send message to device")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        // Cloud Section
        Text(text = "Cloud", modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            cloudManager.sendAccelerometerData(receivedData)
        }) {
            Text(text = "Send Data to Cloud")
        }

        Button(onClick = {
            cloudManager.getParameters { parameters ->
                espParameters = parameters
            }
        }) {
            Text(text = "Get parameters from Cloud")
        }

        // Parameters Received
        if (espParameters != null) {
            Text(text = "Parameters received from Cloud:", color = Color.Red)
            Text(text = "parameter1: ${espParameters!!.parameter1}", color = Color.Red)
            Text(text = "parameter2: ${espParameters!!.parameter2}", color = Color.Red)
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Plotting Section
        TimeSeriesPlot(data = receivedData, label = "ax", color = Color.Red)
        TimeSeriesPlot(data = receivedData, label = "ay", color = Color.Green)
        TimeSeriesPlot(data = receivedData, label = "az", color = Color.Blue)
    }
}