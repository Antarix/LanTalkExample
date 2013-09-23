package com.larphoid.lantalk;

import android.app.Activity;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.widget.Toast;

import com.larphoid.lanudpcomm.LanUDPComm;

public class PlaybackAudioInBackground extends AsyncTask<Void, double[], Void> {
	private Activity activity;
	private LanUDPComm lanUdpComm;
	private Exception exception = null;
	AudioTrack playback;
	boolean playing = false;

	public PlaybackAudioInBackground(Activity pActivity, LanUDPComm pLanUdpComm) {
		this.activity = pActivity;
		this.lanUdpComm = pLanUdpComm;
	}

	@Override
	protected void onPreExecute() {
		final int minbuffersize = AudioTrack.getMinBufferSize(ActivityMain.SAMPLERATE, ActivityMain.channelConfiguration, ActivityMain.audioFormat);
		if (minbuffersize < 0) {
			return;
		}
		try {
			playback = new AudioTrack(AudioManager.STREAM_VOICE_CALL, ActivityMain.SAMPLERATE, ActivityMain.channelConfiguration, ActivityMain.audioFormat, minbuffersize, AudioTrack.MODE_STREAM);
			// playback.setStereoVolume(AudioTrack.getMaxVolume(), AudioTrack.getMaxVolume());
			Toast.makeText(activity, "vol: " + ActivityMain.audio.getStreamVolume(AudioManager.STREAM_VOICE_CALL) + "\nmax: " + ActivityMain.audio.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL), Toast.LENGTH_LONG).show();
		} catch (Exception e) {
			e.printStackTrace();
			exception = e;
			onError();
		}
		super.onPreExecute();
	}

	@Override
	protected Void doInBackground(Void... arg0) {
		try {
			if (playback != null && playback.getState() == AudioTrack.STATE_INITIALIZED) {
				playing = true;
				playback.play();
			} else {
				exception = null;
				onError();
			}
			while (!isCancelled() && playing) {
			}
			cancelme();
		} catch (Exception e) {
			e.printStackTrace();
			exception = e;
			onError();
		}
		return null;
	}

	private void onError() {
		cancelme();
		this.activity.runOnUiThread(showerror);
	}

	private void cancelme() {
		lanUdpComm.stopConnection();
		playing = false;
		if (playback != null && playback.getState() == AudioTrack.STATE_INITIALIZED) {
			playback.stop();
			playback.release();
		}
	}

	private final Runnable showerror = new Runnable() {
		@Override
		public void run() {
			if (exception == null) {
				Toast.makeText(activity, activity.getString(R.string.error_playback, activity.getString(R.string.error_message)), Toast.LENGTH_LONG).show();
			} else {
				exception.printStackTrace();
				Toast.makeText(activity, exception.getClass().getName() + ": " + exception.getMessage(), Toast.LENGTH_LONG).show();
			}
		}
	};
}
