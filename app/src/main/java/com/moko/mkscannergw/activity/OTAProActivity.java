package com.moko.mkscannergw.activity;

import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.lib.mqtt.MQTTSupport;
import com.moko.mkscannergw.AppConstants;
import com.moko.mkscannergw.R;
import com.moko.mkscannergw.base.BaseActivity;
import com.moko.mkscannergw.databinding.ActivityOtaProBinding;
import com.moko.lib.scannerui.dialog.BottomDialog;
import com.moko.mkscannergw.entity.MQTTConfig;
import com.moko.mkscannergw.entity.MokoDevice;
import com.moko.mkscannergw.service.DfuService;
import com.moko.mkscannergw.utils.FileUtils;
import com.moko.mkscannergw.utils.SPUtiles;
import com.moko.lib.scannerui.utils.ToastUtils;
import com.moko.support.scannergw.MQTTConstants;
import com.moko.support.scannergw.MokoSupport;
import com.moko.support.scannergw.entity.MsgConfigResult;
import com.moko.support.scannergw.entity.MsgDeviceInfo;
import com.moko.support.scannergw.entity.MsgNotify;
import com.moko.support.scannergw.entity.MsgReadResult;
import com.moko.support.scannergw.entity.OTABothWayParams;
import com.moko.support.scannergw.entity.OTAMasterParams;
import com.moko.support.scannergw.entity.OTAOneWayParams;
import com.moko.support.scannergw.entity.OTAResult;
import com.moko.support.scannergw.entity.OTAState;
import com.moko.support.scannergw.entity.SlaveDeviceInfo;
import com.moko.lib.mqtt.event.DeviceOnlineEvent;
import com.moko.lib.mqtt.event.MQTTMessageArrivedEvent;
import com.moko.support.scannergw.handler.MQTTMessageAssembler;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import no.nordicsemi.android.dfu.DfuProgressListener;
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter;
import no.nordicsemi.android.dfu.DfuServiceInitiator;
import no.nordicsemi.android.dfu.DfuServiceListenerHelper;

public class OTAProActivity extends BaseActivity<ActivityOtaProBinding> {
    public static final int REQUEST_CODE_SELECT_FIRMWARE = 0x10;
    private final String FILTER_ASCII = "[ -~]*";
    public static String TAG = OTAProActivity.class.getSimpleName();
    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private ArrayList<String> mValues;
    private int mSelected;
    private Handler mHandler;
    private String mSlaveDeviceMac;
    private Uri mFirmwareUri;

