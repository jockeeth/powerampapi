package com.poweramp.v3.sampleskin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

public class SkinInfoActivity extends Activity {
	private static final String TAG = "SkinInfoActivity";

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_skin_info);
	}

	/**
	 * @return resolved Poweramp package name or null if not installed
	 * NOTE: can be called from any thread, though double initialization is possible, but it's OK
	 */
	public static String getPowerampPackageName(final Context context) {
		try {
			final ResolveInfo info = context.getPackageManager().resolveService(new Intent("com.maxmpz.audioplayer.API_COMMAND"), 0);
			if(null != info && null != info.serviceInfo) {
				return info.serviceInfo.packageName;
			}
		} catch(final Throwable th) {
			Log.e(SkinInfoActivity.TAG, "", th);
		}
		return null;
	}

	public void startWithSampleSkin(final View view) {
		final String pak = SkinInfoActivity.getPowerampPackageName(this);
		if(null == pak) {
			Toast.makeText(this, R.string.skin_poweramp_not_installed, Toast.LENGTH_LONG).show();
			return;
		}
		final Intent intent = new Intent(Intent.ACTION_MAIN)
			.setClassName(pak, "com.maxmpz.audioplayer.StartupActivity")
			.putExtra("theme_pak", this.getPackageName())
			.putExtra("theme_id", R.style.SampleSkin);
        this.startActivity(intent);
		//Toast.makeText(this, R.string.skin_applied_msg, Toast.LENGTH_LONG).show(); // Enable toast if needed
        this.finish();
	}

	public void startWithSampleAAASkin(final View view) {
		final String pak = SkinInfoActivity.getPowerampPackageName(this);
		if(null == pak) {
			Toast.makeText(this, R.string.skin_poweramp_not_installed, Toast.LENGTH_LONG).show();
			return;
		}
		final Intent intent = new Intent(Intent.ACTION_MAIN)
			.setClassName(pak, "com.maxmpz.audioplayer.StartupActivity")
			.putExtra("theme_pak", this.getPackageName())
			.putExtra("theme_id", R.style.SampleSkinAAA);
        this.startActivity(intent);
		//Toast.makeText(this, R.string.skin_applied_msg, Toast.LENGTH_LONG).show(); // Enable toast if needed
        this.finish();
	}

	public void openPowerampThemeSettings(final View view) {
		final String pak = SkinInfoActivity.getPowerampPackageName(this);
		if(null == pak) {
			Toast.makeText(this, R.string.skin_poweramp_not_installed, Toast.LENGTH_LONG).show();
			return;
		}
		final Intent intent = new Intent(Intent.ACTION_MAIN)
			.setClassName(pak, "com.maxmpz.audioplayer.SettingsActivity")
			.putExtra("open", "theme")
			.putExtra("theme_pak", this.getPackageName()) // If theme_pak/theme_id specified for open/theme, will scroll to/opens this skins settings
			.putExtra("theme_id", R.style.SampleSkin);
        this.startActivity(intent);
        this.finish();
	}

}
