//This code got messy trying to figure it all out, but it is an app that will connect via bluetooth
//to another phone, and control it's light, vibrate and sound features.
package com.example.bluetoothflash

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.Menu
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.bluetoothflash.R.id.scan_button
import android.annotation.TargetApi
//import android.content.Context
//import android.hardware.Camera
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
//import android.os.Bundle
import android.os.Vibrator
import android.support.annotation.RequiresApi
//import android.support.design.widget.Snackbar
//import android.support.v7.app.AppCompatActivity;
//import android.view.Menu
import android.view.MenuItem
//import android.widget.Button
//import android.widget.Toast

import kotlinx.android.synthetic.main.activity_main.*
//import kotlinx.android.synthetic.main.content_main.*

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.UnsupportedEncodingException
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private var myUUID: UUID? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null //holds the Bluetooth Adapter
    private var mTextarea: TextView? = null                 //for writing messages to screen
    private var server: AcceptThread? = null                //server object
    private var client:ConnectThread? = null
    private var mmSocket: BluetoothSocket? = null
    private var send = "a"

    private val mReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun onReceive(context: Context, intent: Intent) {
            Log.i(LOG_TAG, "BroadcastReceiver onReceive()")
            handleBTDevice(intent)
        }
    }//when activated, scans for Bluetooth devices -- looks to see which ones it can detect

    @TargetApi(Build.VERSION_CODES.M)
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //setSupportActionBar(toolbar)
        myUUID = UUID.fromString(MY_UUID_STRING)        //unique UUID (or GUID) for this App

        mTextarea = findViewById(R.id.textView) as TextView?
        if (mTextarea != null) {
            mTextarea!!.movementMethod = ScrollingMovementMethod()
            mTextarea!!.append("My UUID:  $myUUID \n")
        }
        setUpButtons()

        vibrateButton.setOnClickListener{
            val vibratorService = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibratorService.vibrate(500)
            val toast= Toast.makeText(applicationContext, "Buzzzzzz", Toast.LENGTH_LONG)
            toast.show()
            send = "1"
            getPairedDevices()
            setUpBroadcastReceiver()
        }
        buttonQ.setOnClickListener{
            send = "9"
            getPairedDevices()
            setUpBroadcastReceiver()
        }

        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val buttonLight:Button = findViewById(R.id.flashButton)
        val cameraID = cameraManager.getCameraIdList()[0]
        var light = false

        buttonLight.setOnClickListener{
            if(light == false){
                cameraManager.setTorchMode(cameraID, true)
                val toast= Toast.makeText(applicationContext, "Light", Toast.LENGTH_LONG)
                toast.show()
                light = true
                send = "2"
            } else if(light == true){
                cameraManager.setTorchMode(cameraID, false)
                val toast= Toast.makeText(applicationContext, "No light", Toast.LENGTH_LONG)
                toast.show()
                light = false
                send = "3"
            }

            getPairedDevices()
            setUpBroadcastReceiver()
        }
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 75)
        toneButton.setOnClickListener{
            tone.startTone(ToneGenerator.TONE_DTMF_B,  500)
            tone.startTone(ToneGenerator.TONE_DTMF_C,  250)
            tone.startTone(ToneGenerator.TONE_DTMF_8,  750)
            tone.startTone(ToneGenerator.TONE_DTMF_B,  500)
            val toast= Toast.makeText(applicationContext, "BEEEEEEEEEEEEEEP", Toast.LENGTH_LONG)
            toast.show()
            send = "4"
            getPairedDevices()
            setUpBroadcastReceiver()
        }

    }

    /**
     * Set up the listeners for the two buttons
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun setUpButtons() {
        val scanButton:Button? = findViewById(R.id.scan_button)
        scanButton?.setOnClickListener {       //Scanning is the action performed by the client
            getPairedDevices()
            setUpBroadcastReceiver()
        }
        val connectButton = findViewById<Button>(R.id.connect_button)
        connectButton?.setOnClickListener {    //This button activates the App as the server
            Log.i(TSERVER, "Connect Button setting up server")
            mTextarea!!.append("Connect Button: setting up server\n")
            //make server discoverable for N_SECONDS
            val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, N_SECONDS)
            startActivity(discoverableIntent)
            //create server thread
            server = AcceptThread()
            if (server != null) {   //start server thread
                Log.i(TSERVER, "Connect Button spawning server thread")
                mTextarea!!.append("Connect Button: spawning server thread $server \n")
                server!!.start()     //calls AcceptThread's run() method
            } else {
                Log.i(TSERVER, "setupButtons(): server is null")
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.menu_main, menu);
        return true
    }

    public override fun onResume() {
        super.onResume()
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        Log.i(LOG_TAG, "onResume()")
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            Log.i(LOG_TAG, "No Bluetooth on this device")
            Toast.makeText(baseContext,
                "No Bluetooth on this device", Toast.LENGTH_LONG).show()
        } else if (!mBluetoothAdapter!!.isEnabled) {
            Log.i(LOG_TAG, "enabling Bluetooth")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }
        mTextarea?.append("This device is:  ${mBluetoothAdapter?.name} \n")
        Log.i(LOG_TAG, "End of onResume()")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.i(LOG_TAG, "onActivityResult(): requestCode = $requestCode")
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                Log.i(LOG_TAG, "  --    Bluetooth is enabled")
                getPairedDevices() //find already known paired devices
                setUpBroadcastReceiver()
            }
        }
    }

    private fun getPairedDevices() {//find already known paired devices
        val pairedDevices = mBluetoothAdapter!!.bondedDevices
        Log.i(TCLIENT, "--------------\ngetPairedDevices() - Known Paired Devices")
        // If there are paired devices
        if (pairedDevices.size > 0) {
            for (device in pairedDevices) {
                Log.i(TCLIENT, device.name + "\n" + device)
                mTextarea!!.append("" + device.name + "\n" + device + "\n")
            }
        }
        Log.i(TCLIENT, "getPairedDevices() - End of Known Paired Devices\n------")
    }

    /**
     * Client scans for nearby Bluetooth devices
     */
    private fun setUpBroadcastReceiver() {
        // Create a BroadcastReceiver for ACTION_FOUND
        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED)    {
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                ACCESS_FINE_LOCATION)
            Log.i(TCLIENT,"Getting Permission")
            return
            //Discovery will be setup in onRequestPermissionResult() if permission is granted
        }
        setupDiscovery()
    }

    /**
     * Callback when request for permission is addressed by the user.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        when (requestCode) {
            ACCESS_FINE_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED) {
                    Log.i(LOG_TAG, "Fine_Location Permission granted")
                    setupDiscovery()
                } else {    //tracking won't happen since user denied permission
                    Log.i(LOG_TAG, "Fine_Location Permission refused")
                }
                return
            }
        }
    }

    /**
     * Activate Bluetooth discovery for the client
     */
    private fun setupDiscovery() {
        Log.i(TCLIENT,"Activating Discovery")
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(mReceiver, filter)
        mBluetoothAdapter!!.startDiscovery()
    }

    /**
     * called by BroadcastReceiver callback when a new BlueTooth device is found
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun handleBTDevice(intent: Intent) {
        Log.i(TCLIENT, "handleBRDevice() -- starting   <<<<--------------------")
        val action = intent.action
        // When discovery finds a device
        if (BluetoothDevice.ACTION_FOUND == action) {
            // Get the BluetoothDevice object from the Intent
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            val deviceName  =
                if (device.name != null) {
                    device.name.toString()
                } else {
                    "--no name--"
                }
            Log.i(TCLIENT, deviceName + "\n" + device)
            mTextarea!!.append("$deviceName, $device \n")

            // The following is specific to this App for the client
            if (deviceName.length > 3) { //for now, looking for MSU prefix
                val prefix = deviceName.subSequence(0,3)
                mTextarea!!.append("Prefix = $prefix\n    ")
                if (prefix == "Sam") {//This is the server   THIS IS WHAT NEEDS CHANGING TO SOMETHING
                    Log.i(TCLIENT,"Canceling Discovery")
                    mBluetoothAdapter!!.cancelDiscovery()
                    Log.i(TCLIENT,"Connecting")
                    client = ConnectThread(device)  //FIX** remember and reconnect if interrupted?
                    Log.i(TCLIENT,"Running Connect Thread")
                    client?.start()
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onStop() {
        super.onStop()
        mBluetoothAdapter!!.cancelDiscovery()    //stop looking for Bluetooth devices
        client?.cancel()
    }

    /**
     * Called from server thread to display received message.
     * This action is specific to this App.
     * @param msg The received info to display
     */
    fun echoMsg(msg: String) {
        mTextarea!!.append(msg)
    }

    ////////////////// Client Thread to talk to Server here ///////////////////

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private inner class ConnectThread(mmDevice: BluetoothDevice):Thread(){//from android developer
    private var mmSocket: BluetoothSocket? = null
        private var mmSocket1: BluetoothSocket? = null
        private var mmSocket2: BluetoothSocket? = null

        init {

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            Log.i(TCLIENT, "ConnectThread: init()")
            try {
                // myUUID is the app's UUID string, also used by the server code
                mmSocket = mmDevice.createRfcommSocketToServiceRecord(myUUID)
            } catch (e: IOException) {
                Log.i(TCLIENT, "IOException when creating RFcommSocket\n $e")
            }
        }

        override fun run() {
            // Cancel discovery because it will slow down the connection
            Log.i(TCLIENT, "ConnectThread: run()")
            Log.i(TCLIENT, "in ClientThread - Canceling Discovery")
            mBluetoothAdapter!!.cancelDiscovery()
            if (mmSocket == null) {
                Log.e(TCLIENT,"ConnectThread:run(): mmSocket is null")
            }
            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception after 12 seconds (or so)
                Log.i(TCLIENT, "Connecting to server")
                mmSocket!!.connect()
            } catch (connectException: IOException) {
                Log.i(TCLIENT,
                    "Connect IOException when trying socket connection\n $connectException")
                // Unable to connect; close the socket and get out
                try {
                    mmSocket!!.close()
                } catch (closeException: IOException) {
                    Log.i(TCLIENT,
                        "Close IOException when trying socket connection\n $closeException")
                }

                return
            }
            Log.i(TCLIENT, "Connection Established")
            val sock = mmSocket!!
            // Do work to manage the connection (in a separate thread)
            manageConnectedSocket(sock)      //talk to server
        }

        //manage the connection over the passed-in socket
        private fun manageConnectedSocket(socket: BluetoothSocket) {
            val out: OutputStream
            //val remote: RemoteAction
            val theMessage = "ABC"      //test message: send actual message here
            val msg = theMessage.toByteArray()
            val sentB = send.toByteArray()
            try {
                Log.i(TCLIENT, "Sending the message: [$theMessage]")
                //runOnUiThread{
                //fun toast() {
                //Toast.makeText( applicationContext,"Hello", Toast.LENGTH_SHORT).show()

                //}
                //}
                //remote = socket.remoteDevice


                out = socket.outputStream
                out.write(sentB)
            } catch (ioe: IOException) {
                Log.e(TCLIENT, "IOException when opening outputStream\n $ioe")
                return
            }

        }

        /**
         * Will cancel an in-progress connection, and close the socket
         */
        fun cancel() {
            try {
                mmSocket!!.close()
            } catch (ioe: IOException) {
                Log.e(TCLIENT, "IOException when closing outputStream\n $ioe")
            }
        }
    }


    ///////////////////////////////  ServerSocket stuff here ///////////////////////////

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private inner class AcceptThread : Thread() {  //from android developer
        private var mmServerSocket: BluetoothServerSocket? = null
        var socket: BluetoothSocket? = null


        init {
            // Use a temporary object that is later assigned to mmServerSocket,
            // because mmServerSocket is supposed to be final
            val tmp: BluetoothServerSocket
            try {
                // myUUID is the app's UUID string, also used by the client code
                tmp = mBluetoothAdapter!!.listenUsingRfcommWithServiceRecord(SERVICE_NAME, myUUID)
                Log.i(TSERVER, "AcceptThread registered the server\n")
                mmServerSocket = tmp
            } catch (e: IOException) {
                Log.e(TSERVER, "AcceptThread registering the server failed\n $e")
            }
        }

        @RequiresApi(Build.VERSION_CODES.M)
        override fun run() {
            //var socket: BluetoothSocket?
            // Keep listening until exception occurs or a socket is returned
            while (true) {
                Log.i(TSERVER, "AcceptTread.run(): Server Looking for a Connection")
                try {
                    socket = mmServerSocket!!.accept()  //block until connection made or exception
                    Log.i(TSERVER, "Server socket accepting a connection")
                } catch (e: IOException) {
                    Log.e(TSERVER, "socket accept threw an exception\n $e")
                    break
                }

                // If a connection was accepted
                if (socket != null) {
                    Log.i(TSERVER, "Server Thread run(): Connection accepted")
                    // Do work to manage the connection (in a separate thread)
                    manageConnectedSocket(socket!!)
                    break
                } else {
                    Log.i(TSERVER, "Server Thread run(): The socket is null")
                }
            }
        }

        //manage the Server's end of the conversation on the passed-in socket
        @RequiresApi(Build.VERSION_CODES.M)
        fun manageConnectedSocket(socket: BluetoothSocket) {
            //var button1 =
            Log.i(TSERVER, "\nManaging the Socket\n")
            val inSt: InputStream
            val nBytes: Int
            val msg = ByteArray(255) //arbitrary size
            //var flashLightStatus =false

            try {

                inSt = socket.inputStream
                nBytes = inSt.read(msg)
                Log.i(TSERVER, "\nServer Received $nBytes \n")
            } catch (ioe: IOException) {
                Log.e(TSERVER, "IOException when opening inputStream\n $ioe")
                return
            }



            try {

                val msgString = msg.toString(Charsets.UTF_8)
                //val toast = Toast.makeText(applicationContext, "Hello Javatpoint", Toast.LENGTH_LONG)
                Log.i(TSERVER, "\nServer Received  $nBytes, Bytes:  [$msgString]\n")
                runOnUiThread { echoMsg("\nReceived $nBytes:  [$msgString]\n") }
                Log.i(TSERVER, msgString[0].toString())


                if (msgString[0].toString() == "1") {
                    Log.i(TSERVER, msgString)

                    val vibratorService = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    vibratorService.vibrate(500)
                } else if (msgString[0].toString() == "2") {
                    val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val cameraId = cameraManager.getCameraIdList()[0]
                    cameraManager.setTorchMode(cameraId, true)
                } else if (msgString[0].toString() == "3") {
                    val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val cameraId = cameraManager.getCameraIdList()[0]
                    cameraManager.setTorchMode(cameraId, false)
                } else if (msgString[0].toString() == "4") {
                    val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 50)
                    tone.startTone(ToneGenerator.TONE_DTMF_C, 500)
                }
                Thread.sleep(250)
                while (msgString[0].toString() != "9") {
                    run()
                    //manageConnectedSocket(socket)
                }
                //Thread.sleep(3000)


            } catch (uee: UnsupportedEncodingException) {
                Log.e(TSERVER,
                    "UnsupportedEncodingException when converting bytes to String\n $uee")
            } finally {
                cancel()        //for this App - close() after 1 (or no) message received
            }
        }

        /**
         * Will cancel the listening socket, and cause the thread to finish
         */
        fun cancel() {
            try {
                mmServerSocket!!.close()
            } catch (ioe: IOException) {
                Log.e(TSERVER, "IOException when canceling serverSocket\n $ioe")
            }
        }
    }

    companion object {
        private const val ACCESS_FINE_LOCATION = 1
        private const val N_SECONDS = 255
        private const val TCLIENT = "--Talker Client--"  //for Log.X
        private const val TSERVER = "--Talker SERVER--"  //for Log.X
        private const val REQUEST_ENABLE_BT = 3313  //our own code used with Intents
        private const val MY_UUID_STRING = "12ce62cb-60a1-4edf-9e3a-ca889faccd6c"
        //from www.uuidgenerator.net
        private const val SERVICE_NAME = "Talker"
        private const val LOG_TAG = "--Talker----"
    }





}
