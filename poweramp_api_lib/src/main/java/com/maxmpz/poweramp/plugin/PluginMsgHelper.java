/*
Copyright (C) 2011-2020 Maksim Petrov

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

package com.maxmpz.poweramp.plugin;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

public enum PluginMsgHelper {
    ;

    public static class PluginMsgException extends RuntimeException {
		private static final long serialVersionUID = -5131019933670409856L;

		public PluginMsgException() {}

		public PluginMsgException(@NonNull final String msg) {
			super(msg);
		}
	}

	// sync with plugininterface.h
	public static final int MSG_TAG 			 = 0xF1F2F3F4;
	public static final int HEADER_SIZE_INTS   = 8;
	public static final int MAX_SIZE_INTS		 = 1024;
	public static final int MAX_SIZE_BYTES	 = PluginMsgHelper.MAX_SIZE_INTS * 4;
	public static final int HEADER_SIZE_BYTES  = PluginMsgHelper.HEADER_SIZE_INTS * 4;

	public static int IX_PLUGIN_ID;
	public static int IX_PLUGIN_ID_INT;
	public static final int IX_TAG            = 4 * 4; // 1
	public static final int IX_MSG_ID         = 5 * 4; // 2
	public static final int IX_FLAGS          = 6 * 4; // 3
	public static final int IX_DATA_SIZE      = 7 * 4; // 4
	public static int IX_DATA           = 8 * 4;
	public static int IX_DATA_INT       = 8;

	public static int FLAG_TYPE_MASK            = 0xF000;
	public static int FLAG_TYPE_SYNC_NO_CONTEXT = 0x1000;
	public static int FLAG_TYPE_SEND_OUTSIDE    = 0x2000;
	public static int FLAG_TYPE_BROADCAST       = 0x4000;

	public static int FLAG_BROADCAST_GROUP_MASK = 0x003F;

	public static int MSG_ID_BROADCAST          = -1;


	public static int calcBufferSizeInts(final int desiredSizeInts) {
		return PluginMsgHelper.HEADER_SIZE_INTS + desiredSizeInts;
	}

	public static int calcBufferSizeBytes(final int desiredSizeBytes) {
		return PluginMsgHelper.HEADER_SIZE_BYTES + desiredSizeBytes;
	}

	private static void writeHeader(final int @NonNull[] buf, final int pluginID, final int msgID, final int flags, final int desiredSizeInts) {
		buf[0] = pluginID;
		// 3 ints are zeros (reserved for Poweramp msg header).
		buf[PluginMsgHelper.IX_TAG / 4] = PluginMsgHelper.MSG_TAG;
		buf[PluginMsgHelper.IX_MSG_ID / 4] = msgID;
		buf[PluginMsgHelper.IX_FLAGS / 4] = flags;
		buf[PluginMsgHelper.IX_DATA_SIZE / 4] = desiredSizeInts * 4;
	}

	private static void writeHeader(@NonNull final ByteBuffer buf, final int pluginID, final int msgID, final int flags, final int desiredSizeBytes) {
		buf.putInt(pluginID);
		// 3 ints are zeros (reserved for Poweramp msg header).
		buf.position(PluginMsgHelper.IX_TAG);
		buf.putInt(PluginMsgHelper.MSG_TAG);
		buf.putInt(msgID);
		buf.putInt(flags);
		buf.putInt(desiredSizeBytes);
	}

	public static int @NonNull[] createIntMsgBuffer(final int pluginID, final int msgID, final int flags, final int desiredSizeInts) {
		if(MAX_SIZE_INTS < desiredSizeInts) {
			throw new PluginMsgException("bad desiredSizeInts=" + desiredSizeInts + " MAX_SIZE_INTS=" + PluginMsgHelper.MAX_SIZE_INTS);
		}
		final int[] buf = new int[PluginMsgHelper.calcBufferSizeInts(desiredSizeInts)];
        PluginMsgHelper.writeHeader(buf, pluginID, msgID, flags, desiredSizeInts);
		return buf;
	}

	/**
	 * NOTE: returned ByteBuffer is positioned to the first data position<br>
	 * NOTE: direct buffer makes no sense in our case and is slower<br>
	 */
	public static @NonNull ByteBuffer createBufferMsgBuffer(final int pluginID, final int msgID, final int flags, final int desiredSizeBytes) {
		if(MAX_SIZE_BYTES < desiredSizeBytes) {
			throw new PluginMsgException("bad desiredSizeBytes=" + PluginMsgHelper.MAX_SIZE_BYTES + " MAX_SIZE_BYTES=" + PluginMsgHelper.MAX_SIZE_BYTES);
		}
		final ByteBuffer buf = ByteBuffer.allocate(PluginMsgHelper.calcBufferSizeBytes(desiredSizeBytes));
		buf.order(ByteOrder.LITTLE_ENDIAN);
        PluginMsgHelper.writeHeader(buf, pluginID, msgID, flags, desiredSizeBytes);
		return buf;
	}

	public static @NonNull String msgBufferAsString(final int @Nullable[] buf) {
		if(null == buf) {
			return "null";
		}
		if(HEADER_SIZE_INTS > buf.length) {
			throw new PluginMsgException("bad buf length=" + buf.length);
		}
		return PluginMsgHelper.toString(buf);
	}

	public static @NonNull String msgBufferAsString(@NonNull final ByteBuffer buf) {
		if(HEADER_SIZE_BYTES > buf.capacity()) {
			throw new PluginMsgException("bad buf capacity=" + buf.capacity());
		}
		final int pos = buf.position();
		buf.position(0);
		final IntBuffer intBuf = buf.asIntBuffer();
		buf.position(pos);
		final int[] ar = new int[intBuf.capacity()];
		intBuf.get(ar);
		return PluginMsgHelper.toString(ar);
	}

	@SuppressWarnings("null")
	private static @NonNull String toString(final int @NonNull[] array) {
		if (null == array) {
			return "null";
		}
		if (0 == array.length) {
			return "[]";
		}
		final StringBuilder sb = new StringBuilder(array.length * 6);
		sb.append('[');
		sb.append(array[0]);
		for (int i = 1; i < array.length; i++) {
			sb.append(", 0x");
			sb.append(Integer.toHexString(array[i]));
		}
		sb.append(']');
		return sb.toString();
	}
}
