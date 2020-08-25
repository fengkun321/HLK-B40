package com.example.bluetooth.le.activity

import android.app.Activity
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.Toast
import com.example.bluetooth.le.*
import kotlinx.android.synthetic.main.activity_testdata.*
import kotlinx.android.synthetic.main.viewpager_one.view.*
import kotlinx.android.synthetic.main.viewpager_two.*
import kotlinx.android.synthetic.main.viewpager_two.view.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_testdata)

        initUI()
        initData()
        // 注册广播
        reciverBand()

    }

    fun initUI() {
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
        tvClear.setOnClickListener(this)
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

    /** 初始化数据 */
    private fun initData() {
        tvName.text = DeviceScanActivity.getInstance().nowSelectDevice.name
        tvMac.text = DeviceScanActivity.getInstance().nowSelectDevice.address

        val list: ArrayList<String> = ArrayList()
        for (i in 0..29) {
            list.add("Name$i")
        }

        val myAdapter = MySpinnerAdapter(DeviceScanActivity.getInstance().serverList, this,true)
        view1.spinnerServer.setAdapter(myAdapter)

        view1.spinnerServer.setOnItemSelectedListener(onItemSelectedListener)
        view1.spinnerWrite.setOnItemSelectedListener(onItemWriteListener)
        view1.spinnerRead.setOnItemSelectedListener(onItemReadListener)



        // 断开或连接 状态发生变化时调用
        DeviceScanActivity.getInstance().mBLE.setOnConnectListener(OnConnectListener)
        // 读操作的回调
        DeviceScanActivity.getInstance().mBLE.setOnDataAvailableListener(OnDataAvailableListener)
        // 写操作的回调
        DeviceScanActivity.getInstance().mBLE.setOnWriteDataListener(OnWriteDataListener)
        // 接收到硬件返回的数据
        DeviceScanActivity.getInstance().mBLE.setOnRecvDataListener(OnRecvDataListerner)


        updateLog("Connected to GATT server.")
        var strServerCharact = ""
        var iServerCount = 0
        DeviceScanActivity.getInstance().serverList.forEach {
            ++iServerCount
            strServerCharact += ("\n$iServerCount.Server:\n${it.uuidString},${it.strCharactInfo}")
            val writeArray = DeviceScanActivity.getInstance().writeCharaMap[it.uuidString]
            val readArray = DeviceScanActivity.getInstance().readCharaMap[it.uuidString]
            strServerCharact += ("\nWrite:")
            writeArray?.forEach { itW ->
                strServerCharact += ("\n${itW.uuidString},${itW.strCharactInfo}")
            }
            strServerCharact += ("\nRead:")
            readArray?.forEach { itR ->
                strServerCharact += ("\n${itR.uuidString},${itR.strCharactInfo}")
            }
        }
        updateLog(strServerCharact)

        view2.tvSendLen.text = "发送:$iSendLength"
        view2.tvRecvLen.text = "接收:$iRecvLength"

    }

    // 断开或连接 状态发生变化时调用
    private val OnConnectListener = object : BluetoothLeClass.OnConnectListener {
        override fun onConnected(gatt: BluetoothGatt?, status: Int, newState: Int) {
            updateLog("Connected to GATT server.")
        }
        override fun onDisconnect(gatt: BluetoothGatt?, status: Int, newState: Int) {
            SendTimer(false)
            updateLog("Disconnected from GATT server.")
        }
        override fun onConnectting(gatt: BluetoothGatt?, status: Int, newState: Int) {
            updateLog("Connectting to GATT...")
        }
    }

    // 读操作的回调
    private val OnDataAvailableListener = object : BluetoothLeClass.OnDataAvailableListener {
        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            val strStatus = BluetoothLeClass.strResultInfoByStatus(status)
            updateLog("Read:$strStatus")
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
                updateLog("RecvData<<<$string,Len:${characteristic!!.value.size}")
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



    /** 选项改变监听事件 */
    private val onItemSelectedListener = object : OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
            selectServer = DeviceScanActivity.getInstance().serverList[position]
            val strServerUUID = DeviceScanActivity.getInstance().serverList[position].uuidString
//                Toast.makeText(this@TestDataActivity, "$strServerUUID", Toast.LENGTH_SHORT).show()
            var wArray = DeviceScanActivity.getInstance().writeCharaMap[strServerUUID]
            var rArray = DeviceScanActivity.getInstance().readCharaMap[strServerUUID]
            if (wArray == null) wArray = ArrayList<UUIDInfo>()
            if (rArray == null) rArray = ArrayList<UUIDInfo>()
            writeArray = wArray
            readArray = rArray
            myWriteAdapter = MySpinnerAdapter(writeArray, this@TestDataActivity,false)
            view1.spinnerWrite.setAdapter(myWriteAdapter)
            myReadAdapter = MySpinnerAdapter(readArray, this@TestDataActivity,false)
            view1.spinnerRead.setAdapter(myReadAdapter)
        }
        override fun onNothingSelected(parent: AdapterView<*>) {
            Log.e("TAG_MainActivity", parent.toString())
        }
    }

    /** 选项改变监听事件 */
    private val onItemWriteListener = object : OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            selectWrite = writeArray[position]
        }
        override fun onNothingSelected(parent: AdapterView<*>?) {
            Log.e("TAG_MainActivity", parent.toString())
        }
    }
    /** 选项改变监听事件 */
    private val onItemReadListener = object : OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
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
                updateLog("subscribe Notification:$isNotification")
            }
            R.id.tvRight -> viewPager.currentItem = 1
            R.id.tvLeft -> viewPager.currentItem = 0
            R.id.btnSend -> {
                val strSend = edData.text.toString()
                if (strSend.equals("")) return
                startSendData(strSend)
            }
            R.id.tvFilePath -> {

            }
            R.id.btnUpdate -> {
                val strFilePath = tvFilePath.text.toString()
                if (strFilePath.equals("")) return
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
                updateLog(strValue)
                tvMTU.text = "MTU*${DeviceScanActivity.getInstance().mBLE.mtuSize}"
            }
        }
    }

    private fun updateLog(strValue:String) {
        if (strValue.equals("")) return
        val date = Date()
        val dateFormat = SimpleDateFormat("HH:mm:ss:SSS")
        // 主线程操作
        runOnUiThread {
            tvLog.text =  tvLog.text.toString().plus("\n${dateFormat.format(date)}::::${strValue}")
            val offset: Int = tvLog.getMeasuredHeight() - scrollView.getMeasuredHeight()
            if (offset > 0) {
                scrollView.scrollTo(0, offset)
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        SendTimer(false)
        DeviceScanActivity.getInstance().mBLE.disconnect()
        unregisterReceiver(mBroadcastReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()

    }


}