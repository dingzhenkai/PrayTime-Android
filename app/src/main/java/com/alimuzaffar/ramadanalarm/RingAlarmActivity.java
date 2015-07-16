package com.alimuzaffar.ramadanalarm;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.alimuzaffar.ramadanalarm.util.AlarmUtils;
import com.alimuzaffar.ramadanalarm.util.AppSettings;
import com.alimuzaffar.ramadanalarm.util.PrayTime;
import com.alimuzaffar.ramadanalarm.util.ScreenUtils;
import com.google.android.gms.wearable.Asset;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;

public class RingAlarmActivity extends AppCompatActivity implements Constants, View.OnClickListener, MediaPlayer.OnCompletionListener {

  private Button mAlarmOff;
  private TextView mPrayerName;
  MediaPlayer mMediaPlayer = null;
  Runnable mAutoStop = null;
  int mOriginalVolume = -1;
  AudioManager mAudioManager;
  AscendingAlarmHandler mAscHandler;
  String mPrayerNameString = null;
  AppSettings mSettings;
  static int mAudioStream = AudioManager.STREAM_ALARM;

  private NotificationManager mNotificationManager;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
        WindowManager.LayoutParams.FLAG_FULLSCREEN);

    ScreenUtils.lockOrientation(this);

    setContentView(R.layout.activity_ring_alarm);

    setVolumeControlStream(AudioManager.STREAM_ALARM);

    Calendar now = Calendar.getInstance(TimeZone.getDefault());
    now.setTimeInMillis(System.currentTimeMillis());

    mSettings = AppSettings.getInstance(this);

    mPrayerName = (TextView) findViewById(R.id.prayer_name);
    mPrayerNameString = getIntent().getStringExtra(EXTRA_PRAYER_NAME);

    if (getIntent().hasExtra(EXTRA_PRE_ALARM_FLAG)) {
      String formatString = "%2$tl:%2$tM %2$tp %1$s";
      if (mSettings.getTimeFormatFor(0) == PrayTime.TIME_24) {
        formatString = "%2$tk:%2$tM %1$s";
      }
      mPrayerName.setText(String.format(formatString, mPrayerNameString, now));
    } else {
      mPrayerName.setText(getString(R.string.prayer_name_time, mPrayerNameString));
    }

    mAlarmOff = (Button) findViewById(R.id.alarm_off);
    mAlarmOff.setOnClickListener(this);

    try {
      playAlarm();
    } catch (Exception e) {
      Log.e("RingAlarmActivity", e.getMessage(), e);
    }
  }

  private void playAlarm() throws Exception {
    boolean loop = true;
    Uri alert = null;
    AssetFileDescriptor assetFileDescriptor = null;
    if (mSettings.getBoolean(AppSettings.Key.USE_ADHAN)) {
      loop = false;
      //mAudioStream = AudioManager.STREAM_MUSIC;
      if (mPrayerNameString.equalsIgnoreCase(getString(R.string.fajr))) {
        assetFileDescriptor = getResources().openRawResourceFd(R.raw.adhan_fajr_trimmed);
      } else {
        assetFileDescriptor = getResources().openRawResourceFd(R.raw.adhan_trimmed);
      }

    } else if (mSettings.getBoolean(AppSettings.Key.IS_RANDOM_ALARM)) {
      //IF RANDOM RINGTONE IS SELECTED, USE IT.
      alert = AlarmUtils.getRandomRingtone(this);
    } else {
      //IF A RINGTONE IS SELECTED, USE IT!
      String uriStr = mSettings.getString(AppSettings.Key.SELECTED_RINGTONE);
      if (TextUtils.isEmpty(uriStr)) {
        alert = AlarmUtils.getAlarmRingtoneUri();
      } else {
        alert = Uri.parse(uriStr);
      }
    }
    //if URI is not null, then it's a ringtone
    mMediaPlayer = new MediaPlayer();
    if (alert != null) {
      mMediaPlayer.setDataSource(this, alert);
    } else {
      mMediaPlayer.setDataSource(assetFileDescriptor.getFileDescriptor(), assetFileDescriptor.getStartOffset(), assetFileDescriptor.getLength());
    }

    mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

    mOriginalVolume = mAudioManager.getStreamVolume(mAudioStream);

    if (mOriginalVolume == 0) {
      int volume = AlarmUtils.getAlarmVolumeFromPercentage(mAudioManager, mAudioStream, 50f);
      mAudioManager.setStreamVolume(AudioManager.STREAM_ALARM, volume, 0);
    }

    if (mSettings.getBoolean(AppSettings.Key.IS_ASCENDING_ALARM)) {
      int volume = AlarmUtils.getAlarmVolumeFromPercentage(mAudioManager, mAudioStream, 20f);
      mAudioManager.setStreamVolume(mAudioStream, volume, 0);
      if (mAscHandler == null) {
        mAscHandler = new AscendingAlarmHandler(mAudioManager);
      }
      mAscHandler.sendEmptyMessageDelayed(2, 10000);
      mAscHandler.sendEmptyMessageDelayed(3, 20000);
      mAscHandler.sendEmptyMessageDelayed(4, 30000);
      mAscHandler.sendEmptyMessageDelayed(5, 40000);
    }

    mMediaPlayer.setLooping(loop);
    mMediaPlayer.setAudioStreamType(mAudioStream);
    mMediaPlayer.prepare();
    if (assetFileDescriptor != null) {
      mMediaPlayer.setOnCompletionListener(this);
    }
    mMediaPlayer.start();

    mAlarmOff.postDelayed(mAutoStop = new Runnable() {
      @Override
      public void run() {
        sendNotification();
        mAlarmOff.performClick();
      }
    }, FIVE_MINUTES);


  }

  private void stopAlarm() {
    if (mAscHandler != null) {
      if (mAscHandler.hasMessages(20)) {
        mAscHandler.removeMessages(20);
      }
      if (mAscHandler.hasMessages(40)) {
        mAscHandler.removeMessages(20);
      }
      if (mAscHandler.hasMessages(60)) {
        mAscHandler.removeMessages(20);
      }
      if (mAscHandler.hasMessages(80)) {
        mAscHandler.removeMessages(20);
      }
      if (mAscHandler.hasMessages(100)) {
        mAscHandler.removeMessages(20);
      }
      mAscHandler = null;
    }

    if (mMediaPlayer != null) {
      mMediaPlayer.stop();
      mMediaPlayer.release();
      mMediaPlayer = null;
      if (mOriginalVolume != -1) {
        mAudioManager.setStreamVolume(mAudioStream, mOriginalVolume, 0);
      }
    }

    if (mAutoStop != null) {
      mAlarmOff.removeCallbacks(mAutoStop);
      mAutoStop = null;
    }
  }


  @Override
  public void onClick(View v) {
    stopAlarm();
    finish();
  }


  @Override
  public void onBackPressed() {
    //Do nothing since we want to force the user
    //to click the alarm button.
  }

  @Override
  protected void onDestroy() {
    stopAlarm();
    super.onDestroy();
  }

  // Post a notification indicating whether a doodle was found.
  private void sendNotification() {
    Calendar now = Calendar.getInstance(TimeZone.getDefault());
    now.setTimeInMillis(System.currentTimeMillis());

    String formatString = " %1$tl:%1$tM %1$tp " + getString(R.string.alarm_timed_out, mPrayerName);
    if (AppSettings.getInstance(this).getTimeFormatFor(0) == PrayTime.TIME_24) {
      formatString = "%1$tk:%1$tM " + getString(R.string.alarm_timed_out, mPrayerName);
    }
    String title = getString(R.string.alarm_timed_out_only);
    String body = String.format(formatString, now);

    mNotificationManager = (NotificationManager)
        this.getSystemService(Context.NOTIFICATION_SERVICE);

    PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, SalaatTimesActivity.class), PendingIntent.FLAG_CANCEL_CURRENT);

    NotificationCompat.Builder mBuilder =
        new NotificationCompat.Builder(this)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setStyle(new NotificationCompat.BigTextStyle()
                .bigText(body))
            .setContentText(body);

    mBuilder.setContentIntent(contentIntent);
    mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
  }

  @Override
  public void onCompletion(MediaPlayer mp) {
    //IF RAMADAN PLAY PRAYER, IF PRAYER ALREADY PLAYED, STOP
    if (mSettings.getBoolean(AppSettings.Key.USE_ADHAN) &&
        mSettings.getBoolean(AppSettings.Key.IS_RAMADAN)) {
      mMediaPlayer.stop();
      mMediaPlayer.release();
      mMediaPlayer = null;
      AssetFileDescriptor assetFileDescriptor = null;
      if (mPrayerNameString.equalsIgnoreCase(getString(R.string.fajr))) {
        assetFileDescriptor = getResources().openRawResourceFd(R.raw.dua_sehri);
      } else if (mPrayerNameString.equalsIgnoreCase(getString(R.string.maghrib))) {
        assetFileDescriptor = getResources().openRawResourceFd(R.raw.dua_iftar);
      }

      if (assetFileDescriptor == null) {
        stopAlarm();
        return;
      }

      try {
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setDataSource(assetFileDescriptor.getFileDescriptor(), assetFileDescriptor.getStartOffset(), assetFileDescriptor.getLength());
        mMediaPlayer.setAudioStreamType(mAudioStream);
        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
          @Override
          public void onCompletion(MediaPlayer mp) {
            stopAlarm();
          }
        });
        mMediaPlayer.setLooping(false);
        mMediaPlayer.prepare();
        mMediaPlayer.start();
      } catch (Exception e) {
        Log.e("RingAlarmActivity", e.getMessage(), e);
      }
    }
  }

  private static class AscendingAlarmHandler extends Handler {
    WeakReference<AudioManager> mAudioManagerRef = null;


    public AscendingAlarmHandler(AudioManager audioManager) {
      mAudioManagerRef = new WeakReference<>(audioManager);
    }

    @Override
    public void handleMessage(Message msg) {
      super.handleMessage(msg);
      AudioManager audioManager = mAudioManagerRef.get();
      if (audioManager != null) {
        int what = msg.what;
        float percentage = (float) what * 20f;
        int volume = AlarmUtils.getAlarmVolumeFromPercentage(audioManager, mAudioStream, percentage);
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, volume, 0);
      }
    }
  }

}
