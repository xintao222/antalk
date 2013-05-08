package com.zhuri.talk;

import java.nio.*;
import java.io.IOException;

import com.zhuri.slot.*;
import com.zhuri.util.DEBUG;
import com.zhuri.net.Connector;
import com.zhuri.net.XyConnector;
import com.zhuri.net.IConnectable;
import com.zhuri.slot.IWaitableChannel;
import com.zhuri.net.WaitableSslChannel;
import com.zhuri.talk.protocol.Bind;
import com.zhuri.talk.protocol.Packet;
import com.zhuri.talk.protocol.Stream;
import com.zhuri.talk.protocol.Session;
import com.zhuri.talk.protocol.IQPacket;
import com.zhuri.talk.protocol.Starttls;
import com.zhuri.talk.protocol.PlainSasl;

public class TalkClient {
	final static int WF_RESOLV = 0x00000001;
	final static int WF_HEADER = 0x00000002;
	final static int WF_FEATURE = 0x00000004;
	final static int WF_PROCEED = 0x00000008;
	final static int WF_SUCCESS  = 0x00000010;
	final static int WF_LOOPING  = 0x00000020;
	final static int WF_STARTTLS = 0x00000040;
	final static int WF_CONNECTED = 0x00000080;
	final static int WF_HANDSHAKE = 0x00000100;
	final static int WF_PLAINSASL = 0x00000200;
	final static int WF_LASTSTART = 0x00000400;

	final static int WF_DESTROY = 0x00020000;
	final static int WF_CONFIGURE = 0x00004000;
	final static int WF_CONNECTING = 0x00008000;
	final static int WF_DISCONNECT = 0x00010000;
	final static int WF_SASLFINISH = 0x00020000;
	final static int WF_LASTFINISH = 0x00040000;

	final static int WF_FORCETLS   = 0x10000000;
	final static int WF_ENABLETLS  = 0x20000000;

	final private static String LOG_TAG = "TalkClient";
	final private static String XYHOST  = "112.64.221.141:9418";

	final private int mInterval = 10000;
	final private Connector mConnector = new XyConnector(XYHOST);
	final private OutgoingIQManager mIQManager = new OutgoingIQManager();

	final private SlotTimer mKeepalive = new SlotTimer() {
		public void invoke() {
			DEBUG.Print("time out event");
			routine();
			mKeepalive.reset(mInterval);
			return;
		}
	};

	final private SlotWait mWaitOut = new SlotWait() {
		public void invoke() {
			DEBUG.Print("output event");
			mKeepalive.reset(mInterval);
			routine();
			return;
		}
	};

	final private SlotWait mWaitIn = new SlotWait() {
		public void invoke() {
			DEBUG.Print("input event");
			mKeepalive.reset(mInterval);
			routine();
			return;
		}
	};

	private int mStateFlags = 0;
	private SampleXmlChannel mXmlChannel = null;
	private IWaitableChannel mWaitableChannel = null;
	private boolean stateMatch(int next, int prev) {
		int flags = mStateFlags;
		flags &= (next | prev);
		return (flags == prev);
	}

	public void start() {
		mStateFlags |= WF_CONFIGURE;
		routine();
		return;
	}

	public void updateFeature(Packet packet) {
		return;
	}

