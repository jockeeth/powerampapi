package com.poweramp.v3.vispresets.sample;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import com.maxmpz.poweramp.player.PowerampAPI;
import com.maxmpz.poweramp.player.PowerampAPIHelper;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;


public class InfoActivity extends Activity {
	private static final String TAG = "InfoActivity";
	private static final boolean LOG = true;

	private static final int REQUEST_ACCESS_MILK_PRESETS = 1;

	private final ArrayList<String> mPushedFiles = new ArrayList<>();


	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_info);

		final EditText edit = this.findViewById(R.id.edit);

		// Inject some preset as text
		try(final InputStreamReader isr = new InputStreamReader(this.getResources().getAssets().open("milk_presets/Example - example-bars-vertical.milk"), StandardCharsets.UTF_8)) {
			StringBuilder out = new StringBuilder();
			int charsRead;
			final int bufferSize = 1024;
			char[] buffer = new char[bufferSize];
			while(0 < (charsRead = isr.read(buffer, 0, buffer.length))) {
				out.append(buffer, 0, charsRead);
			}
			edit.setText(out);

		} catch(final IOException e) {
			Log.e(InfoActivity.TAG, "", e);
		}
	}

	/**
	 * This opens Poweramp and rescans appropriate preset APK - as specified in {@link PowerampAPI.Settings#EXTRA_VIS_PRESETS_PAK} extra
	 */
	public void startWithVisPresets(final View view) {
		final Intent intent = new Intent(Intent.ACTION_MAIN)
				.setClassName(PowerampAPIHelper.getPowerampPackageName(this), PowerampAPI.ACTIVITY_STARTUP)
				.putExtra(PowerampAPI.Settings.EXTRA_VIS_PRESETS_PAK, this.getPackageName());
        this.startActivity(intent);
        this.finish();
	}

	/**
	 * This opens Poweramp settings and scrolls to the appropriate preset APK - as specified in {@link PowerampAPI.Settings#EXTRA_VIS_PRESETS_PAK} extra,
	 * but this doesn't rescan presets
	 */
	public void openPowerampVisSettings(final View view) {
		final Intent intent = new Intent(Intent.ACTION_MAIN)
				.setClassName(PowerampAPIHelper.getPowerampPackageName(this), PowerampAPI.Settings.ACTIVITY_SETTINGS)
				.putExtra(PowerampAPI.Settings.EXTRA_OPEN, PowerampAPI.Settings.OPEN_VIS)
				.putExtra(PowerampAPI.Settings.EXTRA_VIS_PRESETS_PAK, this.getPackageName()) // If vis_presets_pak specified for open/theme, will scroll presets list to this apk entry
				;
        this.startActivity(intent);
        this.finish();
	}

	/**
	 * This asks Poweramp to rescan all presets. At this moment (build 867) all presets are rescanned, not just the given package.<br>
	 * This works only if current application is foreground, meaning this can't be used from the background service (Android 8+), as
	 * Android will block background service execution.
	 */
	public void rescanVisPresets(final View view) {
		final Intent intent = new Intent(PowerampAPI.MilkScanner.ACTION_SCAN)
				.setComponent(PowerampAPIHelper.getMilkScannerServiceComponentName(this))
				.putExtra(PowerampAPI.MilkScanner.EXTRA_CAUSE, "Manual rescan")
				.putExtra(PowerampAPI.MilkScanner.EXTRA_PACKAGE, this.getPackageName());
        this.startService(intent);
	}

	public void sendPreset(final View view) {
		// Issue SET_VIS_PRESET

		final Intent intent = new Intent(PowerampAPI.ACTION_API_COMMAND)
				.setComponent(PowerampAPIHelper.getApiActivityComponentName(this))
				.putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.SET_VIS_PRESET)
				.putExtra(PowerampAPI.EXTRA_NAME, "VisPresetsExample - Test Preset.milk")
				.putExtra(PowerampAPI.EXTRA_DATA, ((EditText) this.findViewById(R.id.edit)).getText().toString()); // NOTE: strictly String type is expected
        this.startActivity(intent);

		// NOTE: we can't immediately do same action/component startActivity immediately, so delay it a bit via post
		new Handler(Looper.getMainLooper()).post(new Runnable() {
			@Override
			public void run() {
				Intent intent;
				// Let's force  vis mode to VIS + UI

				// NOTE: Poweramp won't change interface in reflection to just changing this preference. But as we're currently inside other activity,
				// re-opening Poweramp UI will re-read this preference. Though, this may fail for e.g. split screen when both activities are always on top
				final Bundle request = new Bundle();
				request.putInt("vis_mode", PowerampAPI.Settings.PreferencesConsts.VIS_MODE_VIS_W_UI); // See PowerampAPI.Settings.Preferences
                InfoActivity.this.getContentResolver().call(PowerampAPI.ROOT_URI, PowerampAPI.CALL_SET_PREFERENCE, null, request);

				// Now open Poweramp UI main screen

				intent = new Intent(PowerampAPI.ACTION_OPEN_MAIN)
						.setClassName(PowerampAPIHelper.getPowerampPackageName(InfoActivity.this), PowerampAPI.ACTIVITY_STARTUP)
				;
                InfoActivity.this.startActivity(intent);

				// And command it to play something

				intent = new Intent(PowerampAPI.ACTION_API_COMMAND)
						.setComponent(PowerampAPIHelper.getApiActivityComponentName(InfoActivity.this))
						.putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.RESUME)
				;
                InfoActivity.this.startActivity(intent);
			}
		});
	}

	public void listMilkPresets(final View view) {
		if(23 > Build.VERSION.SDK_INT) {
			// We shouldn't do this for Androids below 10. Instead directly access files as needed
			// Poweramp still supports this functionality for lower Androids, but due to the permission access used, we may ask it directly
			// here for Android 5
            this.listMilkPresetsImpl();
			return;
		}

		if(PackageManager.PERMISSION_GRANTED != checkSelfPermission(PowerampAPI.PERMISSION_ACCESS_MILK_PRESETS)) {
			if(InfoActivity.LOG) Log.w(InfoActivity.TAG, "listMilkPresets requesting permission");
            this.requestPermissions(new String[] { PowerampAPI.PERMISSION_ACCESS_MILK_PRESETS }, InfoActivity.REQUEST_ACCESS_MILK_PRESETS);
		} else {
            this.listMilkPresetsImpl();
		}
	}

	@Override
	public void onRequestPermissionsResult(final int requestCode, final String[] permissions, final int[] grantResults) {
		if(REQUEST_ACCESS_MILK_PRESETS == requestCode && null != grantResults && 0 < grantResults.length
				&& PackageManager.PERMISSION_GRANTED == grantResults[0]
		) {
			if(InfoActivity.LOG) Log.w(InfoActivity.TAG, "onRequestPermissionsResult PERMISSION_GRANTED");
            this.listMilkPresetsImpl();

		} else if(InfoActivity.LOG) Log.e(InfoActivity.TAG, "onRequestPermissionsResult !PERMISSION_GRANTED requestCode=" + requestCode +
								" permissions=" + Arrays.toString(permissions) + " grantResults=" + Arrays.toString(grantResults));
	}

	private String getExtension(final String filename) {
		final int lastDot = filename.lastIndexOf('.');
		if(-1 == lastDot || lastDot == filename.length() - 1) {
			return "";
		}
		return filename.substring(lastDot + 1);
	}

	@SuppressWarnings("CommentedOutCode")
	public void listMilkPresetsImpl() {
		if(InfoActivity.LOG) Log.w(InfoActivity.TAG, "listMilkPresetsImpl");
		ArrayList<String> presets = new ArrayList<>();
		ArrayList<String> textures = new ArrayList<>();
		ArrayList<String> zips = new ArrayList<>();

		// The path parameter also accepts glob patterns, e.g. content://com.maxmpz.audioplayer.milk_presets/*.milk,
		// To quickly query for the single file, you can also use uris like: content://com.maxmpz.audioplayer.milk_presets/single_file
		// NOTE: cols are always ignored, the following columns are always returned:
		//   Document.COLUMN_DISPLAY_NAME, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_SIZE,
		// NOTE: ? symbol requires escaping (%3F)

		final Uri uri = PowerampAPI.MILK_PRESETS_URI;
		//Uri uri = Uri.parse("content://com.maxmpz.audioplayer.milk_presets");
		//Uri uri = PowerampAPI.MILK_PRESETS_URI.buildUpon().appendPath("*.milk").build();
		//Uri uri = PowerampAPI.MILK_PRESETS_URI.buildUpon().appendPath("*.{jpg|jpeg|png|tga|bmp}").build();
		//Uri uri = PowerampAPI.MILK_PRESETS_URI.buildUpon().appendPath("file.???").build();
		//Uri uri = PowerampAPI.MILK_PRESETS_URI.buildUpon().appendPath("*.???").build();

		try(final Cursor c = this.getContentResolver().query(uri, null, null, null, null)) {
			while(null != c && c.moveToNext()) {
				final String fileName = c.getString(0);
				final long lastModified = c.getLong(1);
				final long size = c.getLong(2);

				if(InfoActivity.LOG) Log.w(InfoActivity.TAG, "listMilkPresetsImpl fileName=" + fileName + " lastModified=" + lastModified + " size=" + size);

				final String ext = this.getExtension(fileName).toLowerCase(Locale.ROOT); // NOTE: Poweramp is not case sensitive for presets
				switch(ext) {
					case "zip":
						Log.w(InfoActivity.TAG, "listMilkPresetsImpl FOUND zip=" + fileName);
						zips.add(fileName);
						break;
					case "prjm":
					case "milk":
						Log.w(InfoActivity.TAG, "listMilkPresetsImpl FOUND milk preset=" + fileName);
						presets.add(fileName);
						break;
					case "jpeg":
					case "jpg":
					case "png":
					case "tga":
					case "bmp":
						Log.w(InfoActivity.TAG, "listMilkPresetsImpl FOUND texture=" + fileName);
						textures.add(fileName);
						break;
				}
			}

			new AlertDialog.Builder(this)
					.setTitle("listMilkPresets")
					.setMessage(
							"Presets: " + presets.size() + "\n" +
							"Zips: " + zips.size() + "\n" +
							"Textures: " + textures.size() + "\n"
					)
					.setPositiveButton(android.R.string.ok, null)
					.show();

		} catch(final Throwable th) {
			Log.e(InfoActivity.TAG, "", th);
		}
	}


	public void pushFile(final View view) {
		// Assume we asked for permission earlier in list milk presets
		if(23 <= Build.VERSION.SDK_INT && PackageManager.PERMISSION_GRANTED != checkSelfPermission(PowerampAPI.PERMISSION_ACCESS_MILK_PRESETS)) {
			Log.e(InfoActivity.TAG, "pushFile !permission");
			return;
		}

		final String newFile = "TestPreset - " + System.currentTimeMillis() + ".milk";

		final Uri uri = PowerampAPI.MILK_PRESETS_URI.buildUpon().appendPath(newFile).build();
		try(final ParcelFileDescriptor pfd = this.getContentResolver().openFileDescriptor(uri, "wt")) {
			if(null != pfd) {
				try(final OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(pfd.getFileDescriptor()), StandardCharsets.UTF_8)) {
					final EditText edit = this.findViewById(R.id.edit);
					writer.write(edit.getText().toString());
				}
			}

			// Ask Poweramp to rescan
            this.rescanVisPresets(null);

            this.mPushedFiles.add(newFile);

			new AlertDialog.Builder(this)
					.setTitle("pushFile")
					.setMessage("Pushed file=" + newFile)
					.setPositiveButton(android.R.string.ok, null)
					.show();

		} catch(final Throwable th) {
			Log.e(InfoActivity.TAG, "", th);
		}
	}
	
	public void deleteFile(final View view) {
		// Assume we asked for permission earlier in list milk presets
		if(23 <= Build.VERSION.SDK_INT && PackageManager.PERMISSION_GRANTED != checkSelfPermission(PowerampAPI.PERMISSION_ACCESS_MILK_PRESETS)) {
			Log.e(InfoActivity.TAG, "deleteFile !permission");
			return;
		}

		if(0 == mPushedFiles.size()) { // We need some files previously pushed in this app session
			new AlertDialog.Builder(this)
					.setTitle("deleteFile")
					.setMessage("No pushed files to delete yet")
					.setPositiveButton(android.R.string.ok, null)
					.show();
			return;
		}

		final String fileToDelete = this.mPushedFiles.get(this.mPushedFiles.size() - 1); // Delete last file pushed
        this.mPushedFiles.remove(this.mPushedFiles.size() - 1);

		final Uri uri = PowerampAPI.MILK_PRESETS_URI.buildUpon().appendPath(fileToDelete).build();
		try{
			final int deleted = this.getContentResolver().delete(uri, null, null);
			if(0 != deleted) {
				// Ask Poweramp to rescan
                this.rescanVisPresets(null);
			}
			new AlertDialog.Builder(this)
					.setTitle("deleteFile")
					.setMessage(0 != deleted ? "Deleted file=" + fileToDelete : "No files deleted")
					.setPositiveButton(android.R.string.ok, null)
					.show();

		} catch(final Throwable th) {
			Log.e(InfoActivity.TAG, "", th);
		}
	}

}
