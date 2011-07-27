package com.quran.labs.androidquran;

import java.util.ArrayList;
import java.util.HashMap;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.markupartist.android.widget.ActionBar.IntentAction;
import com.quran.labs.androidquran.common.AyahItem;
import com.quran.labs.androidquran.common.AyahStateListener;
import com.quran.labs.androidquran.common.PageViewQuranActivity;
import com.quran.labs.androidquran.common.QuranPageFeeder;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.service.AudioServiceBinder;
import com.quran.labs.androidquran.service.QuranAudioService;
import com.quran.labs.androidquran.util.QuranAudioLibrary;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;

public class QuranViewActivity extends PageViewQuranActivity implements
		AyahStateListener {

	protected static final String ACTION_PREVIOUS = "ACTION_PREVIOUS";
	protected static final String ACTION_NEXT = "ACTION_NEXT";
	protected static final String ACTION_PAUSE = "ACTION_PAUSE";
	protected static final String ACTION_PLAY = "ACTION_PLAY";
	protected static final String ACTION_STOP = "ACTION_STOP";
	protected static final String ACTION_CHANGE_READER = "ACTION_CHANGE_READER";
	protected static final String ACTION_JUMP_TO_AYAH = "ACTION_JUMP_TO_AYAH";

	private static final String TAG = "QuranViewActivity";

	private boolean bounded = false;
	private AudioServiceBinder quranAudioPlayer = null;
	
	// on Stop Actions
	private static final int ACTION_BAR_ACTION_PLAY = 0;

	// on Play Actions
	private static final int ACTION_BAR_ACTION_CHANGE_READER = 0;
	private static final int ACTION_BAR_ACTION_JUMP_TO_AYAH = 1;
	private static final int ACTION_BAR_ACTION_PREVIOUS = 2;
	private static final int ACTION_BAR_ACTION_PAUSE = 3;
	private static final int ACTION_BAR_ACTION_STOP = 4;
	private static final int ACTION_BAR_ACTION_NEXT = 5;

	private AyahItem lastAyah;
	private int currentReaderId;

	private HashMap<String, IntentAction> actionBarActions = new HashMap<String, IntentAction>();
	private HashMap<String, Integer> actionBarIndecies = new HashMap<String, Integer>();

	// private TextView textView;

	private ServiceConnection conn = new ServiceConnection() {
		@Override
		public void onServiceDisconnected(ComponentName name) {
			unBindAudioService();
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			quranAudioPlayer = (AudioServiceBinder) service;
			quranAudioPlayer.setAyahCompleteListener(QuranViewActivity.this);
			if (quranAudioPlayer.isPlaying()) {
				onActionPlay();
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// textView = new TextView(this);
		// textView.setText("");
		bindAudioService();
	}

	protected void addActions() {
		super.addActions();
		if (actionBar != null) {
			// actionBar.setTitle("QuranAndroid");
			actionBarActions.put(ACTION_PLAY, getIntentAction(
					ACTION_PLAY, android.R.drawable.ic_media_play));
			actionBarActions.put(ACTION_PAUSE, getIntentAction(
					ACTION_PAUSE, android.R.drawable.ic_media_pause));
			actionBarActions.put(ACTION_NEXT, getIntentAction(
					ACTION_NEXT, android.R.drawable.ic_media_next));
			actionBarActions.put(ACTION_PREVIOUS, getIntentAction(
					ACTION_PREVIOUS, android.R.drawable.ic_media_previous));
			actionBarActions.put(ACTION_STOP, getIntentAction(
					ACTION_STOP, R.drawable.stop));
			actionBarActions.put(ACTION_CHANGE_READER,
					getIntentAction(ACTION_CHANGE_READER, R.drawable.mic));
			actionBarActions.put(ACTION_JUMP_TO_AYAH,
					getIntentAction(ACTION_JUMP_TO_AYAH, R.drawable.jump));
			
			actionBarIndecies.put(ACTION_PLAY, ACTION_BAR_ACTION_PLAY);
			actionBarIndecies.put(ACTION_PAUSE, ACTION_BAR_ACTION_PAUSE);
			actionBarIndecies.put(ACTION_NEXT, ACTION_BAR_ACTION_NEXT);
			actionBarIndecies.put(ACTION_PREVIOUS, ACTION_BAR_ACTION_PREVIOUS);
			actionBarIndecies.put(ACTION_STOP, ACTION_BAR_ACTION_STOP);
			actionBarIndecies.put(ACTION_CHANGE_READER, ACTION_BAR_ACTION_CHANGE_READER);
			actionBarIndecies.put(ACTION_JUMP_TO_AYAH, ACTION_BAR_ACTION_JUMP_TO_AYAH);
			
			onActionStop();
		}
	}

	private IntentAction getIntentAction(String intentAction, int drawable) {
		Intent i = new Intent(this, QuranViewActivity.class);
		i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		i.setAction(intentAction);
		IntentAction action = new IntentAction(this, i, drawable);
		return action;
	}

	@Override
	protected void onNewIntent(Intent intent) {
		// TODO Auto-generated method stub
		super.onNewIntent(intent);
		String action = intent.getAction();
		if (quranAudioPlayer != null && action != null) {
			if (action.equalsIgnoreCase(ACTION_PLAY)) {
				if (quranAudioPlayer.isPaused())
					quranAudioPlayer.resume();
				else {
					lastAyah = getCurrentAudioAyah();
					// soura not totall found
					if (QuranUtils.isSouraAudioFound(lastAyah
							.getQuranReaderId(), lastAyah.getSoura()) < 0) {
						showDownloadDialog(lastAyah);
					} else {
						quranAudioPlayer.enableRemotePlay(false);
						playAudio(lastAyah);
					}
				}
				onActionPlay();
			} else if (action.equalsIgnoreCase(ACTION_PAUSE)) {
				quranAudioPlayer.pause();
				onActionStop();
			} else if (action.equalsIgnoreCase(ACTION_NEXT)) {
				lastAyah = QuranAudioLibrary.getNextAyahAudioItem(this,
						getCurrentAudioAyah());
				if (quranAudioPlayer != null && quranAudioPlayer.isPlaying())
					quranAudioPlayer.play(lastAyah);
			} else if (action.equalsIgnoreCase(ACTION_PREVIOUS)) {
				lastAyah = QuranAudioLibrary.getPreviousAyahAudioItem(this,
						getCurrentAudioAyah());
				if (quranAudioPlayer != null && quranAudioPlayer.isPlaying())
					quranAudioPlayer.play(lastAyah);
			} else if (action.equalsIgnoreCase(ACTION_STOP)) {
				lastAyah = null;
				quranAudioPlayer.stop();
				onActionStop();
			} else if (action.equalsIgnoreCase(ACTION_CHANGE_READER)){
				showChangeReaderDialog();
			}
			else if (action.equalsIgnoreCase(ACTION_JUMP_TO_AYAH)) {
				showJumpToAyahDialog();
			}
		}
	}

	private void onActionPlay() {
		actionBar.removeAllActions();
		for (String action : actionBarIndecies.keySet()) {
			if (ACTION_PLAY.equals(action))
				continue;
			actionBar.addAction(actionBarActions.get(action), actionBarIndecies.get(ACTION_PLAY));
		}
	}

	private void onActionStop() {
		actionBar.removeAllActions();
		actionBar.addAction(actionBarActions.get(ACTION_PLAY), actionBarIndecies.get(ACTION_PLAY));
	}

	private void showDownloadDialog(final AyahItem i) {

		AlertDialog.Builder dialog = new AlertDialog.Builder(this);
		LayoutInflater li = LayoutInflater.from(this);
		final View view = li.inflate(R.layout.dialog_download, null);
		Spinner s = (Spinner) view.findViewById(R.id.spinner);
		if (s != null)
			s.setSelection(getReaderIndex(getQuranReaderId()));
		dialog.setView(view);
		// AlertDialog dialog = new DownloadDialog(this);
		dialog.setMessage("Do you want to download sura");
		dialog.setPositiveButton("Download",
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						// get reader id
						Spinner s = (Spinner) view.findViewById(R.id.spinner);
						lastAyah = i;
						if (s != null) {
							if (s.getSelectedItemPosition() != Spinner.INVALID_POSITION) {
								setReaderId(s.getSelectedItemPosition());
								// reader is not default reader
								if (getQuranReaderId() != i.getQuranReaderId()) {
									lastAyah = QuranAudioLibrary.getAyahItem(
											getApplicationContext(), i
													.getSoura(), i.getAyah(),
											getQuranReaderId());
								}
							}
						}
						downloadSura(getQuranReaderId(), lastAyah
								.getSoura(), lastAyah.getAyah());
						dialog.dismiss();
					}
				});
		dialog.setNeutralButton("Stream",
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						// get reader id
						quranAudioPlayer.enableRemotePlay(true);
						Spinner s = (Spinner) view.findViewById(R.id.spinner);
						lastAyah = i;
						if (s != null) {
							if (s.getSelectedItemPosition() != Spinner.INVALID_POSITION) {								
								setReaderId(s.getSelectedItemPosition());
								// reader is not default reader
								if (getQuranReaderId() != i.getQuranReaderId()) {
									lastAyah = QuranAudioLibrary.getAyahItem(
											getApplicationContext(), i
													.getSoura(), i.getAyah(),
											getQuranReaderId());
								}
							}
						}
						if(lastAyah.getQuranReaderId() != getQuranReaderId())
							lastAyah.setReader(getQuranReaderId());
						quranAudioPlayer.play(lastAyah);
						dialog.dismiss();
					}
				});

		dialog.setNegativeButton("Cancel",
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				});

		AlertDialog diag = dialog.create();
		diag.show();
	}

	private void showJumpToAyahDialog() {
		final Integer[] pageBounds = QuranInfo.getPageBounds(quranPageFeeder
				.getCurrentPagePosition());
		AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
		LayoutInflater li = LayoutInflater.from(this);
		final View view = li.inflate(R.layout.dialog_jump_to_ayah, null);
		
		final Spinner ayatSpinner = (Spinner) view.findViewById(R.id.spinner_ayat);
		final CheckBox checkbox = (CheckBox) view.findViewById(R.id.checkbox_whole_quran);
		int startAyah = pageBounds[1];
		int endAyah = pageBounds[0] == pageBounds[2]? pageBounds[3] : QuranInfo.SURA_NUM_AYAHS[pageBounds[0] - 1];
		initAyatSpinner(ayatSpinner, startAyah, endAyah);
		
		final Spinner surasSpinner = (Spinner) view.findViewById(R.id.spinner_suras);
		initSurasSpinner(surasSpinner, pageBounds[0], pageBounds[2]);
		surasSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

			@SuppressWarnings("unchecked")
			@Override
			public void onItemSelected(AdapterView<?> adapter, View view,
					int position, long id) {
				HashMap<String, String>map = (HashMap<String, String>) adapter.getItemAtPosition(position);
				int suraIndex = Integer.parseInt(map.get("suraId"));
				int startAyah = suraIndex == pageBounds[0] ? pageBounds[1] : 1;
				int endAyah = suraIndex == pageBounds[2]? pageBounds[3] : QuranInfo.SURA_NUM_AYAHS[suraIndex - 1];
				initAyatSpinner(ayatSpinner, startAyah, endAyah);
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				// TODO Auto-generated method stub
				
			}
		});
			
		checkbox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if(isChecked){
					initSurasSpinner(surasSpinner, 1, 114);
					surasSpinner.setSelection(pageBounds[0] - 1);
				}
				else
					initSurasSpinner(surasSpinner, pageBounds[0], pageBounds[2]);
			}
		});
		dialogBuilder.setView(view);
		dialogBuilder.setMessage("Jumo to ayah");
		dialogBuilder.setPositiveButton("Jump",
				new DialogInterface.OnClickListener() {

					@SuppressWarnings("unchecked")
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// get sura
						HashMap<String, String> suraData =
							(HashMap<String, String>) surasSpinner.getSelectedItem();
						int sura = Integer.parseInt(suraData.get("suraId"));
						Log.d("Ayah", "Spinner ayay values " + ayatSpinner.getSelectedItem().toString());
						Integer ayah = (Integer) ayatSpinner.getSelectedItem();
						lastAyah = QuranAudioLibrary.getAyahItem(getApplicationContext(), 
								sura, ayah, getQuranReaderId());
						if (quranAudioPlayer != null && quranAudioPlayer.isPlaying()){
							quranAudioPlayer.stop();
							quranAudioPlayer.play(lastAyah);	
						}
							
					}
				});
		dialogBuilder.setNegativeButton("Cancel", null);
		dialogBuilder.show();

	}

	
	private void initSurasSpinner(final Spinner spinner, int startSura, int endSura){
		String[] from = new String[] {"suraName"};
		int[] to = new int[] {android.R.id.text1 };

		ArrayList<HashMap<String, String>> data = new ArrayList<HashMap<String, String>>();
		for (int i = startSura; i <= endSura; i++) {
			HashMap<String, String> hash = new HashMap<String, String>();
			hash.put("suraName", QuranInfo.getSuraName(i-1));
			hash.put("suraId", ""+i);
			data.add(hash);
		}
		SimpleAdapter sa = new SimpleAdapter(this, data, 
				android.R.layout.simple_spinner_item, from, to);
		sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		SimpleAdapter.ViewBinder viewBinder = new SimpleAdapter.ViewBinder() {
			 
            public boolean setViewValue(View view, Object data,
                    String textRepresentation) {
                TextView textView = (TextView) view;
                textView.setText(textRepresentation);
                return true;
            }
        };
        sa.setViewBinder(viewBinder);
		spinner.setAdapter(sa);

	}
	
	
	private void initAyatSpinner(final Spinner spinner, int startAyah, int endAyah){
		ArrayAdapter<Integer> adapter = new ArrayAdapter<Integer>(this, android.R.layout.simple_spinner_item);
		for(int i = startAyah; i <= endAyah; i++)
			adapter.add(i);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
	}
	
	private void showChangeReaderDialog() {
		AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
		LayoutInflater li = LayoutInflater.from(this);
		final View view = li.inflate(R.layout.dialog_download, null);
		Spinner s = (Spinner) view.findViewById(R.id.spinner);
		s.setSelection(getReaderIndex(getQuranReaderId()));
		dialogBuilder.setView(view);
		dialogBuilder.setMessage("Change quran reader");
		dialogBuilder.setPositiveButton("Change",
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						Spinner s = (Spinner) view.findViewById(R.id.spinner);
						if (s != null
								&& s.getSelectedItemPosition() != Spinner.INVALID_POSITION) {
							setReaderId(s.getSelectedItemPosition());
						}
					}
				});
		dialogBuilder.setNegativeButton("Cancel", null);
		dialogBuilder.show();
	}

	protected void initQuranPageFeeder() {
		if (quranPageFeeder == null) {
			Log.d(TAG, "Quran Feeder instantiated...");
			quranPageFeeder = new QuranPageFeeder(this, quranPageCurler,
					R.layout.quran_page_layout);
		} else {
			quranPageFeeder.setContext(this, quranPageCurler);
		}
	}

	private void unBindAudioService() {
		if (bounded) {
			// Detach our existing connection.
			unbindService(conn);
			if (quranAudioPlayer != null)
				quranAudioPlayer.setAyahCompleteListener(null);
			bounded = false;
		}
	}

	private void bindAudioService() {
		if (!bounded) {
			Intent serviceIntent = new Intent(getApplicationContext(),
					QuranAudioService.class);
			startService(serviceIntent);
			bounded = bindService(serviceIntent, conn, BIND_AUTO_CREATE);
			if (!bounded)
				Toast
						.makeText(this, "can not bind service",
								Toast.LENGTH_SHORT);
		}
	}

	private void playAudio(AyahItem ayah) {
		if (quranAudioPlayer != null) {
			if (ayah == null) {
				Integer[] pageBounds = QuranInfo.getPageBounds(quranPageFeeder
						.getCurrentPagePosition());
				ayah = QuranAudioLibrary.getAyahItem(getApplicationContext(),
						pageBounds[0], pageBounds[1], getQuranReaderId());
			}
			quranAudioPlayer.play(ayah);
		}
	}

	@Override
	public boolean onAyahComplete(AyahItem ayah, AyahItem nextAyah) {
		lastAyah = ayah;
		if (nextAyah.getQuranReaderId() != getQuranReaderId()
				&& quranAudioPlayer != null && quranAudioPlayer.isPlaying()) {
			quranAudioPlayer.stop();
			lastAyah = QuranAudioLibrary.getAyahItem(this, nextAyah.getSoura(),
					nextAyah.getAyah(), getQuranReaderId());
			quranAudioPlayer.play(lastAyah);
			return false;
		}
		int page = QuranInfo.getPageFromSuraAyah(nextAyah.getSoura(), nextAyah
				.getAyah());
		quranPageFeeder.jumpToPage(page);
		return true;
	}

	@Override
	public void onAyahNotFound(AyahItem ayah) {
		lastAyah = ayah;
		showDownloadDialog(ayah);
	}

	@Override
	protected void loadLastNonConfigurationInstance() {
		super.loadLastNonConfigurationInstance();
		Object[] saved = (Object[]) getLastNonConfigurationInstance();
		if (saved != null) {
			Log.d("exp_v", "Adapter retrieved..");
			quranPageFeeder = (QuranPageFeeder) saved[0];
		}
	}

	@Override
	protected void onFinishDownload() {
		super.onFinishDownload();
		if (quranAudioPlayer != null) {
			quranAudioPlayer.enableRemotePlay(false);
			playAudio(lastAyah);
		}
	}

	private int getQuranReaderId() {
		return QuranSettings.getInstance().getLastReader();
	}

	private AyahItem getCurrentAudioAyah() {
		if (lastAyah != null) {
			return lastAyah;
		}
		Integer[] pageBounds = QuranInfo.getPageBounds(quranPageFeeder
				.getCurrentPagePosition());
		return QuranAudioLibrary.getAyahItem(getApplicationContext(),
				pageBounds[0], pageBounds[1], getQuranReaderId());
	}

	private void setReaderId(int readerNamePosition) {
		currentReaderId = getResources().getIntArray(R.array.quran_readers_id)[readerNamePosition];
		QuranSettings.getInstance().setLastReader(currentReaderId);		
	}

	private int getReaderIndex(int readerId) {
		int[] ids = getResources().getIntArray(R.array.quran_readers_id);
		for (int i=0 ; i<ids.length ; i++) {
			if (ids[i] == readerId) {
				return i;
			}
		}
		return 0;
	}

	@Override
	public void onAyahError(AyahItem ayah) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("An error occured");
		builder.setNegativeButton("ok", new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				
			}
		});
		builder.show();
	}

}
