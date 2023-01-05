package com.moko.mkscannergw.activity;

import android.content.Intent;
import android.net.Uri;
import android.view.View;

import com.moko.ble.lib.utils.MokoUtils;
import com.moko.mkscannergw.R;
import com.moko.mkscannergw.base.BaseActivity;
import com.moko.mkscannergw.databinding.ActivityAboutBinding;
import com.moko.mkscannergw.utils.ToastUtils;
import com.moko.mkscannergw.utils.Utils;
import com.moko.support.scannergw.event.MQTTConnectionCompleteEvent;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.Calendar;

public class AboutActivity extends BaseActivity<ActivityAboutBinding> {


    @Override
    protected void onCreate() {
        mBind.tvSoftVersion.setText(getString(R.string.version_info, Utils.getVersionInfo(this)));
    }

    @Override
    protected ActivityAboutBinding getViewBinding() {
        return ActivityAboutBinding.inflate(getLayoutInflater());
    }

    public void openURL(View view) {
        Uri uri = Uri.parse("https://" + getString(R.string.company_website));
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(intent);
    }

    public void back(View view) {
        finish();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTConnectionCompleteEvent(MQTTConnectionCompleteEvent event) {
    }

    public void onFeedbackLog(View view) {
        if (isWindowLocked())
            return;
        File trackerLog = new File(ScannerMainActivity.PATH_LOGCAT + File.separator + "MKScannerGW.txt");
        File trackerLogBak = new File(ScannerMainActivity.PATH_LOGCAT + File.separator + "MKScannerGW.txt.bak");
        File trackerCrashLog = new File(ScannerMainActivity.PATH_LOGCAT + File.separator + "crash_log.txt");
        if (!trackerLog.exists() || !trackerLog.canRead()) {
            ToastUtils.showToast(this, "File is not exists!");
            return;
        }
        String address = "feedback@mokotechnology.com";
        StringBuilder mailContent = new StringBuilder("MKScannerGW_");
        Calendar calendar = Calendar.getInstance();
        String date = MokoUtils.calendar2strDate(calendar, "yyyyMMdd");
        mailContent.append(date);
        String title = mailContent.toString();
        if ((!trackerLogBak.exists() || !trackerLogBak.canRead())
                && (!trackerCrashLog.exists() || !trackerCrashLog.canRead())) {
            Utils.sendEmail(this, address, "", title, "Choose Email Client", trackerLog);
        } else if (!trackerCrashLog.exists() || !trackerCrashLog.canRead()) {
            Utils.sendEmail(this, address, "", title, "Choose Email Client", trackerLog, trackerLogBak);
        } else if (!trackerLogBak.exists() || !trackerLogBak.canRead()) {
            Utils.sendEmail(this, address, "", title, "Choose Email Client", trackerLog, trackerCrashLog);
        } else {
            Utils.sendEmail(this, address, "", title, "Choose Email Client", trackerLog, trackerLogBak, trackerCrashLog);
        }
    }
}