    @Override
    protected void onCreate() {
        if (getIntent().getExtras() != null) {
            mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        }
        InputFilter inputFilter = (source, start, end, dest, dstart, dend) -> {
            if (!(source + "").matches(FILTER_ASCII)) {
                return "";
            }
            return null;
        };
        mBind.etMasterHost.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), inputFilter});
        mBind.etOneWayHost.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), inputFilter});
        mBind.etBothWayHost.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), inputFilter});
        mBind.etMasterFilePath.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100), inputFilter});
        mBind.etOneWayCaFilePath.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100), inputFilter});
        mBind.etBothWayCaFilePath.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100), inputFilter});
        mBind.etBothWayClientKeyFilePath.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100), inputFilter});
        mBind.etBothWayClientCertFilePath.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100), inputFilter});
        mHandler = new Handler(Looper.getMainLooper());
        String mqttConfigAppStr = SPUtiles.getStringValue(OTAProActivity.this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mValues = new ArrayList<>();
        mValues.add("Master Firmware");
        mValues.add("Slave Firmware");
        mValues.add("CA certificate");
        mValues.add("Self signed server certificates ");
        mBind.tvUpdateType.setText(mValues.get(mSelected));
    }

    @Override
    protected ActivityOtaProBinding getViewBinding() {
        return ActivityOtaProBinding.inflate(getLayoutInflater());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTMessageArrivedEvent(MQTTMessageArrivedEvent event) {
        // 更新所有设备的网络状态
        final String topic = event.getTopic();
        final String message = event.getMessage();
        if (TextUtils.isEmpty(message)) return;
        int msg_id;
        try {
            JsonObject object = new Gson().fromJson(message, JsonObject.class);
            JsonElement element = object.get("msg_id");
            msg_id = element.getAsInt();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_OTA_RESULT) {
            Type type = new TypeToken<MsgNotify<OTAResult>>() {
            }.getType();
            MsgNotify<OTAResult> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.deviceId.equals(result.device_info.device_id)) return;
            dismissLoadingProgressDialog();
            if (result.data.ota_result == 1) {
                ToastUtils.showToast(this, R.string.update_success);
            } else {
                ToastUtils.showToast(this, R.string.update_failed);
            }
            if (isUpgrading && isUpgradeCompleted) {
                isUpgrading = false;
                isUpgradeCompleted = false;
                dismissDFUProgressDialog();
            }
        }
        if (msg_id == MQTTConstants.READ_MSG_ID_SLAVE_DEVICE_INFO) {
            Type type = new TypeToken<MsgReadResult<SlaveDeviceInfo>>() {
            }.getType();
            MsgReadResult<SlaveDeviceInfo> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.deviceId.equals(result.device_info.device_id)) return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            StringBuffer stringBuffer = new StringBuffer(result.data.slave_mac);
            stringBuffer.insert(2, ":");
            stringBuffer.insert(5, ":");
            stringBuffer.insert(8, ":");
            stringBuffer.insert(11, ":");
            stringBuffer.insert(14, ":");
            mSlaveDeviceMac = stringBuffer.toString().toUpperCase();
            // 不需要扫描，直接DFU升级
//            startScan();
            slaveOTA();
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_OTA_MASTER
                || msg_id == MQTTConstants.CONFIG_MSG_ID_OTA_ONE_WAY
                || msg_id == MQTTConstants.CONFIG_MSG_ID_OTA_BOTH_WAY) {
            Type type = new TypeToken<MsgConfigResult<OTAState>>() {
            }.getType();
            MsgConfigResult<OTAState> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.deviceId.equals(result.device_info.device_id)) return;
            mHandler.removeMessages(0);
            if (result.result_code == 0) {
                if (result.data.ota_state != 0) {
                    dismissLoadingProgressDialog();
                    ToastUtils.showToast(this, "Device is upgrading, please try it again later！");
                }
            } else {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(this, "Set up failed");
            }
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_OTA_SLAVE) {
            Type type = new TypeToken<MsgConfigResult>() {
            }.getType();
            MsgConfigResult result = new Gson().fromJson(message, type);
            if (!mMokoDevice.deviceId.equals(result.device_info.device_id)) return;
            if (result.result_code == 0) {
                // 获取MAC地址后开始搜索设备
                getSlaveMac();
            } else {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
                ToastUtils.showToast(this, "Set up failed");
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceOnlineEvent(DeviceOnlineEvent event) {
        String deviceId = event.getMac();
        if (!mMokoDevice.deviceId.equals(deviceId)) return;
        boolean online = event.isOnline();
        if (!online) finish();
    }

    public void back(View view) {
        finish();
    }

    public void startUpdate(View view) {
        if (isWindowLocked()) return;
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        if (!mMokoDevice.isOnline) {
            ToastUtils.showToast(this, R.string.device_offline);
            return;
        }
        if (mSelected == 0) {
            String hostStr = mBind.etMasterHost.getText().toString();
            String portStr = mBind.etMasterPort.getText().toString();
            String masterStr = mBind.etMasterFilePath.getText().toString();
            if (TextUtils.isEmpty(hostStr)) {
                ToastUtils.showToast(this, R.string.mqtt_verify_host);
                return;
            }
            if (TextUtils.isEmpty(portStr) || Integer.parseInt(portStr) > 65535) {
                ToastUtils.showToast(this, R.string.mqtt_verify_port_empty);
                return;
            }
            if (TextUtils.isEmpty(masterStr)) {
                ToastUtils.showToast(this, R.string.mqtt_verify_file_path);
                return;
            }
        }
        if (mSelected == 1) {
            String slaveStr = mBind.tvSlaveFilePath.getText().toString();
            if (TextUtils.isEmpty(slaveStr)) {
                ToastUtils.showToast(this, R.string.mqtt_verify_file_path);
                return;
            }
        }
        if (mSelected == 2) {
            String hostStr = mBind.etOneWayHost.getText().toString();
            String portStr = mBind.etOneWayPort.getText().toString();
            String oneWayStr = mBind.etOneWayCaFilePath.getText().toString();
            if (TextUtils.isEmpty(hostStr)) {
                ToastUtils.showToast(this, R.string.mqtt_verify_host);
                return;
            }
            if (TextUtils.isEmpty(portStr) || Integer.parseInt(portStr) > 65535) {
                ToastUtils.showToast(this, R.string.mqtt_verify_port_empty);
                return;
            }
            if (TextUtils.isEmpty(oneWayStr)) {
                ToastUtils.showToast(this, R.string.mqtt_verify_file_path);
                return;
            }
        }
        if (mSelected == 3) {
            String hostStr = mBind.etBothWayHost.getText().toString();
            String portStr = mBind.etBothWayPort.getText().toString();
            String bothWayCaStr = mBind.etBothWayCaFilePath.getText().toString();
            String bothWayClientKeyStr = mBind.etBothWayClientKeyFilePath.getText().toString();
            String bothWayClientCertStr = mBind.etBothWayClientCertFilePath.getText().toString();
            if (TextUtils.isEmpty(hostStr)) {
                ToastUtils.showToast(this, R.string.mqtt_verify_host);
                return;
            }
            if (TextUtils.isEmpty(portStr) || Integer.parseInt(portStr) > 65535) {
                ToastUtils.showToast(this, R.string.mqtt_verify_port_empty);
                return;
            }
            if (TextUtils.isEmpty(bothWayCaStr)
                    || TextUtils.isEmpty(bothWayClientKeyStr)
                    || TextUtils.isEmpty(bothWayClientCertStr)) {
                ToastUtils.showToast(this, R.string.mqtt_verify_file_path);
                return;
            }
        }
        XLog.i("升级固件");
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 50 * 1000);
        showLoadingProgressDialog();
        if (mSelected == 0) {
            setOTAMaster();
        }
        if (mSelected == 1) {
            setOTASlave();
        }
        if (mSelected == 2) {
            setOTAOneWay();
        }
        if (mSelected == 3) {
            setOTABothWay();
        }
    }

    public void onSelectUpdateType(View view) {
        BottomDialog dialog = new BottomDialog();
        dialog.setDatas(mValues, mSelected);
        dialog.setListener(value -> {
            mSelected = value;
            switch (value) {
                case 0:
                    mBind.llMasterFirmware.setVisibility(View.VISIBLE);
                    mBind.clSlaveFirmware.setVisibility(View.GONE);
                    mBind.llOneWay.setVisibility(View.GONE);
                    mBind.llBothWay.setVisibility(View.GONE);
                    break;
                case 1:
                    mBind.llMasterFirmware.setVisibility(View.GONE);
                    mBind.clSlaveFirmware.setVisibility(View.VISIBLE);
                    mBind.llOneWay.setVisibility(View.GONE);
                    mBind.llBothWay.setVisibility(View.GONE);
                    break;
                case 2:
                    mBind.llMasterFirmware.setVisibility(View.GONE);
                    mBind.clSlaveFirmware.setVisibility(View.GONE);
                    mBind.llOneWay.setVisibility(View.VISIBLE);
                    mBind.llBothWay.setVisibility(View.GONE);
                    break;
                case 3:
                    mBind.llMasterFirmware.setVisibility(View.GONE);
                    mBind.clSlaveFirmware.setVisibility(View.GONE);
                    mBind.llOneWay.setVisibility(View.GONE);
                    mBind.llBothWay.setVisibility(View.VISIBLE);
                    break;
            }
            mBind.tvUpdateType.setText(mValues.get(value));
        });
        dialog.show(getSupportFragmentManager());
    }

    private void setOTAMaster() {
        String hostStr = mBind.etMasterHost.getText().toString();
        String portStr = mBind.etMasterPort.getText().toString();
        String masterStr = mBind.etMasterFilePath.getText().toString();
        MsgDeviceInfo deviceInfo = new MsgDeviceInfo();
        deviceInfo.device_id = mMokoDevice.deviceId;
        deviceInfo.mac = mMokoDevice.mac;
        OTAMasterParams params = new OTAMasterParams();
        params.host = hostStr;
        params.port = Integer.parseInt(portStr);
        params.firmware_way = masterStr;
        String message = MQTTMessageAssembler.assembleWriteOTAMaster(deviceInfo, params);
        try {
            MQTTSupport.getInstance().publish(getTopic(), message, MQTTConstants.CONFIG_MSG_ID_OTA_MASTER, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setOTASlave() {
        MsgDeviceInfo deviceInfo = new MsgDeviceInfo();
        deviceInfo.device_id = mMokoDevice.deviceId;
        deviceInfo.mac = mMokoDevice.mac;
        String message = MQTTMessageAssembler.assembleWriteOTASlave(deviceInfo);
        try {
            MQTTSupport.getInstance().publish(getTopic(), message, MQTTConstants.CONFIG_MSG_ID_OTA_SLAVE, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void getSlaveMac() {
        MsgDeviceInfo deviceInfo = new MsgDeviceInfo();
        deviceInfo.device_id = mMokoDevice.deviceId;
        deviceInfo.mac = mMokoDevice.mac;
        String message = MQTTMessageAssembler.assembleReadSlaveDeviceInfo(deviceInfo);
        try {
            MQTTSupport.getInstance().publish(getTopic(), message, MQTTConstants.READ_MSG_ID_SLAVE_DEVICE_INFO, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setOTAOneWay() {
        String hostStr = mBind.etOneWayHost.getText().toString();
        String portStr = mBind.etOneWayPort.getText().toString();
        String oneWayStr = mBind.etOneWayCaFilePath.getText().toString();
        MsgDeviceInfo deviceInfo = new MsgDeviceInfo();
        deviceInfo.device_id = mMokoDevice.deviceId;
        deviceInfo.mac = mMokoDevice.mac;
        OTAOneWayParams params = new OTAOneWayParams();
        params.host = hostStr;
        params.port = Integer.parseInt(portStr);
        params.ca_way = oneWayStr;
        String message = MQTTMessageAssembler.assembleWriteOTAOneWay(deviceInfo, params);
        try {
            MQTTSupport.getInstance().publish(getTopic(), message, MQTTConstants.CONFIG_MSG_ID_OTA_ONE_WAY, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setOTABothWay() {
        String hostStr = mBind.etBothWayHost.getText().toString();
        String portStr = mBind.etBothWayPort.getText().toString();
        String bothWayCaStr = mBind.etBothWayCaFilePath.getText().toString();
        String bothWayClientKeyStr = mBind.etBothWayClientKeyFilePath.getText().toString();
        String bothWayClientCertStr = mBind.etBothWayClientCertFilePath.getText().toString();
        MsgDeviceInfo deviceInfo = new MsgDeviceInfo();
        deviceInfo.device_id = mMokoDevice.deviceId;
        deviceInfo.mac = mMokoDevice.mac;
        OTABothWayParams params = new OTABothWayParams();
        params.host = hostStr;
        params.port = Integer.parseInt(portStr);
        params.ca_way = bothWayCaStr;
        params.client_cer_way = bothWayClientCertStr;
        params.client_key_way = bothWayClientKeyStr;
        String message = MQTTMessageAssembler.assembleWriteOTABothWay(deviceInfo, params);
        try {
            MQTTSupport.getInstance().publish(getTopic(), message, MQTTConstants.CONFIG_MSG_ID_OTA_BOTH_WAY, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private String getTopic() {
        return TextUtils.isEmpty(appMqttConfig.topicPublish) ? mMokoDevice.topicSubscribe : appMqttConfig.topicPublish;
    }

    public void openSlaveFirmwareFile(View view) {
        if (isWindowLocked())
            return;
        {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");//设置类型，我这里是任意类型，任意后缀的可以这样写。
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            try {
                startActivityForResult(Intent.createChooser(intent, "select file first!"), REQUEST_CODE_SELECT_FIRMWARE);
            } catch (ActivityNotFoundException ex) {
                ToastUtils.showToast(this, "install file manager app");
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        //得到uri，后面就是将uri转化成file的过程。
        Uri uri = data.getData();
        String filePath = FileUtils.getPath(this, uri);
        if (TextUtils.isEmpty(filePath)) {
            ToastUtils.showToast(this, "file path error!");
            return;
        }
        final File file = new File(filePath);
        if (file.exists()) {
            if (requestCode == REQUEST_CODE_SELECT_FIRMWARE) {
                mFirmwareUri = uri;
                mBind.tvSlaveFilePath.setText(filePath);
            }
        } else {
            ToastUtils.showToast(this, "file is not exists!");
        }
    }

//    private void startScan() {
//        showLoadingProgressDialog();
//        mokoBleScanner.startScanDevice(this);
//        mHandler.postDelayed(() -> {
//            dismissLoadingProgressDialog();
//            mokoBleScanner.stopScanDevice();
//        }, 1000 * 30);
//    }

//    @Override
//    public void onStartScan() {
//
//    }
//
//    @Override
//    public void onScanDevice(DeviceInfo deviceInfo) {
//        ScanResult scanResult = deviceInfo.scanResult;
//        ScanRecord scanRecord = scanResult.getScanRecord();
//        Map<ParcelUuid, byte[]> map = scanRecord.getServiceData();
//        if (map == null || map.isEmpty()) return;
//        byte[] data = map.get(new ParcelUuid(OrderServices.SERVICE_ADV.getUuid()));
//        if (data == null || data.length != 1) return;
//        if (!deviceInfo.mac.equalsIgnoreCase(mSlaveDeviceMac)) return;
//        mHandler.removeMessages(0);
//        mokoBleScanner.stopScanDevice();
//        mBind.tvUpdateType.postDelayed(() -> MokoSupport.getInstance().connDevice(deviceInfo.mac), 500);
//    }
//
//    @Override
//    public void onStopScan() {
//    }


    //    @Subscribe(threadMode = ThreadMode.MAIN)
//    public void onConnectStatusEvent(ConnectStatusEvent event) {
//        String action = event.getAction();
//        if (MokoConstants.ACTION_DISCONNECTED.equals(action)) {
//            dismissLoadingProgressDialog();
//            if (MokoSupport.getInstance().isBluetoothOpen()) {
//                if (!isUpgrading) {
//                    ToastUtils.showToast(this, "Connection Failed, please try again");
//                }
//            }
//        }
//        if (MokoConstants.ACTION_DISCOVER_SUCCESS.equals(action)) {
//            dismissLoadingProgressDialog();
//            slaveOta();
//        }
//    }
    private void slaveOTA() {
        String firmwareFilePath = mBind.tvSlaveFilePath.getText().toString();
        final File firmwareFile = new File(firmwareFilePath);
        if (firmwareFile.exists()) {
            try {
                final DfuServiceInitiator starter = new DfuServiceInitiator(mSlaveDeviceMac)
                        .setKeepBond(false)
                        .setForeground(false)
                        .disableMtuRequest()
                        .setDisableNotification(true);
                starter.setZip(mFirmwareUri);
                starter.start(this, DfuService.class);
                showDFUProgressDialog("Waiting...");
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            Toast.makeText(this, "file is not exists!", Toast.LENGTH_SHORT).show();
        }
    }

    private ProgressDialog mDFUDialog;

    private void showDFUProgressDialog(String tips) {
        mDFUDialog = new ProgressDialog(this);
        mDFUDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mDFUDialog.setCanceledOnTouchOutside(false);
        mDFUDialog.setCancelable(false);
        mDFUDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mDFUDialog.setMessage(tips);
        if (!isFinishing() && mDFUDialog != null && !mDFUDialog.isShowing()) {
            mDFUDialog.show();
        }
    }

    private void dismissDFUProgressDialog() {
        mDeviceConnectCount = 0;
        if (!isFinishing() && mDFUDialog != null && mDFUDialog.isShowing()) {
            mDFUDialog.dismiss();
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        DfuServiceListenerHelper.registerProgressListener(this, mDfuProgressListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        DfuServiceListenerHelper.unregisterProgressListener(this, mDfuProgressListener);
    }

    private int mDeviceConnectCount;
    private boolean isUpgrading;
    private boolean isUpgradeCompleted;

    private final DfuProgressListener mDfuProgressListener = new DfuProgressListenerAdapter() {
        @Override
        public void onDeviceConnecting(String deviceAddress) {
            XLog.w("onDeviceConnecting...");
            mDeviceConnectCount++;
            if (mDeviceConnectCount > 3) {
                ToastUtils.showToast(OTAProActivity.this, "Error:DFU Failed");
                MokoSupport.getInstance().disConnectBle();
                final LocalBroadcastManager manager = LocalBroadcastManager.getInstance(OTAProActivity.this);
                final Intent abortAction = new Intent(DfuService.BROADCAST_ACTION);
                abortAction.putExtra(DfuService.EXTRA_ACTION, DfuService.ACTION_ABORT);
                manager.sendBroadcast(abortAction);
            }
        }

        @Override
        public void onDeviceDisconnecting(String deviceAddress) {
            XLog.w("onDeviceDisconnecting...");
        }

        @Override
        public void onDfuProcessStarting(String deviceAddress) {
            isUpgrading = true;
            mDFUDialog.setMessage("DfuProcessStarting...");
        }


        @Override
        public void onEnablingDfuMode(String deviceAddress) {
            mDFUDialog.setMessage("EnablingDfuMode...");
        }

        @Override
        public void onFirmwareValidating(String deviceAddress) {
            mDFUDialog.setMessage("FirmwareValidating...");
        }

        @Override
        public void onDfuCompleted(String deviceAddress) {
            XLog.w("onDfuCompleted...");
            ToastUtils.showToast(OTAProActivity.this, R.string.update_success);
            isUpgradeCompleted = true;
            if (isUpgrading && isUpgradeCompleted) {
                isUpgrading = false;
                isUpgradeCompleted = false;
                dismissDFUProgressDialog();
            }
        }

        @Override
        public void onDfuAborted(String deviceAddress) {
            mDFUDialog.setMessage("DfuAborted...");
        }

        @Override
        public void onProgressChanged(String deviceAddress, int percent, float speed, float avgSpeed, int currentPart, int partsTotal) {
            String progress = String.format("Progress:%d%%", percent);
            XLog.i(progress);
            mDFUDialog.setMessage(progress);
        }

        @Override
        public void onError(String deviceAddress, int error, int errorType, String message) {
            dismissDFUProgressDialog();
            XLog.i("DFU Error:" + message);
            ToastUtils.showToast(OTAProActivity.this, R.string.update_failed);
        }
    };

}
