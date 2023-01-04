package com.moko.mkscannergw.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.mkscannergw.AppConstants;
import com.moko.mkscannergw.R;
import com.moko.mkscannergw.base.BaseActivity;
import com.moko.mkscannergw.databinding.ActivityNetworkReportPeriodBinding;
import com.moko.mkscannergw.entity.MQTTConfig;
import com.moko.mkscannergw.entity.MokoDevice;
import com.moko.mkscannergw.utils.SPUtiles;
import com.moko.mkscannergw.utils.ToastUtils;
import com.moko.support.scannergw.MQTTConstants;
import com.moko.support.scannergw.MQTTSupport;
import com.moko.support.scannergw.entity.MsgConfigResult;
import com.moko.support.scannergw.entity.MsgDeviceInfo;
import com.moko.support.scannergw.entity.MsgReadResult;
import com.moko.support.scannergw.entity.NetworkReportPeriod;
import com.moko.support.scannergw.event.DeviceOnlineEvent;
import com.moko.support.scannergw.event.MQTTMessageArrivedEvent;
import com.moko.support.scannergw.handler.MQTTMessageAssembler;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;

public class NetworkReportPeriodActivity extends BaseActivity {
    private ActivityNetworkReportPeriodBinding mBind;

    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;

    public Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBind = ActivityNetworkReportPeriodBinding.inflate(getLayoutInflater());
        setContentView(mBind.getRoot());
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        mHandler = new Handler(Looper.getMainLooper());
        showLoadingProgressDialog();
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        getNetworkReportPeriod();
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
        if (msg_id == MQTTConstants.READ_MSG_ID_NETWORK_REPORT_PERIOD) {
            Type type = new TypeToken<MsgReadResult<NetworkReportPeriod>>() {
            }.getType();
            MsgReadResult<NetworkReportPeriod> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.deviceId.equals(result.device_info.device_id)) {
                return;
            }
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            mBind.etNetworkReportPeriod.setText(String.valueOf(result.data.interval));
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_NETWORK_REPORT_PERIOD) {
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
        String deviceId = event.getDeviceId();
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

    private void setNetworkReportPeriod(int period) {
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        MsgDeviceInfo deviceInfo = new MsgDeviceInfo();
        deviceInfo.device_id = mMokoDevice.deviceId;
        deviceInfo.mac = mMokoDevice.mac;
        NetworkReportPeriod reportPeriod = new NetworkReportPeriod();
        reportPeriod.interval = period;
        String message = MQTTMessageAssembler.assembleWriteNetworkReportPeriod(deviceInfo, reportPeriod);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.CONFIG_MSG_ID_NETWORK_REPORT_PERIOD, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }


    private void getNetworkReportPeriod() {
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        MsgDeviceInfo deviceInfo = new MsgDeviceInfo();
        deviceInfo.device_id = mMokoDevice.deviceId;
        deviceInfo.mac = mMokoDevice.mac;
        String message = MQTTMessageAssembler.assembleReadNetworkReportPeriod(deviceInfo);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.READ_MSG_ID_NETWORK_REPORT_PERIOD, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void onConfirm(View view) {
        if (isWindowLocked())
            return;
        String periodStr = mBind.etNetworkReportPeriod.getText().toString();
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        if (!mMokoDevice.isOnline) {
            ToastUtils.showToast(this, R.string.device_offline);
            return;
        }
        if (TextUtils.isEmpty(periodStr)) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        int period = Integer.parseInt(periodStr);
        if (period != 0 && (period < 10 || period > 86400)) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        setNetworkReportPeriod(period);
    }
}
