package com.example.bluetooth.le.activity

import android.Manifest
import android.app.Dialog
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.bluetooth.le.*
import com.example.bluetooth.le.utilInfo.GetOTAAddrTask
import com.example.bluetooth.le.utilInfo.SendOTAFileTask
import com.example.bluetooth.le.utilInfo.Utils
import kotlinx.android.synthetic.main.activity_ota_update.*
import kotlinx.android.synthetic.main.viewpager_two.view.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class OTAUpdateActivity : BaseActivity() {

    private lateinit var precenttv: TextView
    private lateinit var mDialog : Dialog
    private var getOTAAddrTask: GetOTAAddrTask? = null
    private var sendOTAFileTask: SendOTAFileTask? = null
    private var isGetOTAAddr = false
    private var isSendOTAFile = false
    private var selectFile: File? = null
    private var everyPackageSize = 0
    private var fileLength = 0L
    private lateinit var woperation : WriterOperation
    private lateinit var selectServer : UUIDInfo
    private lateinit var selectWrite : UUIDInfo
    private lateinit var selectRead : UUIDInfo
    var isConnected = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ota_update)

        imgBack.setOnClickListener { onBackPressed() }

        initUI()
        verifyStoragePermissions()



        // 断开或连接 状态发生变化时调用
        DeviceScanActivity.getInstance().mBLE.setOnConnectListener(OnConnectListener)
        // 发现BLE终端的Service时回调
        DeviceScanActivity.getInstance().mBLE.setOnServiceDiscoverListener(mOnServiceDiscover)
        // 读操作的回调
        DeviceScanActivity.getInstance().mBLE.setOnDataAvailableListener(OnDataAvailableListener)
        // 写操作的回调
        DeviceScanActivity.getInstance().mBLE.setOnWriteDataListener(OnWriteDataListener)
        // 接收到硬件返回的数据
        DeviceScanActivity.getInstance().mBLE.setOnRecvDataListener(OnRecvDataListerner)
        DeviceScanActivity.getInstance().mBLE.setOnChangeMTUListener(onChangeMTUListener)

        startGetServerTimer(true)