	private void routine() {
		if (stateMatch(WF_RESOLV, WF_CONFIGURE)) {
			mWaitableChannel = mConnector;
			mStateFlags |= WF_RESOLV;
		}

		if (stateMatch(WF_CONNECTING, WF_RESOLV)) {
			mConnector.connect("xmpp.l.google.com:5222");
			mKeepalive.reset(mInterval);
			mConnector.waitO(mWaitOut);
			mStateFlags |= WF_CONNECTING;
		}

		if (stateMatch(WF_CONNECTED, WF_CONNECTING)) {
			if (mWaitOut.completed()) {
				mXmlChannel = new SampleXmlChannel(mWaitableChannel);
				mStateFlags |= WF_CONNECTED;
				mWaitOut.clear();
			}
		}

		if (stateMatch(WF_HEADER, WF_CONNECTED)) {
			mXmlChannel.mark(SampleXmlChannel.XML_NEXT);
			mXmlChannel.open("gmail.com");
			mXmlChannel.waitI(mWaitIn);
			mStateFlags |= WF_HEADER;
		}

		if (stateMatch(WF_FEATURE, WF_HEADER)) {
			Packet packet = mXmlChannel.get();
			if (packet.matchTag("features")) {
				DEBUG.Print("features");
				mStateFlags |= WF_FEATURE;
				updateFeature(packet);
			}
		}

		if (stateMatch(WF_STARTTLS, WF_FEATURE)) {
			mXmlChannel.mark(SampleXmlChannel.XML_NEXT);
			Packet packet = new Starttls();
			mStateFlags |= WF_STARTTLS;
			mXmlChannel.waitI(mWaitIn);
			mXmlChannel.put(packet);
		}

		if (stateMatch(WF_PROCEED, WF_STARTTLS)) {
			Packet packet = mXmlChannel.get();
			if (packet.matchTag("proceed")) {
				mStateFlags |= WF_PROCEED;
				DEBUG.Print("proceed");
			}
		}

		if (stateMatch(WF_HANDSHAKE, WF_PROCEED)) {
			WaitableSslChannel sslChannel = new WaitableSslChannel(mConnector);
			try { sslChannel.handshake(); } catch (Exception e) {};

			mWaitOut.clear();
			mWaitableChannel = sslChannel;
			mWaitableChannel.waitO(mWaitOut);

			mStateFlags &= ~(WF_CONNECTED | WF_FEATURE | WF_HEADER);
			mStateFlags |= WF_HANDSHAKE;
		}

		if (stateMatch(WF_PLAINSASL, WF_HANDSHAKE | WF_FEATURE)) {
			mXmlChannel.mark(SampleXmlChannel.XML_NEXT);
			Packet packet = new PlainSasl("pagxir", "LrTqS24IFKc6");
			mStateFlags |= WF_PLAINSASL;
			mXmlChannel.waitI(mWaitIn);
			mXmlChannel.put(packet);
			DEBUG.Print("WF_PLAINSASL");
		}

		if (stateMatch(WF_SASLFINISH, WF_PLAINSASL)) {
			Packet packet = mXmlChannel.get();
			if (packet.matchTag("success") || packet.matchTag("failure")) {
				DEBUG.Print(packet.matchTag("success")? "success": "failue");
				mStateFlags |= (packet.matchTag("success")? WF_SUCCESS: 0x0);
				mStateFlags |= WF_SASLFINISH;
			}
		}

		if (stateMatch(WF_LASTSTART, WF_SUCCESS)) {
			mStateFlags &= ~(WF_CONNECTED | WF_FEATURE | WF_HEADER);
			mWaitableChannel.waitO(mWaitOut);
			mStateFlags |= WF_LASTSTART;
		}

		if (stateMatch(WF_LASTFINISH, WF_LASTSTART| WF_FEATURE)) {
			mXmlChannel.mark(SampleXmlChannel.XML_NEXT);
			mStateFlags |= WF_LASTFINISH;
			initializeFinalLogin();
		}

		if (stateMatch(WF_LOOPING, WF_LASTFINISH)) {
			mWaitIn.clear();
			processIncomingPacket();
			mXmlChannel.waitI(mWaitIn);
		}
	}

	private void initializeFinalLogin() {
		IQPacket packet;

		packet = mIQManager.createPacket(new Bind());
		packet.setType("set");
		mXmlChannel.put(packet);

		packet = mIQManager.createPacket(new Session());
		packet.setType("set");
		mXmlChannel.put(packet);
		return;
	}

	private void processIncomingPacket() {
		Packet packet = mXmlChannel.get();

		while (packet != Packet.EMPTY_PACKET) {
			if (packet.matchTag("presence")) {
				DEBUG.Print("packet TAG: presence");
			} else if (packet.matchTag("message")) {
				DEBUG.Print("packet TAG: message");
			} else if (packet.matchTag("iq")) {
				DEBUG.Print("packet TAG: iq");
			} else {
				DEBUG.Print("unkown TAG: " + packet.getTag());
			}
			mXmlChannel.mark(SampleXmlChannel.XML_NEXT);
			packet = mXmlChannel.get();
		}

		return;
	}
}

