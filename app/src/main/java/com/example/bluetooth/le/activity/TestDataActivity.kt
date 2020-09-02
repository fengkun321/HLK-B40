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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_testdata)

        initUI()
        initData()

        val mAdapterManager = AdapterManager(this)
        BluetoothApplication.getInstance().adapterManager = mAdapterManager
        verifyStoragePermissions()


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
        view2.btnSet.setOnClickListener(this)
        view2.btnOTA.setOnClickListener(this)

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



        view2.tvSendLen.text = "发送:$iSendLength"
        view2.tvRecvLen.text = "接收:$iRecvLength"

        getServerDialog.setTitle("获取服务中")
        getServerDialog.setMessage("获取服务信息,请稍后...")
        getServerDialog.show()
        startGetServerTimer(true)
    }

    override fun onResume() {
        super.onResume()
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
        // MTU改变的回调
        DeviceScanActivity.getInstance().mBLE.setOnChangeMTUListener(onChangeMTUListener)


    }

    override fun onPause() {
        super.onPause()
        // 反注册

        // 断开或连接 状态发生变化时调用
        DeviceScanActivity.getInstance().mBLE.setUnConnectListener()
        // 发现BLE终端的Service时回调
        DeviceScanActivity.getInstance().mBLE.setUnServiceDiscoverListener()
        // 读操作的回调
        DeviceScanActivity.getInstance().mBLE.setUnDataAvailableListener()
        // 写操作的回调
        DeviceScanActivity.getInstance().mBLE.setUnWriteDataListener()
        // 接收到硬件返回的数据
        DeviceScanActivity.getInstance().mBLE.setUnRecvDataListener()
        // MTU改变的回调
        DeviceScanActivity.getInstance().mBLE.setUnChangeMTUListener()
    }

    private var getServerTimer = Timer()
    private fun startGetServerTimer(isRun:Boolean) {
        getServerTimer.cancel()
        if (isRun) {
            getServerTimer = Timer()
            getServerTimer.schedule(object : TimerTask(){
                override fun run() {
                    DeviceScanActivity.getInstance().mBLE.getServiceByGatt()
                }
            },0,1000)
        }

    }

    // 断开或连接 状态发生变化时调用
    private val OnConnectListener = object : BluetoothLeClass.OnConnectListener {
        override fun onConnected(gatt: BluetoothGatt?, status: Int, newState: Int) {
            updateLog("Connected to GATT server.")
            isConnected = true
        }
        override fun onDisconnect(gatt: BluetoothGatt?, status: Int, newState: Int) {
            SendTimer(false)
            updateLog("Disconnected from GATT server.")
            isConnected = false
        }
        override fun onConnectting(gatt: BluetoothGatt?, status: Int, newState: Int) {
            updateLog("Connectting to GATT...")
        }
    }

    var serverList = ArrayList<UUIDInfo>()
    var readCharaMap = HashMap<String, ArrayList<UUIDInfo>>()
    var writeCharaMap = HashMap<String, ArrayList<UUIDInfo>>()

    /**
     * 搜索到BLE终端服务的事件
     */
    private val mOnServiceDiscover = OnServiceDiscoverListener {
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
            val strStatus = BluetoothLeClass.strResultInfoByStatus(status)
            updateLog("Write:$strStatus")
        }
    }

    // 接收到硬件返回的数据
    private val OnRecvDataListerner = object : BluetoothLeClass.OnRecvDataListerner {
        override fun OnCharacteristicRecv(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
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
            updateLog("RecvData<<<$string,Len:${characteristic!!.value.size}")
        }
    }

    /** MTU更改的回调 */
    private val onChangeMTUListener = object : BluetoothLeClass.OnChangeMTUListener {
        override fun OnChangeMTUListener(strResult: String?, iMTU: Int) {
            val strValue = intent.getStringExtra("onMtuChanged")
            updateLogServer(strValue)
            updateLog(strValue)
            tvMTU.text = "MTU*${DeviceScanActivity.getInstance().mBLE.mtuSize}"
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
                        val b1 = descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
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
            R.id.btnOTA -> {
                startActivity(Intent(this,OTAUpdateActivity().javaClass))
            }
            R.id.btnSet -> {
                startActivity(Intent(this,SetParameterActivity().javaClass))
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
        val date = Date()
        val dateFormat = SimpleDateFormat("HH:mm:ss:SSS")
        // 主线程操作
        runOnUiThread {
            view2.tvLog.text =  view2.tvLog.text.toString().plus("\n${dateFormat.format(date)}::::${strValue}")
            view2.scrollView.fullScroll(ScrollView.FOCUS_DOWN)
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






    override fun onBackPressed() {
        super.onBackPressed()
        startGetServerTimer(false)
        SendTimer(false)
        DeviceScanActivity.getInstance().mBLE.disconnect()
    }

    override fun onDestroy() {
        super.onDestroy()

    }


}