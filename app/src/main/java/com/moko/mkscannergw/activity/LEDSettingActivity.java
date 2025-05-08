package com.moko.mkscannergw.activity;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.lib.mqtt.MQTTSupport;
import com.moko.mkscannergw.AppConstants;
import com.moko.mkscannergw.R;
import com.moko.mkscannergw.base.BaseActivity;
import com.moko.mkscannergw.databinding.ActivityLedSettingBinding;
import com.moko.mkscannergw.entity.MQTTConfig;
import com.moko.mkscannergw.entity.MokoDevice;
import com.moko.mkscannergw.utils.SPUtiles;
import com.moko.lib.scannerui.utils.ToastUtils;
import com.moko.support.scannergw.MQTTConstants;
import com.moko.support.scannergw.entity.IndicatorLightStatus;
import com.moko.support.scannergw.entity.MsgConfigResult;
import com.moko.support.scannergw.entity.MsgDeviceInfo;
import com.moko.support.scannergw.entity.MsgReadResult;
import com.moko.lib.mqtt.event.DeviceOnlineEvent;
import com.moko.lib.mqtt.event.MQTTMessageArrivedEvent;
import com.moko.support.scannergw.handler.MQTTMessageAssembler;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;

public class LEDSettingActivity extends BaseActivity<ActivityLedSettingBinding> {

    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;

    private int bleBroadcastEnable;
    private int bleConnectedEnable;
    private int serverConnectingEnable;
    private int serverConnectedEnable;

    public Handler mHandler;

    @Override
    protected void onCreate() {
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);

        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        mHandler = new Handler(Looper.getMainLooper());
        showLoadingProgressDialog();
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            LEDSettingActivity.this.finish();
        }, 30 * 1000);
        getLEDStatus();
    }

    @Override
    protected ActivityLedSettingBinding getViewBinding() {
        return ActivityLedSettingBinding.inflate(getLayoutInflater());
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTMessageArrivedEvent(MQTTMessageArrivedEvent event) {
        // 更新所有设备的网络状态
        final String topic = event.getTopic();
        final String message = event.getMessage();
        if (TextUtils.isEmpty(message))
            return;
        int msg_id;
        try {
            JsonObject object = new Gson().fromJson(message, JsonObject.class);
            JsonElement element = object.get("msg_id");
            msg_id = element.getAsInt();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        if (msg_id == MQTTConstants.READ_MSG_ID_INDICATOR_STATUS) {
            Type type = new TypeToken<MsgReadResult<IndicatorLightStatus>>() {
            }.getType();
            MsgReadResult<IndicatorLightStatus> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.deviceId.equals(result.device_info.device_id)) {
                return;
            }
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            bleBroadcastEnable = result.data.ble_adv;
            bleConnectedEnable = result.data.ble_connected;
            serverConnectingEnable = result.data.server_connecting;
            serverConnectedEnable = result.data.server_connected;
            if (bleConnectedEnable == 1) {
                mBind.cbBleConnected.setChecked(true);
            }
            if (bleBroadcastEnable == 1) {
                mBind.cbBleBroadcast.setChecked(true);
            }
            if (serverConnectingEnable == 1) {
                mBind.cbServerConnecting.setChecked(true);
            }
            if (serverConnectedEnable == 1) {
                mBind.cbServerConnected.setChecked(true);
            }
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_INDICATOR_STATUS) {
            Type type = new TypeToken<MsgConfigResult>() {
            }.getType();
            MsgConfigResult result = new Gson().fromJson(message, type);
            if (!mMokoDevice.deviceId.equals(result.device_info.device_id)) {
                return;
            }
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            if (result.result_code == 0) {
                ToastUtils.showToast(this, "Set up succeed");
            } else {
                ToastUtils.showToast(this, "Set up failed");
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceOnlineEvent(DeviceOnlineEvent event) {
        String deviceId = event.getMac();
        if (!mMokoDevice.deviceId.equals(deviceId)) {
            return;
        }
        boolean online = event.isOnline();
        if (!online) {
            finish();
        }
    }

    public void back(View view) {
        finish();
    }


    private void setLEDStatus() {
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        MsgDeviceInfo deviceInfo = new MsgDeviceInfo();
        deviceInfo.device_id = mMokoDevice.deviceId;
        deviceInfo.mac = mMokoDevice.mac;
        IndicatorLightStatus lightStatus = new IndicatorLightStatus();
        lightStatus.ble_adv = bleBroadcastEnable;
        lightStatus.ble_connected = bleConnectedEnable;
        lightStatus.server_connecting = serverConnectingEnable;
        lightStatus.server_connected = serverConnectedEnable;
        String message = MQTTMessageAssembler.assembleWriteLEDStatus(deviceInfo, lightStatus);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.CONFIG_MSG_ID_INDICATOR_STATUS, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void getLEDStatus() {
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        MsgDeviceInfo deviceInfo = new MsgDeviceInfo();
        deviceInfo.device_id = mMokoDevice.deviceId;
        deviceInfo.mac = mMokoDevice.mac;
        String message = MQTTMessageAssembler.assembleReadLEDStatus(deviceInfo);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.READ_MSG_ID_INDICATOR_STATUS, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void onConfirm(View view) {
        if (isWindowLocked())
            return;
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        if (!mMokoDevice.isOnline) {
            ToastUtils.showToast(this, R.string.device_offline);
            return;
        }
        bleBroadcastEnable = mBind.cbBleBroadcast.isChecked() ? 1 : 0;
        bleConnectedEnable = mBind.cbBleConnected.isChecked() ? 1 : 0;
        serverConnectingEnable = mBind.cbServerConnecting.isChecked() ? 1 : 0;
        serverConnectedEnable = mBind.cbServerConnected.isChecked() ? 1 : 0;
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        setLEDStatus();
    }
}
