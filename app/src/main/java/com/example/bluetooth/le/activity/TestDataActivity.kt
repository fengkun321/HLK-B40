package com.example.bluetooth.le.activity

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.app.ProgressDialog
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.annotation.RequiresApi
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.bluetooth.le.*
import com.example.bluetooth.le.BluetoothLeClass.OnServiceDiscoverListener
import com.example.bluetooth.le.utilInfo.Utils
import com.example.bluetooth.le.adapter.MyPagerAdapter
import com.example.bluetooth.le.adapter.MySpinnerAdapter
import kotlinx.android.synthetic.main.activity_testdata.*
import kotlinx.android.synthetic.main.viewpager_one.view.*
import kotlinx.android.synthetic.main.viewpager_two.*
import kotlinx.android.synthetic.main.viewpager_two.view.*
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlinx.android.synthetic.main.viewpager_two.view.scrollView as scrollView1
import kotlinx.android.synthetic.main.viewpager_two.view.tvLog as tvLog1


class TestDataActivity : Activity(),View.OnClickListener{

    private lateinit var view1: View
    private lateinit var view2: View
    private var pagerList = arrayListOf<View>()
    private lateinit var myReadAdapter : MySpinnerAdapter
    private lateinit var myWriteAdapter : MySpinnerAdapter
    private lateinit var selectServer : UUIDInfo
    private lateinit var selectWrite : UUIDInfo
    private lateinit var selectRead : UUIDInfo
    var readArray = ArrayList<UUIDInfo>()
    var writeArray = ArrayList<UUIDInfo>()
    var iSendLength = 0
    var iRecvLength = 0
    var isConnected = true
    private lateinit var woperation : WriterOperation
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_testdata)

        initUI()
        initData()
        // 注册广播
        reciverBand()

        val mAdapterManager = AdapterManager(this)
        BluetoothApplication.getInstance().adapterManager = mAdapterManager
        verifyStoragePermissions()
        woperation = WriterOperation()

    }

    fun initUI() {

        getServerDialog = ProgressDialog(this)
        getServerDialog.setOnDismissListener {
            startGetServerTimer(false)
        }

        val mInflater = layoutInflater
        view1 = mInflater.inflate(R.layout.viewpager_one, null)
        view2 = mInflater.inflate(R.layout.viewpager_two, null)

        pagerList.add(view1!!)
        pagerList.add(view2!!)
        viewPager.adapter = MyPagerAdapter(pagerList)
        viewPager.currentItem = 0

        imgBack.setOnClickListener {
            onBackPressed()
        }
        tvState.setOnClickListener{
            onBackPressed()
        }
        tvMTU.setOnClickListener(this)
        view2.tvClear.setOnClickListener(this)
        view1.tvRight.setOnClickListener(this)
        view1.btnRead.setOnClickListener(this)
        view1.btnNotify.setOnClickListener(this)

        view2.tvLeft.setOnClickListener(this)
        view2.cbAutoSend.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                iSendSpeed = 1000
                if (!edSpeed.text.toString().equals(""))
                    iSendSpeed = edSpeed.text.toString().toLong()
                strSendData = edData.text.toString()
                if (!strSendData.equals(""))
                    SendTimer(true)
            }
            else
                SendTimer(false)
        }
        view2.rbStr.isChecked = true
        view2.btnSend.setOnClickListener(this)
        view2.tvFilePath.setOnClickListener(this)
        view2.btnUpdate.setOnClickListener(this)

    }
    lateinit var getServerDialog:ProgressDialog
    /** 初始化数据 */
    private fun initData() {
        tvName.text = DeviceScanActivity.getInstance().nowSelectDevice.name
        tvMac.text = DeviceScanActivity.getInstance().nowSelectDevice.address

        val list: ArrayList<String> = ArrayList()
        for (i in 0..29) {
            list.add("Name$i")
        }

        view1.spinnerServer.setOnItemSelectedListener(onItemSelectedListener)
        view1.spinnerWrite.setOnItemSelectedListener(onItemWriteListener)
        view1.spinnerRead.setOnItemSelectedListener(onItemReadListener)

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

        view2.tvSendLen.text = "发送:$iSendLength"
        view2.tvRecvLen.text = "接收:$iRecvLength"

        getServerDialog.setTitle("获取服务中")
        getServerDialog.setMessage("获取服务信息,请稍后...")
        getServerDialog.show()
        startGetServerTimer(true)
    }

    private var getServerTimer = Timer()
    private fun startGetServerTimer(isRun:Boolean) {
        getServerTimer.cancel()
        if (isRun) {
            getServerTimer = Timer()
            getServerTimer.schedule(object : TimerTask(){
                override fun run() {
                    val getResult = DeviceScanActivity.getInstance().mBLE.getServiceByGatt()
                    if (!getResult) {
                        runOnUiThread {
                            getServerDialog.dismiss()
                            Toast.makeText(this@TestDataActivity,"getServiceByGatt:$getResult",Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },0,5000)
        }

    }

    // 断开或连接 状态发生变化时调用
    private val OnConnectListener = object : BluetoothLeClass.OnConnectListener {
        override fun onConnected(gatt: BluetoothGatt?, status: Int, newState: Int) {
//            updateLog("Connected to GATT server.")
            isConnected = true
            var msgStop = Message()
            msgStop.what = iUpdateStop
            msgStop.obj = "Connected to GATT server."
            mHandler.sendMessage(msgStop)
        }
        override fun onDisconnect(gatt: BluetoothGatt?, status: Int, newState: Int) {
            SendTimer(false)
//            updateLog("Disconnected from GATT server.")
            isConnected = false
            var msgStop = Message()
            msgStop.what = iUpdateStop
            msgStop.obj = "Disconnected from GATT server."
            mHandler.sendMessage(msgStop)
        }
        override fun onConnectting(gatt: BluetoothGatt?, status: Int, newState: Int) {
//            updateLog("Connectting to GATT...")
            var msgStop = Message()
            msgStop.what = iUpdateStop
            msgStop.obj = "Connectting to GATT..."
            mHandler.sendMessage(msgStop)
        }
    }

    var serverList = ArrayList<UUIDInfo>()
    var readCharaMap = HashMap<String, ArrayList<UUIDInfo>>()
    var writeCharaMap = HashMap<String, ArrayList<UUIDInfo>>()

    /**
     * 搜索到BLE终端服务的事件
     */
    private val mOnServiceDiscover = OnServiceDiscoverListener {
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
            val readArray = java.util.ArrayList<UUIDInfo>()
            val writeArray = java.util.ArrayList<UUIDInfo>()
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
                    Log.e(TAG, "read_chara=" + characteristic.uuid + "----read_service=" + bluetoothGattService.uuid)
                }
                if (charaProp and BluetoothGattCharacteristic.PROPERTY_WRITE > 0) {
                    isWrite = true
                    strWriteCharactInfo += "[PROPERTY_WRITE]"
                    Log.e(TAG, "write_chara=" + characteristic.uuid + "----write_service=" + bluetoothGattService.uuid)
                }
                if (charaProp and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE > 0) {
                    isWrite = true
                    strWriteCharactInfo += "[PROPERTY_WRITE_NO_RESPONSE]"
                    Log.e(TAG, "write_chara=" + characteristic.uuid + "----write_service=" + bluetoothGattService.uuid)
                }
                if (charaProp and BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0) {
                    isRead = true
                    strReadCharactInfo += "[PROPERTY_NOTIFY]"
                    Log.e(TAG, "notify_chara=" + characteristic.uuid + "----notify_service=" + bluetoothGattService.uuid)
                }
                if (charaProp and BluetoothGattCharacteristic.PROPERTY_INDICATE > 0) {
                    isRead = true
                    strReadCharactInfo += "[PROPERTY_INDICATE]"
                    Log.e(TAG, "indicate_chara=" + characteristic.uuid + "----indicate_service=" + bluetoothGattService.uuid)
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
        var strServerCharact = "***********Gatt Server Info***********"
        var iServerCount = 0
        serverList.forEach {
            ++iServerCount
            strServerCharact += ("\n$iServerCount.Server:\n${it.uuidString},${it.strCharactInfo}")
            val writeArray = writeCharaMap[it.uuidString]
            val readArray = readCharaMap[it.uuidString]
            strServerCharact += ("\nWrite:")
            writeArray?.forEach { itW ->
                strServerCharact += ("\n${itW.uuidString},${itW.strCharactInfo}")
            }
            strServerCharact += ("\nRead:")
            readArray?.forEach { itR ->
                strServerCharact += ("\n${itR.uuidString},${itR.strCharactInfo}")
            }
        }
        view1.tvLog.text = ""
        updateLogServer(strServerCharact)

        runOnUiThread {
            getServerDialog.dismiss()
            val myAdapter = MySpinnerAdapter(serverList, this, true)
            view1.spinnerServer.setAdapter(myAdapter)
        }

    }

    // 读操作的回调
    private val OnDataAvailableListener = object : BluetoothLeClass.OnDataAvailableListener {
        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            val strStatus = BluetoothLeClass.strResultInfoByStatus(status)
            updateLogServer("Read:$strStatus")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic!!.value.size == 0) return
                iRecvLength += characteristic!!.value.size
                var string = ""
                // 字符
                if (view2.rbStr.isChecked)
                    string = String(characteristic!!.value)
                // 十六进制
                else
                    string = Utils.bytesToHexString(characteristic!!.value)

                view2.tvRecvLen.text = "接收:$iRecvLength"
                updateLogServer("RecvData<<<$string,Len:${characteristic!!.value.size}")
            }
        }
    }

    // 写操作的回调
    private val OnWriteDataListener = object : BluetoothLeClass.OnWriteDataListener {
        override fun OnCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            writeStatus = (status == 0)
            Log.e("OnWriteDataListener","writeStatus:$writeStatus")
            // 如果没在升级，则打印写的结果
            if (!isStartUpdateOTA) {
                val strStatus = BluetoothLeClass.strResultInfoByStatus(status)
                var msgStart = Message()
                msgStart.what = iRecvLog
                msgStart.obj = "Write:$strStatus"
                mHandler.sendMessage(msgStart)
                return
            }
        }
    }

    // 接收到硬件返回的数据
    private val OnRecvDataListerner = object : BluetoothLeClass.OnRecvDataListerner {
        override fun OnCharacteristicRecv(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            if (characteristic!!.value.size == 0) return
            resultUpdateValue = characteristic.value
            isResultCallBack = true
            if (isStartUpdateOTA) {
                Log.e("OnRecvDataListerner","isResultCallBack:$isResultCallBack")
                return
            }

            Log.e("OnRecvDataListerner","RECVDATA,to HEX:${Utils.bytesToHexString(characteristic!!.value)},to STR:${String(characteristic!!.value)}")

            iRecvLength += characteristic!!.value.size
            var string = ""
            // 字符
            if (view2.rbStr.isChecked)
                string = String(characteristic!!.value)
            // 十六进制
            else
                string = Utils.bytesToHexString(characteristic!!.value)

            var msgStart = Message()
            msgStart.what = iRecvLog
            msgStart.obj = "RecvData<<<$string,Len:${characteristic!!.value.size}"
            mHandler.sendMessage(msgStart)

        }
    }



    /** 选项改变监听事件 */
    private val onItemSelectedListener = object : OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            if (serverList.size == 0) return
            selectServer = serverList[position]
            val strServerUUID = serverList[position].uuidString
