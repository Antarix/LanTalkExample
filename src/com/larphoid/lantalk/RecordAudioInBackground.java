package com.larphoid.lantalk;

import java.nio.ByteBuffer;

import android.app.Activity;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.widget.Toast;

import com.larphoid.lanudpcomm.LanUDPComm;

public class RecordAudioInBackground extends AsyncTask<Void, double[], Void> {
	private Activity activity;
	private LanUDPComm lanUdpComm;
	private boolean running;
	private AudioRecord recorder;
	private short[] inputbuffer = new short[ActivityMain.BLOCKSIZE];
	private ByteBuffer out;
	private int read;

	public RecordAudioInBackground(Activity pActivity, LanUDPComm pLanUdpComm) {
		this.activity = pActivity;
		this.lanUdpComm = pLanUdpComm;
	}

	@Override
	protected void onPreExecute() {
		final int minbuffersize = AudioRecord.getMinBufferSize(ActivityMain.SAMPLERATE, ActivityMain.channelConfiguration, ActivityMain.audioFormat);
		if (minbuffersize < 0) {
			onError();
			return;
		}
		try {
			recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, ActivityMain.SAMPLERATE, ActivityMain.channelConfiguration, ActivityMain.audioFormat, minbuffersize);
			recorder.startRecording();
			running = true;
		} catch (Exception e) {
			e.printStackTrace();
			onError();
		}
		super.onPreExecute();
	}

	@Override
	protected Void doInBackground(Void... arg0) {
		try {
			while (!isCancelled() && running) {
				read = recorder.read(inputbuffer, 0, ActivityMain.BLOCKSIZE);
				if (read > 0) {
					out = lanUdpComm.GetClientEventBuffer();
					for (int i = 0; i < read; i++) {
						out.putShort(inputbuffer[i]);
					}
					lanUdpComm.sendClientPacket();
				}
			}
			recorder.stop();
			recorder.release();
		} catch (Exception e) {
			e.printStackTrace();
			onError();
		}
		return null;
	}

	private void onError() {
		running = false;
		cancel(true);
		this.activity.runOnUiThread(showerror);
	}

	private final Runnable showerror = new Runnable() {
		@Override
		public void run() {
			Toast.makeText(activity, activity.getString(R.string.error_recorder, activity.getString(R.string.error_message)), Toast.LENGTH_LONG).show();
		}
	};
}
