/*
Copyright (C) 2011-2023 Maksim Petrov

Redistribution and use in source and binary forms, with or without
modification, are permitted for widgets, plugins, applications and other software
which communicate with Poweramp application on Android platform.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE FOUNDATION OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.maxmpz.poweramp.player;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.FileNotFoundException;
import org.eclipse.jdt.annotation.Nullable;


public enum PowerampAPIHelper {
    ;
    private static final String TAG = "PowerampAPIHelper";
	private static final boolean LOG = false;
	/** Used to test PA vs older service starting appropach */
	private static final boolean DEBUG_ALWAYS_SEND_TO_SERVICE = false;

	private static String sPowerampPak;
	private static ComponentName sPowerampPSComponentName;
	private static int sPowerampBuild;
	private static ComponentName sApiReceiverComponentName;
	private static ComponentName sApiActivityComponentName;
	private static ComponentName sBrowserServiceComponentName;
	private static ComponentName sScanServiceComponentName;
	private static ComponentName sMilkScanServiceComponentName;


	/**
	 * THREADING: can be called from any thread, though double initialization is possible, but it's OK
	 * @return resolved and cached Poweramp package name or null if it's not installed<br>
	 */
	public static String getPowerampPackageName(final Context context) {
		String pak = PowerampAPIHelper.sPowerampPak;
		if(null == pak) {
			final ComponentName componentName = PowerampAPIHelper.getPlayerServiceComponentNameImpl(context);
			if(null != componentName) {
				pak = PowerampAPIHelper.sPowerampPak = componentName.getPackageName();
			}
		}
		return pak;
	}
	
	/**
	 * THREADING: can be called from any thread, though double initialization is possible, but it's OK
	 * @return resolved and cached Poweramp PlayerService component name, or null if not installed
	 * @deprecated use {@link #getApiReceiverComponentName(Context)}
	 */
	@Deprecated
	public static ComponentName getPlayerServiceComponentName(final Context context) {
		return PowerampAPIHelper.getPlayerServiceComponentNameImpl(context);
	}

	private static ComponentName getPlayerServiceComponentNameImpl(final Context context) {
		ComponentName componentName = PowerampAPIHelper.sPowerampPSComponentName;
		if(null == componentName) {
			try {
				final ResolveInfo info = context.getPackageManager().resolveService(new Intent(PowerampAPI.ACTION_API_COMMAND), 0);
				if(null != info && null != info.serviceInfo) {
					componentName = PowerampAPIHelper.sPowerampPSComponentName = new ComponentName(info.serviceInfo.packageName, info.serviceInfo.name);
				}
			} catch(final Throwable th) {
				Log.e(PowerampAPIHelper.TAG, "", th);
			}
		}
		return componentName;
	}

	/**
	 * THREADING: can be called from any thread, though double initialization is possible, but it's OK
	 * @return resolved and cached Poweramp Media Browser Service component name, or null if not installed
	 */
	public static ComponentName getBrowserServiceComponentName(final Context context) {
		ComponentName componentName = PowerampAPIHelper.sBrowserServiceComponentName;
		if(null == componentName) {
			try {
				final ResolveInfo info = context.getPackageManager().resolveService(new Intent("android.media.browse.MediaBrowserService").setPackage(PowerampAPIHelper.getPowerampPackageName(context)), 0);
				if(null != info && null != info.serviceInfo) {
					componentName = PowerampAPIHelper.sBrowserServiceComponentName = new ComponentName(info.serviceInfo.packageName, info.serviceInfo.name);
				}
			} catch(final Throwable th) {
				Log.e(PowerampAPIHelper.TAG, "", th);
			}
		}
		return componentName;
	}

	/**
	 * THREADING: can be called from any thread, though double initialization is possible, but it's OK
	 * @return resolved and cached Poweramp Media Browser Service component name, or null if not installed
	 */
	public static ComponentName getScannerServiceComponentName(final Context context) {
		ComponentName componentName = PowerampAPIHelper.sScanServiceComponentName;
		if(null == componentName) {
			try {
				final ResolveInfo info = context.getPackageManager().resolveService(new Intent(PowerampAPI.Scanner.ACTION_SCAN_DIRS).setPackage(PowerampAPIHelper.getPowerampPackageName(context)), 0);
				if(null != info && null != info.serviceInfo) {
					componentName = PowerampAPIHelper.sScanServiceComponentName = new ComponentName(info.serviceInfo.packageName, info.serviceInfo.name);
				}
			} catch(final Throwable th) {
				Log.e(PowerampAPIHelper.TAG, "", th);
			}
		}
		return componentName;
	}

	/**
	 * THREADING: can be called from any thread, though double initialization is possible, but it's OK
	 * @return resolved and cached Poweramp milk scanner service component name, or null if not installed
	 */
	public static ComponentName getMilkScannerServiceComponentName(final Context context) {
		ComponentName componentName = PowerampAPIHelper.sMilkScanServiceComponentName;
		if(null == componentName) {
			try {
				final ResolveInfo info = context.getPackageManager().resolveService(new Intent(PowerampAPI.MilkScanner.ACTION_SCAN).setPackage(PowerampAPIHelper.getPowerampPackageName(context)), 0);
				if(null != info && null != info.serviceInfo) {
					componentName = PowerampAPIHelper.sMilkScanServiceComponentName = new ComponentName(info.serviceInfo.packageName, info.serviceInfo.name);
				}
			} catch(final Throwable th) {
				Log.e(PowerampAPIHelper.TAG, "", th);
			}
		}
		return componentName;
	}

	/**
	 * THREADING: can be called from any thread, though double initialization is possible, but it's OK
	 * @return resolved and cached Poweramp API receiver component name, or null if not installed
	 */
	public static ComponentName getApiReceiverComponentName(final Context context) {
		ComponentName componentName = PowerampAPIHelper.sApiReceiverComponentName;
		if(null == componentName) {
			final String pak = PowerampAPIHelper.getPowerampPackageName(context);
			if(null != pak) {
				componentName = PowerampAPIHelper.sApiReceiverComponentName = new ComponentName(pak, PowerampAPI.API_RECEIVER_NAME);
			}
		}
		return componentName;
	}

	/**
	 * NOTE: this is API activity - invisible activity to receive API commands. This one doesn't show any Poweramp UI.
	 * THREADING: can be called from any thread, though double initialization is possible, but it's OK
	 * @return resolved and cached Poweramp API activity component name, or null if not installed
	 */
	public static ComponentName getApiActivityComponentName(final Context context) {
		ComponentName componentName = PowerampAPIHelper.sApiActivityComponentName;
		if(null == componentName) {
			final String pak = PowerampAPIHelper.getPowerampPackageName(context);
			if(null != pak) {
				componentName = PowerampAPIHelper.sApiActivityComponentName = new ComponentName(pak, PowerampAPI.API_ACTIVITY_NAME);
			}
		}
		return componentName;
	}

	/**
	 * THREADING: can be called from any thread, though double initialization is possible, but it's OK
	 * @return resolved and cached Poweramp build number<br>
	 */
	public static int getPowerampBuild(final Context context) {
		if(0 == sPowerampBuild) {
			final String pak = PowerampAPIHelper.getPowerampPackageName(context);
			if(null != pak) {
				try {
					final PackageInfo pi = context.getPackageManager().getPackageInfo(pak, 0);
                    PowerampAPIHelper.sPowerampBuild = 1000 < pi.versionCode ? pi.versionCode / 1000 : pi.versionCode;
				} catch(final Throwable th) {
					Log.e(PowerampAPIHelper.TAG, "", th);
				}
			}
		}
		return PowerampAPIHelper.sPowerampBuild;
	}
	
	/**
	 * THREADING: can be called from any thread<br>
	 * @return intent to send with sendPAIntent
	 */
	public static Intent newAPIIntent(final Context context) {
		return new Intent(PowerampAPI.ACTION_API_COMMAND);
	}


	/**
	 * If we have Poweramp build 855+, send broadcast, otherwise use startForegroundService/startService which may be prone to ANR errors
	 * and are deprecated for Poweramp builds 855+.<br><br>
	 * THREADING: can be called from any thread<br>
	 * @return true if intent was successfully sent, false otherwise
	 */
	public static boolean sendPAIntent(final Context context, final Intent intent) {
		return PowerampAPIHelper.sendPAIntent(context, intent, false);
	}

	/**
	 * If we have Poweramp build 855+, send broadcast, otherwise use startForegroundService/startService which may be prone to ANR errors
	 * and are deprecated for Poweramp builds 855+.<br><br>
	 * THREADING: can be called from any thread
	 * @param sendToActivity if true, we're sending intent to the activity (build 862+)
	 * @return true if intent was successfully sent, false otherwise
	 */
	public static boolean sendPAIntent(final Context context, final Intent intent, final boolean sendToActivity) {
		try {
			final int buildNum = PowerampAPIHelper.getPowerampBuild(context);
			intent.putExtra(PowerampAPI.EXTRA_PACKAGE, context.getPackageName());

			if(PowerampAPIHelper.DEBUG_ALWAYS_SEND_TO_SERVICE) {
				intent.setComponent(PowerampAPIHelper.getPlayerServiceComponentNameImpl(context));
				if(26 <= Build.VERSION.SDK_INT) {
					context.startForegroundService(intent);
				} else {
					context.startService(intent);
				}
				return true;
			}

			if(sendToActivity && 862 <= buildNum) {
				intent.setComponent(PowerampAPIHelper.getApiActivityComponentName(context));
				if(!(context instanceof Activity)) {
					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				}
				context.startActivity(intent);
			} else if(855 <= buildNum) {
				intent.setComponent(PowerampAPIHelper.getApiReceiverComponentName(context));
				context.sendBroadcast(intent);
			} else {
				intent.setComponent(PowerampAPIHelper.getPlayerServiceComponentNameImpl(context));
				if(26 <= Build.VERSION.SDK_INT) {
					context.startForegroundService(intent);
				} else {
					context.startService(intent);
				}
			}
			return true;
		} catch(final Exception ex) {
			Log.e(PowerampAPIHelper.TAG, "intent=" + intent, ex);
			return false;
		}
	}

	@Deprecated
	public static void startPAServiceOld(final Context context, final Intent intent) {
		intent.setComponent(PowerampAPIHelper.getPlayerServiceComponentNameImpl(context));
		if(26 <= Build.VERSION.SDK_INT) {
			context.startForegroundService(intent);
		} else {
			context.startService(intent);
		}
	}

	// WARNING: openFileDescriptor() will return the original image right from the track loaded in Poweramp or
	// a cached image file. The later is more or less under control in terms of size, though, that can be in-folder user provided image without any limits.
	// As for embedded album art, the resulting bitmap can be any size. Poweramp has some upper limits on embed album art, still the decoded image can be very large.
	public static @Nullable Bitmap getAlbumArt(final Context context, @Nullable final Bundle track, final int subsampleWidth, final int subsampleHeight) {
		if(null == track) {
			if(PowerampAPIHelper.LOG) Log.e(PowerampAPIHelper.TAG, "getAlbumArt !track");
			return null;
		}

		final Uri aaUri = PowerampAPI.AA_ROOT_URI.buildUpon().appendEncodedPath("files").appendEncodedPath(Long.toString(track.getLong(PowerampAPI.Track.REAL_ID))).build();

		try(final ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(aaUri, "r")) {
			if(null != pfd) {
				final BitmapFactory.Options opts = new BitmapFactory.Options();
				// If this pfd is pipe, we can't reuse it for decoding, so don't do the subsample then
				if(0 < pfd.getStatSize()) {
					// Get original bitmap size
					opts.inJustDecodeBounds = true;
					BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor(), null, opts);

					// Calculate subsample and load subsampled image
					opts.inJustDecodeBounds = false;
					if(0 < subsampleWidth && 0 < subsampleHeight) {
						opts.inSampleSize = PowerampAPIHelper.calcSubsample(subsampleWidth, subsampleHeight, opts.outWidth, opts.outHeight); // Subsamples images up to 2047x2047, should be safe, though this is up to 16mb per bitmap
					}
				}


				final Bitmap b = BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor(), null, opts);

				if(PowerampAPIHelper.LOG) Log.e(PowerampAPIHelper.TAG, "getAlbumArt aaUri=" + aaUri + " b=" + b);
				if(PowerampAPIHelper.LOG && null != b)
					Log.e(PowerampAPIHelper.TAG, "getAlbumArt w=" + b.getWidth() + " h=" + b.getHeight());

				return b;

			} else if(PowerampAPIHelper.LOG) Log.e(PowerampAPIHelper.TAG, "getAlbumArt no pfd for aaUri=" + aaUri);

		} catch(final FileNotFoundException ex) {
			// OK
			if(PowerampAPIHelper.LOG) Log.e(PowerampAPIHelper.TAG, "getAlbumArt aaUri=" + aaUri, ex);

		} catch(final Throwable th) {
			Log.e(PowerampAPIHelper.TAG, "", th);

		}

		return null;
	}

	// NOTE: maxW/maxH is not actual max, as we just subsample. Output image size will be up to maxW(H)*2 - 1
	private static int calcSubsample(int maxW, int maxH, int outWidth, int outHeight) {
		int sampleSize = 1;
		int nextWidth = outWidth >> 1;
		int nextHeight = outHeight >> 1;
		while(nextWidth >= maxW && nextHeight >= maxH) {
			sampleSize <<= 1;
			nextWidth >>= 1;
			nextHeight >>= 1;
		}
		return sampleSize;
	}

}
