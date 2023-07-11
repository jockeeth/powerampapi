/*
Copyright (C) 2011-2021 Maksim Petrov

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

package com.maxmpz.poweramp.apiexample;

import com.maxmpz.poweramp.player.PowerampAPI;
import com.maxmpz.poweramp.player.PowerampAPIHelper;

import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

@SuppressWarnings("deprecation")
public class FoldersActivity extends ListActivity implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {
	private static final String TAG = "FoldersActivity";

	@Override
	protected void onCreate(final Bundle savedInstanceState){
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_folders);

		final Cursor c = getContentResolver().query(PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("folders").build(),
				new String[]{ "folders._id AS _id", "folders.name AS name", "folders.parent_name AS parent_name" }, null, null, "folders.name COLLATE NOCASE");
		if(null != c) this.startManagingCursor(c);

		final SimpleCursorAdapter adapter = new SimpleCursorAdapter(
				this, // Context.
				android.R.layout.two_line_list_item,
				c,
				new String[] { "name", "parent_name" },
				new int[] {android.R.id.text1, android.R.id.text2});
        this.setListAdapter(adapter);

		final ListView list = this.findViewById(android.R.id.list);
		list.setOnItemClickListener(this);
		list.setOnItemLongClickListener(this);
	}

	@Override
	public boolean onItemLongClick(final AdapterView<?> arg0, final View arg1, final int arg2, final long id) {
		Log.w(FoldersActivity.TAG, "folder long press=" + id);

		final Uri.Builder uriB = PowerampAPI.ROOT_URI.buildUpon()
				.appendEncodedPath("folders")
				.appendEncodedPath(Long.toString(id))
				.appendQueryParameter(PowerampAPI.PARAM_SHUFFLE, Integer.toString(PowerampAPI.ShuffleMode.SHUFFLE_SONGS));

		PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND)
				.putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.OPEN_TO_PLAY)
				.setData(uriB.build()),
				MainActivity.FORCE_API_ACTIVITY
		);
        this.finish();
		return true;
	}

	@Override
	public void onItemClick(final AdapterView<?> arg0, final View arg1, final int arg2, final long id) {
		Log.w(FoldersActivity.TAG, "folder press=" + id);

		final Uri filesUri = PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("folders").appendEncodedPath(Long.toString(id)).appendEncodedPath("files").build();

        this.startActivity(new Intent(this, TrackListActivity.class).setData(filesUri));
	}
}