//                Toast.makeText(this@TestDataActivity, "$strServerUUID", Toast.LENGTH_SHORT).show()
            var wArray = writeCharaMap[strServerUUID]
            var rArray = readCharaMap[strServerUUID]
            if (wArray == null) wArray = ArrayList<UUIDInfo>()
            if (rArray == null) rArray = ArrayList<UUIDInfo>()
            writeArray = wArray
            readArray = rArray
            myWriteAdapter = MySpinnerAdapter(writeArray, this@TestDataActivity, false)
            view1.spinnerWrite.setAdapter(myWriteAdapter)
            myReadAdapter = MySpinnerAdapter(readArray, this@TestDataActivity, false)
            view1.spinnerRead.setAdapter(myReadAdapter)
        }
        override fun onNothingSelected(parent: AdapterView<*>) {
            Log.e("TAG_MainActivity", parent.toString())
        }
    }

    /** 选项改变监听事件 */
    private val onItemWriteListener = object : OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            if (writeArray.size == 0) return
            selectWrite = writeArray[position]
        }
        override fun onNothingSelected(parent: AdapterView<*>?) {
            Log.e("TAG_MainActivity", parent.toString())
        }
    }
    /** 选项改变监听事件 */
    private val onItemReadListener = object : OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            if (readArray.size == 0) return
            selectRead = readArray[position]
        }
        override fun onNothingSelected(parent: AdapterView<*>?) {
            Log.e("TAG_MainActivity", parent.toString())
        }
    }

    /**
     * 注册广播
     */
    private fun reciverBand() {
        val myIntentFilter = IntentFilter()
        // MTU变化的回调
        myIntentFilter.addAction("onMtuChanged")
        // 注册广播
        registerReceiver(mBroadcastReceiver, myIntentFilter)
    }

    override fun onClick(v: View?) {
        when(v?.id) {
            R.id.tvMTU -> {
                // api 小于21，不支持修改MTU
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    Toast.makeText(this,"Android 系统版本过低！不支持该功能！",Toast.LENGTH_SHORT).show()
                    return
                }
                val strMtu = tvMTU.text.toString().split("*")
                AreaAddWindowSetMTU(this,R.style.dialog,object : AreaAddWindowSetMTU.PeriodListener{
                    override fun refreshListener(oldPwd: String) {
                        DeviceScanActivity.getInstance().mBLE.requestMtu(oldPwd.toInt())
                    }
                    override fun clearListener() {
                    }
                },strMtu[1]).show()
            }
            R.id.tvClear -> {
                tvLog.text = ""
                iSendLength = 0
                iRecvLength = 0
                view2.tvSendLen.text = "发送:$iSendLength"
                view2.tvRecvLen.text = "接收:$iRecvLength"
            }
            R.id.btnRead -> {
                val isRead = DeviceScanActivity.getInstance().mBLE.readCharacteristic(selectRead.bluetoothGattCharacteristic)
                Log.e("TestDataActivity","isRead:$isRead")

            }
            R.id.btnNotify -> {
                val isNotification = DeviceScanActivity.getInstance().mBLE.setCharacteristicNotification(selectRead.bluetoothGattCharacteristic,true)
                Log.e("TestDataActivity","isNotification:$isNotification")

                updateLogServer("subscribe Notification:$isNotification")
                if (isNotification) {
                    val descriptors: List<BluetoothGattDescriptor> = selectRead.bluetoothGattCharacteristic.getDescriptors()
                    for (descriptor in descriptors) {
                        // 读写开关操作，writeDescriptor 否则可能读取不到数据。
                        val b1 = descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        if (b1) {
                            val isB = DeviceScanActivity.getInstance().mBLE.writeDescriptor(descriptor)
                            Log.e(TAG, "startRead: " + "监听收数据")
                            updateLogServer("writeDescriptor:$isB")
                        }
                    }

                }
            }
            R.id.tvRight -> viewPager.currentItem = 1
            R.id.tvLeft -> viewPager.currentItem = 0
            R.id.btnSend -> {
                val strSend = edData.text.toString()
                if (strSend.equals("")) return
                startSendData(strSend)
            }
            R.id.tvFilePath -> {
                var strFilePath = tvFilePath.text.toString()
                if (strFilePath.equals("请选择 bin 文件")) strFilePath = ""
                val intent = Intent(this@TestDataActivity, SelectFileActivity::class.java)
                intent.putExtra("filepatch", strFilePath)
                startActivityForResult(intent,SelectFileActivity.RESULT_CODE)
            }
            R.id.btnUpdate -> {
                val strFilePath = tvFilePath.text.toString()
                startUpdateBT(strFilePath)
            }


        }
    }

    private var sendTimer = Timer()
    private var iSendSpeed : Long = 1000
    private var strSendData = ""
    private fun SendTimer(isRun:Boolean) {
        sendTimer.cancel()
        if (isRun) {
            sendTimer = Timer()
            sendTimer.schedule(object : TimerTask(){
                override fun run() {
                    startSendData(strSendData)
                }
            },0,iSendSpeed)
        }

    }

    private fun startSendData(strSend: String) {
        var mgattCharacteristic = selectWrite.bluetoothGattCharacteristic
        mgattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        // 字符发送
        if (rbStr.isChecked) {
            mgattCharacteristic.setValue(strSend.toByteArray())
            iSendLength += strSend.length
            tvSendLen.text = "发送:$iSendLength"
        }
        // 十六进制发送
        else {
            mgattCharacteristic.setValue(Utils.hexStringToBytes(strSend))
            iSendLength += (strSend.length/2)
            tvSendLen.text = "发送:$iSendLength"
        }

        updateLog("SendData>>>$strSend")

        DeviceScanActivity.getInstance().mBLE.writeCharacteristic(mgattCharacteristic)

    }

    private val mBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            // 断开或连接 状态发生变化时调用
            if (action == "onConnectionStateChange") {

            }
            // 读操作的回调
            else if (action == "onCharacteristicRead") {

            }
            // 写操作的回调
            else if (action == "onCharacteristicWrite") {

            }
            // 接收到硬件返回的数据
            else if (action == "onCharacteristicChanged") {

            }
            // MTU变化的回调
            else if (action == "onMtuChanged") {
                val strValue = intent.getStringExtra("onMtuChanged")
                updateLogServer(strValue)
                updateLog(strValue)
                tvMTU.text = "MTU*${DeviceScanActivity.getInstance().mBLE.mtuSize}"
                isUpdateMTUCallBack = true
            }
        }
    }

    private fun updateLogServer(strValue:String) {
        if (strValue.equals("")) return
        val date = Date()
        val dateFormat = SimpleDateFormat("HH:mm:ss:SSS")
        // 主线程操作
        runOnUiThread {
            view1.tvLog.text =  view1.tvLog.text.toString().plus("\n${dateFormat.format(date)}::::${strValue}")
            view1.scrollView.fullScroll(ScrollView.FOCUS_DOWN);
        }
    }

    private fun updateLog(strValue:String) {
        if (strValue.equals("")) return
        var msgUpdate = Message()
        msgUpdate.what = iRecvLog
        msgUpdate.obj = strValue
        mHandler.sendMessage(msgUpdate)
    }

    private fun updateLog2(strValue:String) {
        val date = Date()
        val dateFormat = SimpleDateFormat("HH:mm:ss:SSS")
        view2.tvLog.text =  view2.tvLog.text.toString().plus("\n${dateFormat.format(date)}::::$strValue")
        view2.scrollView.fullScroll(ScrollView.FOCUS_DOWN)
    }

    private val REQUEST_EXTERNAL_STORAGE = 1
    private val PERMISSIONS_STORAGE = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS)

    fun verifyStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(PERMISSIONS_STORAGE,REQUEST_EXTERNAL_STORAGE)
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
                Log.e(TAG, "onActivityResult: " + "回调异常！")
                tvFilePath.text = "文件异常：${e.message}"
            }
        }
    }

    private lateinit var precenttv: TextView
    private lateinit var mDialog : Dialog
    private fun showDialog() {
        val layoutinflater = LayoutInflater.from(this)
        val view = layoutinflater.inflate(R.layout.loading_process_dialog_anim, null)
        precenttv = view.findViewById<View>(R.id.precenttv) as TextView
        mDialog = Dialog(this, R.style.dialog)
        mDialog.setCancelable(false)
        mDialog.setContentView(view)
        mDialog.show()
    }

    /** 开始升级 */
    private fun startUpdateBT(strFilePath:String) {
        if (strFilePath.equals("请选择 bin 文件")) return
        val file = File(strFilePath.trim())
        // 校验文件
//        if (!checkFileOK(file)) return
        tvLog.text = ""
        updateLog("!!!!!!!!!!开始升级!!!!!!!!!!\n文件：$strFilePath")
        showDialog()
//         起一个线程，开始升级
        Thread(Runnable {
            try {
                doSendFileByBluetooth(file)
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            }
        }).start()
    }

    /**
     * 校验文件
     */
    private fun checkFileOK(file:File) : Boolean {
        var isOK = true
        if (file.length() < 100) {
            Toast.makeText(this@TestDataActivity, "请选择有效的配置文件",Toast.LENGTH_LONG).show()
            isOK = false
        }
        val infile = FileInputStream(file)
        val input = BufferedInputStream(infile)
        var Buffer = ByteArray(62)
        input.read(Buffer, 0, Buffer.size)
        if (Buffer[58].toInt() != 0x51 || Buffer[59].toInt() != 0x52 || Buffer[60].toInt() != 0x52 || Buffer[61].toInt() != 0x51) {
            Toast.makeText(this@TestDataActivity, "请选择正确的文件", Toast.LENGTH_LONG).show()
            isOK = false
        }
        input.close()
        infile.close()
        return isOK
    }

    private lateinit var isfile: FileInputStream
    private lateinit var input : BufferedInputStream
    private var leng = 0L
    private var isUpdateMTUCallBack = false
    private var isStartUpdateOTA = false
    private var isResultCallBack = false
    private var writeStatus = false
    private var resultUpdateValue: ByteArray? = null
    private val sencondaddr = 0x14000
    private val firstaddr = 0
    private var lastProgress : Float = 0f

    fun doSendFileByBluetooth(file:File) {
        var read_count: Int
        var i = 0
        var addr: Int // 存储的起始地址
        var lastReadCount = 0
        var packageSize = 235 //bleclass.mtuSize - 3; //235;
        var send_data_count = 0
        isfile = FileInputStream(file)
        leng = file.length()
        input = BufferedInputStream(isfile)
        updateLog("大小：${Utils.formatFileSize(leng)}")
        // 先设置mtu
        isUpdateMTUCallBack = false
        Log.e("doSendFileByBluetooth","设置MTU:512")
        DeviceScanActivity.getInstance().mBLE.requestMtu(512)
        while (!isUpdateMTUCallBack) {
            if (!checkConnectState()) {
                return
            }
        }

        packageSize = DeviceScanActivity.getInstance().mBLE.mtuSize - 3 - 9
        Log.e("doSendFileByBluetooth","实际MTU:${DeviceScanActivity.getInstance().mBLE.mtuSize},包长：${packageSize}")
        var inputBuffer = ByteArray(packageSize)
        // 再获取当前升级程序的存储起始地址
        isResultCallBack = false
        woperation.send_data(WriterOperation.OTA_CMD_GET_STR_BASE, 0, null, 0,
                selectWrite.bluetoothGattCharacteristic, DeviceScanActivity.getInstance().mBLE)
        while (!isResultCallBack) {
            if (!checkConnectState()) {
                return
            }
        }
        addr = woperation.bytetoint(resultUpdateValue)
        Log.e("doSendFileByBluetooth","BLE升级的起始位置：$addr")
        updateLog("BLE升级的起始位置：$addr")
        // 按照上面的起始地址和文件大小，计算具体需要发多少数据，并擦除模块对应的升级内存空间
        page_erase(addr, leng, selectWrite.bluetoothGattCharacteristic, DeviceScanActivity.getInstance().mBLE)
        try {
            Log.e("doSendFileByBluetooth","开始写入：0%")
            lastProgress = 0f
            var msgStart = Message()
            msgStart.what = iUpdateLog
            msgStart.obj = "已写入..$lastProgress%"
            mHandler.sendMessage(msgStart)
            isStartUpdateOTA = true
            // 最后根据MTU的传输长度，开始发送升级文件
            while (input.read(inputBuffer, 0, packageSize).also { read_count = it } != -1) {
                writeStatus = false
                isResultCallBack = false
                Log.e("doSendFileByBluetooth","写位置，addr：${addr}")
                woperation.send_data(WriterOperation.OTA_CMD_WRITE_DATA, addr, inputBuffer, read_count,
                        selectWrite.bluetoothGattCharacteristic, DeviceScanActivity.getInstance().mBLE)
                //for(delay_num = 0;delay_num < 10000;delay_num++);
                addr += read_count
                lastReadCount = read_count
                send_data_count += read_count
                i++
                // 更新升级进度%
                val writePrecent = (send_data_count.toFloat() / leng * 100)
                Log.e("doSendFileByBluetooth","已写入..$writePrecent%")
                if ((writePrecent - lastProgress) > 2) {
                    lastProgress = writePrecent
                    var msgUpdate = Message()
                    msgUpdate.what = iUpdateLog
                    msgUpdate.obj = "已写入..$lastProgress%"
                    mHandler.sendMessage(msgUpdate)
                }

//                while (!writeStatus){
//                    Log.e("doSendFileByBluetooth","等待写，结果！")
//                }
//                Log.e("doSendFileByBluetooth","写，成功！")
                while (!isResultCallBack) {
                    Log.e("doSendFileByBluetooth","等待写，回复！")
                    if (!checkConnectState()) {
                        return
                    }
                }
                Log.e("doSendFileByBluetooth","回复成功，开始下一帧！")

            }
            // 如果模块回复的数据大小和升级的不一致，则断开连接
            while (woperation.bytetoint(resultUpdateValue) != (addr - lastReadCount)) {
                if (!checkConnectState()) {
                    return
                }
            }

            Log.e("doSendFileByBluetooth","**********升级完成，则重启设备**********")
            updateLog("**********升级完成，则重启设备**********")
            isStartUpdateOTA = false
            input.close()
            isfile.close()

            // 升级完成，则重启设备！
            woperation.send_data(WriterOperation.OTA_CMD_REBOOT, 0, null,
                    0,selectWrite.bluetoothGattCharacteristic, DeviceScanActivity.getInstance().mBLE)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    val iUpdateLog = 111
    val iUpdateStop = 222
    val iRecvLog = 333
    val mHandler = object : Handler() {
        override fun dispatchMessage(msg: Message) {
            super.dispatchMessage(msg)
            when(msg.what) {
                iUpdateLog -> {
                    val writePrecent = (msg.obj).toString()
                    precenttv.text = writePrecent
                    updateLog2(writePrecent)
                }
                iUpdateStop -> {
                    mDialog.cancel()
                    view2.tvRecvLen.text = "接收:$iRecvLength"
                    val strMsg = (msg.obj).toString()
                    Toast.makeText(this@TestDataActivity, strMsg,Toast.LENGTH_SHORT).show()
                    updateLog2(strMsg)
                }
                iRecvLog -> {
                    view2.tvRecvLen.text = "接收:$iRecvLength"
                    val strMsg = (msg.obj).toString()
                    updateLog2(strMsg)
                }

            }

        }

    }



    private fun page_erase(addr: Int, length: Long, mgattCharacteristic: BluetoothGattCharacteristic, bleclass: BluetoothLeClass): Int {
        var addr0 = addr
        var count = length / 0x1000 // 0x1000 4096,4kb
        if (length % 0x1000 != 0L) {
            count++
        }
        Log.e("page_erase","该升级文件，需要擦除：$count 次空间，每次0x1000个长度")




        for (i in 0 until count) {
            isResultCallBack = false

            woperation.send_data(WriterOperation.OTA_CMD_PAGE_ERASE, addr0, null, 0,
                    mgattCharacteristic, bleclass)
            while (!isResultCallBack)
            Log.e("page_erase","addr:$addr0")
            addr0 += 0x1000
        }
        return 0
    }

    private fun checkConnectState():Boolean{
        if (!isConnected) {
            var msgStop = Message()
            msgStop.what = iUpdateStop
            msgStop.obj = "连接断开"
            mHandler.sendMessage(msgStop)
            return false
        }
        return true
    }



    override fun onBackPressed() {
        super.onBackPressed()
        startGetServerTimer(false)
        SendTimer(false)
        DeviceScanActivity.getInstance().mBLE.disconnect()
        unregisterReceiver(mBroadcastReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()

    }


}