//        serverList = TRXActivity.getInstance().serverList
//        writeCharaMap = TRXActivity.getInstance().writeCharaMap
//        readCharaMap = TRXActivity.getInstance().readCharaMap
//        bindServerSubNofify()

    }



    private fun initUI() {

        val mAdapterManager = AdapterManager(this)
        BluetoothApplication.getInstance().adapterManager = mAdapterManager
        verifyStoragePermissions()
        woperation = WriterOperation()

        val layoutinflater = LayoutInflater.from(this)
        val view = layoutinflater.inflate(R.layout.loading_process_dialog_anim, null)
        precenttv = view.findViewById<View>(R.id.precenttv) as TextView
        mDialog = Dialog(this, R.style.dialog)
        mDialog.setCancelable(false)
        mDialog.setContentView(view)

        imgBack.setOnClickListener { finish() }
        tvFilePath.setOnClickListener{
            var strFilePath = tvFilePath.text.toString()
            if (strFilePath.equals("请选择 bin 文件")) strFilePath = ""
            val intent = Intent(mContext, SelectFileActivity::class.java)
            intent.putExtra("filepatch", strFilePath)
            startActivityForResult(intent, SelectFileActivity.RESULT_CODE)
        }
        btnUpdate.setOnClickListener{
            val strFilePath = tvFilePath.text.toString()
            startUpdateBT(strFilePath)
        }

    }

    private var getServerTimer = Timer()
    private fun startGetServerTimer(isRun: Boolean) {
        getServerTimer.cancel()
        if (isRun) {
            getServerTimer = Timer()
            getServerTimer.schedule(object : TimerTask() {
                override fun run() {
                    val getResult = DeviceScanActivity.getInstance().mBLE.getServiceByGatt()
                    if (!getResult) {

                    }
                }
            }, 0, 5000)
        }

    }

    var serverList = ArrayList<UUIDInfo>()
    var readCharaMap = HashMap<String, ArrayList<UUIDInfo>>()
    var writeCharaMap = HashMap<String, ArrayList<UUIDInfo>>()
    /**
     * 搜索到BLE终端服务的事件
     */
    private val mOnServiceDiscover = BluetoothLeClass.OnServiceDiscoverListener {
        Log.e("onConnected", "mOnServiceDiscover: ${it.services.size}")

        startGetServerTimer(false)
        val gattlist = it.services
        serverList.clear()
        readCharaMap.clear()
        writeCharaMap.clear()
        for (bluetoothGattService in gattlist) {
            val serverInfo = UUIDInfo(bluetoothGattService.uuid)
            serverInfo.strCharactInfo = "[Server]"
            serverList.add(serverInfo)
            val readArray = ArrayList<UUIDInfo>()
            val writeArray = ArrayList<UUIDInfo>()
            val characteristics = bluetoothGattService.characteristics
            for (characteristic in characteristics) {
                val charaProp = characteristic.properties
                var isRead = false
                var isWrite = false
                // 具备读的特征
                var strReadCharactInfo = ""
                // 具备写的特征
                var strWriteCharactInfo = ""
                if (charaProp and BluetoothGattCharacteristic.PROPERTY_READ > 0) {
                    isRead = true
                    strReadCharactInfo += "[PROPERTY_READ]"
                    Log.e(ContentValues.TAG, "read_chara=" + characteristic.uuid + "----read_service=" + bluetoothGattService.uuid)
                }
                if (charaProp and BluetoothGattCharacteristic.PROPERTY_WRITE > 0) {
                    isWrite = true
                    strWriteCharactInfo += "[PROPERTY_WRITE]"
                    Log.e(ContentValues.TAG, "write_chara=" + characteristic.uuid + "----write_service=" + bluetoothGattService.uuid)
                }
                if (charaProp and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE > 0) {
                    isWrite = true
                    strWriteCharactInfo += "[PROPERTY_WRITE_NO_RESPONSE]"
                    Log.e(ContentValues.TAG, "write_chara=" + characteristic.uuid + "----write_service=" + bluetoothGattService.uuid)
                }
                if (charaProp and BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0) {
                    isRead = true
                    strReadCharactInfo += "[PROPERTY_NOTIFY]"
                    Log.e(ContentValues.TAG, "notify_chara=" + characteristic.uuid + "----notify_service=" + bluetoothGattService.uuid)
                }
                if (charaProp and BluetoothGattCharacteristic.PROPERTY_INDICATE > 0) {
                    isRead = true
                    strReadCharactInfo += "[PROPERTY_INDICATE]"
                    Log.e(ContentValues.TAG, "indicate_chara=" + characteristic.uuid + "----indicate_service=" + bluetoothGattService.uuid)
                }
                if (isRead) {
                    val uuidInfo = UUIDInfo(characteristic.uuid)
                    uuidInfo.strCharactInfo = strReadCharactInfo
                    uuidInfo.bluetoothGattCharacteristic = characteristic
                    readArray.add(uuidInfo)
                }
                if (isWrite) {
                    val uuidInfo = UUIDInfo(characteristic.uuid)
                    uuidInfo.strCharactInfo = strWriteCharactInfo
                    uuidInfo.bluetoothGattCharacteristic = characteristic
                    writeArray.add(uuidInfo)
                }
                readCharaMap.put(bluetoothGattService.uuid.toString(), readArray)
                writeCharaMap.put(bluetoothGattService.uuid.toString(), writeArray)
            }
        }



        bindServerSubNofify()
    }

    private fun bindServerSubNofify() {



        for (iPosition in 0 until serverList.size) {
            val serverUUID = serverList[iPosition]
            if (serverUUID.uuidString.equals(strOTA_Server,true)) {
                selectServer = serverUUID
                val readArray = readCharaMap[selectServer.uuidString]
                for (iR in 0 until readArray!!.size) {
                    if (readArray[iR].uuidString.equals(strOTA_Read,true)) {
                        selectRead = readArray[iR]
                        break
                    }
                }
                val writeArray = writeCharaMap[selectServer.uuidString]
                for (iW in 0 until writeArray!!.size) {
                    if (writeArray[iW].uuidString.equals(strOTA_Write,true)) {
                        selectWrite = writeArray[iW]
                        break
                    }
                }
                break
            }
        }

        if (!this::selectServer.isInitialized || selectServer == null ||
                !this::selectRead.isInitialized || selectRead == null ||
                !this::selectWrite.isInitialized || selectWrite == null) {
            showToast("未找到指定升级通道！")
            btnUpdate.isEnabled = false
            return
        }


        // 订阅通知
        val isNotification = DeviceScanActivity.getInstance().mBLE.setCharacteristicNotification(selectRead?.bluetoothGattCharacteristic, true)
        Log.e("TestDataActivity", "isNotification:$isNotification")
        updateLog("subscribe Notification:$isNotification")
        if (isNotification) {
            val descriptors: List<BluetoothGattDescriptor> = selectRead?.bluetoothGattCharacteristic!!.descriptors
            for (descriptor in descriptors) {
                // 读写开关操作，writeDescriptor 否则可能读取不到数据。
                val b1 = descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                if (b1) {
                    val isB = DeviceScanActivity.getInstance().mBLE.writeDescriptor(descriptor)
                    Log.e(ContentValues.TAG, "startRead: " + "监听收数据")
                    updateLog("writeDescriptor:$isB")
                }
            }
        }

    }

    private val REQUEST_EXTERNAL_STORAGE = 1
    private val PERMISSIONS_STORAGE = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS)

    fun verifyStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_EXTERNAL_STORAGE) {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "存储权限已打开", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "需要打开存储权限才可以OTA", Toast.LENGTH_SHORT).show()
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == SelectFileActivity.RESULT_CODE) {
            // 请求为 "选择文件"
            try {
                // 取得选择的文件名
                val sendFileName = data?.getStringExtra(SelectFileActivity.SEND_FILE_NAME)
                if (sendFileName != null && !sendFileName.equals(""))
                    tvFilePath.text = sendFileName
            } catch (e: Exception) {
                Log.e(ContentValues.TAG, "onActivityResult: " + "回调异常！")
                tvFilePath.text = "文件异常：${e.message}"
            }
        }
    }

    // 断开或连接 状态发生变化时调用
    private val OnConnectListener = object : BluetoothLeClass.OnConnectListener {
        override fun onConnected(gatt: BluetoothGatt?, status: Int, newState: Int) {
            isConnected = true
            runOnUiThread {
                updateLog("Connected to GATT server.")
            }
        }
        override fun onDisconnect(gatt: BluetoothGatt?, status: Int, newState: Int) {
            isConnected = false
            runOnUiThread {
                updateLog("Disconnected from GATT server.")
            }
        }
        override fun onConnectting(gatt: BluetoothGatt?, status: Int, newState: Int) {
            isConnected = false
            runOnUiThread {
                updateLog("Connectting to GATT...")
            }

        }
    }

    // 读操作的回调
    private val OnDataAvailableListener = object : BluetoothLeClass.OnDataAvailableListener {
        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
        }
    }

    // 写操作的回调
    private val OnWriteDataListener = object : BluetoothLeClass.OnWriteDataListener {
        override fun OnCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            Log.e("OnWriteDataListener", "writeStatus:${status == 0})")
            // 如果没在升级，则打印写的结果
        }
    }

    // 接收到硬件返回的数据
    private val OnRecvDataListerner = object : BluetoothLeClass.OnRecvDataListerner {
        override fun OnCharacteristicRecv(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            if (characteristic!!.value.size == 0) return
            // 正在获取位置信息
            if (isGetOTAAddr) {
                getOTAAddrTask?.recvValue = characteristic.value
                getOTAAddrTask?.isResultCallBack = true
                return
            }
            // 正在发文件
            if (isSendOTAFile) {
                sendOTAFileTask?.recvValue = characteristic.value
                sendOTAFileTask?.isResultCallBack = true
                return
            }


        }
    }


    /** MTU改变监听 */
    private val onChangeMTUListener = object : BluetoothLeClass.OnChangeMTUListener {
        override fun onChangeMTUListener(isResult: Boolean?, strMsg: String?, iMTU: Int) {
            runOnUiThread {
                updateLog(strMsg!!)
            }

            Log.e("onChangeMTUListener", "MTU设置结果：$strMsg")
            // 正在升级
            if (mDialog.isShowing) {
                everyPackageSize = iMTU - 3 - 9
                // 启动关于地址的线程(activity,发送帮助类，写事件，文件长度)
                isGetOTAAddr = true
                getOTAAddrTask = GetOTAAddrTask()
                getOTAAddrTask?.execute(handler, woperation, selectWrite, fileLength)
            }
        }
    }

    /** 开始升级
     *  1,设置MTU
     *  2，查询存储地址
     *  3，擦除升级文件大小相应的空间
     *  4，发送文件
     *  5，发送完毕，并发重启
     *  6，自动断开连接
     * */
    private fun startUpdateBT(strFilePath: String) {
        if (strFilePath.equals("请选择 bin 文件")) return
        selectFile = File(strFilePath.trim())
        fileLength = selectFile!!.length()
        updateLog("大小：${Utils.formatFileSize(fileLength)}")
        // 校验文件
//        if (!checkFileOK(file)) return
        tvLog.text = ""
        updateLog("!!!!!!!!!!开始升级!!!!!!!!!!\n文件：$strFilePath")
        mDialog.show()
        Log.e("doSendFileByBluetooth", "设置MTU:512")
        DeviceScanActivity.getInstance().mBLE.requestMtu(512)
    }

    /** 开始发送文件 */
    private fun startSendFile(iStartAddr: Int) {
        isGetOTAAddr = false
        isSendOTAFile = true
        sendOTAFileTask = SendOTAFileTask()
        sendOTAFileTask?.execute(handler, woperation, selectWrite, selectFile,iStartAddr,everyPackageSize)
    }

    val iUpdateLog = 111
    val iUpdateStop = 222
    val iRecvLog = 333
    val iSendFile = 444
    val handler = object : Handler() {
        override fun dispatchMessage(msg: Message) {
            super.dispatchMessage(msg)
            when(msg.what) {
                iUpdateLog -> {
                    val writePrecent = (msg.obj).toString()
                    precenttv.text = writePrecent
                    updateLog(writePrecent)
                }
                iUpdateStop -> {
                    mDialog.cancel()
                    isGetOTAAddr = false
                    isSendOTAFile = false
                    getOTAAddrTask?.cancel(true)
                    sendOTAFileTask?.cancel(true)
                    val strMsg = (msg.obj).toString()
                    updateLog(strMsg)
                }
                iRecvLog -> {
                    val strMsg = (msg.obj).toString()
                    updateLog(strMsg)
                }
                iSendFile -> {
                    val iStartAddr = msg.obj.toString().toInt()
                    startSendFile(iStartAddr)
                }
            }

        }

    }


    private fun updateLog(strValue: String) {
        val date = Date()
        val dateFormat = SimpleDateFormat("HH:mm:ss:SSS")
        tvLog.text =  tvLog.text.toString().plus("\n${dateFormat.format(date)}::::$strValue")
        scrollView.fullScroll(ScrollView.FOCUS_DOWN)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        startGetServerTimer(false)
        DeviceScanActivity.getInstance().mBLE.disconnect()

//        val intent0 = Intent(mContext, DeviceScanActivity::class.java)
//        intent0.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
//        startActivity(intent0)

    }




}