package com.maxmpz.powerampproviderexample;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.maxmpz.poweramp.player.PowerampAPI;
import com.maxmpz.poweramp.player.PowerampAPIHelper;

public class MainActivity extends Activity {
	private static final String TAG = "MainActivity";
	private static final boolean LOG = true;


	private final BroadcastReceiver mScanEventsReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			((TextView) MainActivity.this.findViewById(R.id.status)).setText(intent.getAction());
		}
	};

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_main);

		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(PowerampAPI.Scanner.ACTION_DIRS_SCAN_STARTED);
		intentFilter.addAction(PowerampAPI.Scanner.ACTION_DIRS_SCAN_FINISHED);
		intentFilter.addAction(PowerampAPI.Scanner.ACTION_FAST_TAGS_SCAN_FINISHED);
		intentFilter.addAction(PowerampAPI.Scanner.ACTION_TAGS_SCAN_STARTED);
		intentFilter.addAction(PowerampAPI.Scanner.ACTION_TAGS_SCAN_FINISHED);
        this.registerReceiver(this.mScanEventsReceiver, intentFilter);
	}

	public void openPAMusicFolders(final View view) {
		if(MainActivity.LOG) Log.w(MainActivity.TAG, "openPAMusicFolders");

		final Intent intent = new Intent();
		intent.setComponent(new ComponentName(PowerampAPIHelper.getPowerampPackageName(this), PowerampAPI.ACTIVITY_SETTINGS));
		intent.putExtra(PowerampAPI.Settings.EXTRA_OPEN_PATH, "library/music_folders_button");
		intent.putExtra(PowerampAPI.Settings.EXTRA_NO_BACKSTACK, true);
		intent.putExtra(PowerampAPI.EXTRA_PACKAGE, this.getPackageName());
        this.startActivity(intent);
	}

	// NOTE: depending on the "background" state of this app and Poweramp some methods may work or not in Android 8+ due to the background limitation.
	// As ACTION_SCAN_* actions rely on service running in Poweramp, they won't be executed if Poweramp itself is in the background.
	// sendScanThisAct below calls Poweramp via intermediate activity and that will allow scanning even if Poweramp is in the background, but
	// starting such activity from background application may be not possible.
	// It's also possible to start scan via startService (not demonstrated here) - subject to the similar limitations - can't be started from the background app.

	public void sendScan(final View view) {
		if(MainActivity.LOG) Log.w(MainActivity.TAG, "sendScan");
		final Intent intent = new Intent(PowerampAPI.Scanner.ACTION_SCAN_DIRS)
			.putExtra(PowerampAPI.Scanner.EXTRA_CAUSE, this.getPackageName() + " rescan")
			.putExtra(PowerampAPI.EXTRA_PACKAGE, this.getPackageName());

		try {
			// This won't work if Poweramp is on the background (not playing or Poweramp UI is not visible)
			//intent.setComponent(PowerampAPIHelper.getApiReceiverComponentName(this))
			//sendBroadcast(intent);

			// This won't work if this app or Poweramp is on the background (may throw)
			//intent.setComponent(PowerampAPIHelper.getScannerServiceComponentName(this));
			//startService(intent);

			// This won't work if started from background service, but works fine for starting from the activity
			intent.setComponent(PowerampAPIHelper.getApiActivityComponentName(this));
            this.startActivity(intent);

		} catch(final Throwable th) {
			Log.w(MainActivity.TAG, "", th);
		}
	}

	public void sendScanThis(final View view) {
		if(MainActivity.LOG) Log.w(MainActivity.TAG, "sendScanThis");
		final Intent intent = new Intent(PowerampAPI.Scanner.ACTION_SCAN_DIRS)
			.putExtra(PowerampAPI.Scanner.EXTRA_CAUSE, this.getPackageName() + " rescan")
			.putExtra(PowerampAPI.Scanner.EXTRA_PROVIDER, "com.maxmpz.powerampproviderexample") // Our provider authority (matches pak name in this case)
			.putExtra(PowerampAPI.EXTRA_PACKAGE, this.getPackageName())
			.setComponent(PowerampAPIHelper.getApiActivityComponentName(this));
		try {
            this.startActivity(intent);
		} catch(final Throwable th) {
			Log.w(MainActivity.TAG, "", th);
		}
	}

	public void sendScanThisSubdir(final View view) {
		if(MainActivity.LOG) Log.w(MainActivity.TAG, "sendScanThisSubdir");
		final Intent intent = new Intent(PowerampAPI.Scanner.ACTION_SCAN_DIRS)
				.putExtra(PowerampAPI.Scanner.EXTRA_CAUSE, this.getPackageName() + " rescan")
				.putExtra(PowerampAPI.Scanner.EXTRA_PROVIDER, "com.maxmpz.powerampproviderexample") // Our provider authority (matches pak name in this case)
				// This rescans everything under root1
				// The path format is {@code /opaque-treeId/opaque-documentId/}:
				// - opaque-treeId is Uri.encoded treeId as returned from tree uri by Uri.encode(DocumentsContract.getTreeDocumentId(...))
				// - opaque-documentId is Uri.encoded documentId as returned from documentId uri by Uri.encode(DocumentsContract.getDocumentId(...))
				// Poweramp uses GLOB and just adds "*" to the EXTRA_PATH, thus, in our case, Poweramp will search for root1/* folders/files.
				// Last slash is required here so we avoid matching e.g. root123/.
				.putExtra(PowerampAPI.Scanner.EXTRA_PATH, "root1/")
				.putExtra(PowerampAPI.EXTRA_PACKAGE, this.getPackageName())
				//.putExtra(PowerampAPI.Scanner.EXTRA_FAST_SCAN, true) // If specified, Poweramp will only scan tracks if parent folder lastModified changed
				.setComponent(PowerampAPIHelper.getApiActivityComponentName(this));
		try {
            this.startActivity(intent);
		} catch(final Throwable th) {
			Log.w(MainActivity.TAG, "", th);
		}
	}

	public void sendScanThisSubdir2(final View view) {
		if(MainActivity.LOG) Log.w(MainActivity.TAG, "sendScanThisSubdir2");
		final Intent intent = new Intent(PowerampAPI.Scanner.ACTION_SCAN_DIRS)
				.putExtra(PowerampAPI.Scanner.EXTRA_CAUSE, this.getPackageName() + " rescan")
				.putExtra(PowerampAPI.Scanner.EXTRA_PROVIDER, "com.maxmpz.powerampproviderexample") // Our provider authority (matches pak name in this case)
				// This rescans everything under root1/Folder2 subpath.
				// The path format is {@code /opaque-treeId/opaque-documentId/}:
				// - opaque-treeId is Uri.encoded treeId as returned from tree uri by Uri.encode(DocumentsContract.getTreeDocumentId(...))
				// - opaque-documentId is Uri.encoded documentId as returned from documentId uri by Uri.encode(DocumentsContract.getDocumentId(...))
				// Poweramp uses GLOB and just adds "*" to the EXTRA_PATH.
				// Last slash is not added here, as we want to match  root1/root1%2FFolder2*
				// e.g. for file: @com.maxmpz.powerampproviderexample/root1/root1%2FFolder2%2Fdubstep-8.mp3.
				// Again, the EXTRA_PATH depends on how THIS provider defines and exposes paths/documentIds
				.putExtra(PowerampAPI.Scanner.EXTRA_PATH, Uri.encode("root1") + "/" + Uri.encode("root1/Folder2"))
				.putExtra(PowerampAPI.EXTRA_PACKAGE, this.getPackageName())
				//.putExtra(PowerampAPI.Scanner.EXTRA_FAST_SCAN, true) // If specified, Poweramp will only scan tracks if parent folder lastModified changed
				.setComponent(PowerampAPIHelper.getApiActivityComponentName(this));
		try {
            this.startActivity(intent);
		} catch(final Throwable th) {
			Log.w(MainActivity.TAG, "", th);
		}
	}

	@Override
	protected void onDestroy() {
		try {
            this.unregisterReceiver(this.mScanEventsReceiver);
		} catch(final Throwable ignored) { }
		super.onDestroy();
	}
}
