package com.larphoid.lantalk;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings.Secure;
import android.text.InputFilter;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.larphoid.lanudpcomm.ClientEventHandler;
import com.larphoid.lanudpcomm.ClientInviteHandler;
import com.larphoid.lanudpcomm.LanUDPComm;

public class ActivityMain extends Activity implements OnClickListener, OnItemClickListener, ClientInviteHandler, ClientEventHandler {
	private static final int DISCOVERY_PORT = 4567;
	private static final int CLIENT_PORT = 5678;

	public static final int SAMPLERATE = 8000;
	public static final int channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_MONO;
	public static final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
	public static final int BLOCKSIZE = 256;

	private static final String PREFS_NAME = "prefs";
	private static final String PREF_NAME = "name";

	private static final int MENU_NAME = Menu.FIRST + 0;

	private static final int[] TO_CLIENTS = new int[] {
		R.id.clientname
	};
	private static final int[] TO_MESSAGES = new int[] {
		R.id.timestamp,
		R.id.from,
		R.id.messagetext
	};

	private ImageButton btLogin;
	private String myName;
	private LanUDPComm lanUdpComm;
	private ByteBuffer eventBuffer;
	private LinearLayout mainwindow;
	private ListView clientsList;
	private ListView messagewindow;
	private TextView clientname;
	private SimpleAdapter clientsAdapter;
	private RecordAudioInBackground recorder;
	private PlaybackAudioInBackground player;
	static AudioManager audio;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		loadPreferences();
		audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		mainwindow = (LinearLayout) findViewById(R.id.mainwindow);
		clientsList = (ListView) findViewById(R.id.clientlist);
		clientsList.setOnItemClickListener(this);
		messagewindow = (ListView) findViewById(R.id.messageswindow);
		btLogin = (ImageButton) findViewById(R.id.button_login);
		btLogin.setOnClickListener(this);
		clientname = (TextView) findViewById(R.id.clientname);
		lanUdpComm = new LanUDPComm(this, null, DISCOVERY_PORT, CLIENT_PORT, BLOCKSIZE * 2, this, this, myName, true);
		clientsAdapter = new SimpleAdapter(this, lanUdpComm.getClientsData(), R.layout.clientitem, LanUDPComm.FROM_CLIENTS, TO_CLIENTS);
		lanUdpComm.setClientsAdapter(clientsAdapter);
		clientsList.setAdapter(clientsAdapter);
		messagewindow.setAdapter(lanUdpComm.getMessagesAdapter(this, R.layout.messageitem, TO_MESSAGES));
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
		if (!lanUdpComm.isConnected()) {
			lanUdpComm.inviteClientForConnection(position, null, null);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, MENU_NAME, 0, R.string.menu_name);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		return !lanUdpComm.isConnected();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_NAME:
			changeMyDisplayName();
			break;
		}
		return true;
	}

	@Override
	public boolean onSearchRequested() {
		lanUdpComm.sendDiscoveryRequest(myName);
		return false;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (stopConnection()) return true;
			lanUdpComm.cleanup();
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	protected void onDestroy() {
		System.exit(0);
		super.onDestroy();
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.button_login:
			lanUdpComm.sendDiscoveryToAllIps(myName);
			break;
		}
	}

	@Override
	public String[] onInviteAccept() {
		return null;
	}

	@Override
	public void onStartConnection(final String[] data, final int offset, final DatagramPacket pack) {
		startConnection(data, offset, pack);
	}

	@Override
	public void onClientAccepted(String[] data, int offset, DatagramPacket pack) {
		startConnection(data, offset, pack);
	}

	@Override
	public void onClientEvent(final byte[] data, final int offset, final int dataLength, final DatagramPacket pack) {
		if (player != null && player.playing) {
			eventBuffer = ByteBuffer.wrap(data, offset, dataLength - offset);
			player.playback.write(eventBuffer.array(), 0, dataLength - offset);
		}
	}

	@Override
	public void onClientEndConnection(final DatagramPacket pack) {
		stopConnection();
		lanUdpComm.onClientEndConnection(pack);
	}

	@Override
	public void onClientNotResponding(DatagramPacket pack) {
		stopConnection();
		lanUdpComm.onClientNotResponding(pack);
	}

	// -----------------------------------------------------------------------------------------------------------------------------
	// ------------------------------------------------------- CLASSES and METHODS -------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------------------

	private void loadPreferences() {
		final SharedPreferences preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		myName = preferences.getString(PREF_NAME, Build.MODEL + "_" + Secure.getString(getContentResolver(), Secure.ANDROID_ID));
	}

	private void savePreferences() {
		final SharedPreferences preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		SharedPreferences.Editor prefs = preferences.edit();
		prefs.putString(PREF_NAME, myName);
		prefs.commit();
	}

	private void changeMyDisplayName() {
		final EditText edit = new EditText(this);
		edit.setSingleLine(true);
		edit.setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS);
		edit.setPadding(16, 16, 16, 16);
		edit.setText(myName);
		edit.setHint(R.string.menu_name);
		edit.selectAll();
		edit.setFilters(new InputFilter[] {
			new InputFilter.LengthFilter(50)
		});
		new AlertDialog.Builder(this).setCancelable(true).setIcon(android.R.drawable.ic_dialog_info).setTitle(R.string.menu_name).setView(edit).setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				myName = edit.getText().toString();
				savePreferences();
				lanUdpComm.sendDiscoveryRequest(myName);
			}
		}).show();
	}

	private Runnable startConnection = new Runnable() {
		@Override
		public void run() {
			mainwindow.setVisibility(View.GONE);
			clientname.setVisibility(View.VISIBLE);
		}
	};
	private Runnable endConnection = new Runnable() {
		@Override
		public void run() {
			clientname.setVisibility(View.GONE);
			mainwindow.setVisibility(View.VISIBLE);
		}
	};

	private void startConnection(final String data[], final int offset, final DatagramPacket pack) {
		recorder = new RecordAudioInBackground(this, lanUdpComm);
		recorder.execute();
		player = new PlaybackAudioInBackground(this, lanUdpComm);
		player.execute();
		clientname.setKeepScreenOn(true);
		clientname.setText(lanUdpComm.getClientName(pack.getAddress()));
		runOnUiThread(startConnection);
	}

	private boolean stopConnection() {
		final boolean result = lanUdpComm.stopConnection();
		clientname.setKeepScreenOn(false);
		clientname.setText("");
		runOnUiThread(endConnection);
		if (recorder != null && !recorder.isCancelled()) {
			recorder.cancel(true);
			recorder = null;
		}
		if (player != null && !player.isCancelled()) {
			player.cancel(true);
			player = null;
		}
		return result;
	}

}
