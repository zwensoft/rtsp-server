package com.sengled.cloud.mediaserver.rtsp.rtp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.Charset;

import jlibrtp.Participant;
import jlibrtp.RTPSession;
import jlibrtp.RtcpPkt;
import jlibrtp.RtcpPktBYE;
import jlibrtp.RtcpPktSR;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sengled.cloud.mediaserver.rtsp.MediaStream;
import com.sengled.cloud.mediaserver.rtsp.NtpTime;
import com.sengled.cloud.mediaserver.rtsp.Rational;
import com.sengled.cloud.mediaserver.rtsp.interleaved.RtpPkt;

/**
 * RTP over tcp
 * 
 * @author 陈修恒
 * @date 2016年4月28日
 */
public class InterLeavedRTPSession extends RTPSession {
	private static final Logger logger = LoggerFactory
			.getLogger(InterLeavedRTPSession.class);

	private MediaStream mediaStream;
	private Channel channel;
	private int rtpChannel;

	private NtpTime ntpTime;
	private long playingTimestamp = -1;
	Participant outPart;

	public InterLeavedRTPSession(MediaStream mediaStream, Channel channel,
			int rtpChannel, int rtcpChannel) {
		super(InterLeavedParticipantDatabase.FACTORY);

		this.mediaStream = mediaStream;
		this.channel = channel;
		this.rtpChannel = rtpChannel;
		this.generateCNAME();
		this.generateSsrc();

		this.outPart = new Participant(ssrc()); // rtp session use as output
												// part
		this.rtcpSession = new InterLeavedRTCPSession(rtcpChannel);
	}

	public long getPlayingTimestamp() {
		return playingTimestamp;
	}

	public long getPlayingTimeMillis() {
		long ntpTimestamp = playingTimestamp;
		return getNtpTimeMillis(ntpTimestamp);
	}

	public long getNtpTimeMillis(long ntpTimestamp) {
		Rational streamTimeUnit = mediaStream.getTimeUnit();
		if (null == ntpTime) {
			return Rational.$_1_000.convert(ntpTimestamp, streamTimeUnit);
		} else {
			long duration = ntpTimestamp - ntpTime.getRtpTime();
			return ntpTime.getNtpTimeMillis()
					+ Rational.$_1_000.convert(duration, streamTimeUnit);
		}
	}

	public void setNtpTime(NtpTime ntpTime) {
		this.ntpTime = ntpTime;
	}

	public void sendRtpPkt(boolean isNewFrame, RtpPkt rtpObj,
			GenericFutureListener<? extends Future<? super Void>> onComplete) {
		// 更新播放时间
		this.playingTimestamp = rtpObj.getTimestamp();
		if (isNewFrame) {
			sendRtcpPktSRIfNeed(rtpObj.getTimestamp());
		}

		// 统计流量
		this.sentOctetCount += rtpObj.dataLength();
		if (isNewFrame) {
			this.sentPktCount++;
		}

		// 依次发送 rtp 包
		int payloadLength;
		ByteBufAllocator alloc = channel().alloc();
		payloadLength = rtpObj.content().readableBytes();

		final int nextSeqNo = 0xFFFF & (outPart.lastSeqNumber + 1);
		outPart.lastSeqNumber = nextSeqNo;

		if (outPart.firstSeqNumber < 0) {
			outPart.firstSeqNumber = nextSeqNo;
		}

		ByteBuf payload = alloc.buffer(4 + payloadLength);
		payload.writeByte('$');
		payload.writeByte(rtpChannel());
		payload.writeShort(payloadLength);

		rtpObj.setSeqNumber(nextSeqNo);
		rtpObj.ssrc(ssrc());
		payload.writeBytes(rtpObj.content()); // 应为修改了 ssrc 和 seq, 所以只能用拷贝

		writeAndFlush(payload, onComplete);
	}


	private void sendRtcpPktSRIfNeed(long rtpTs) {
		boolean hasNtpTime = null != ntpTime
				&& ntpTime.getRtpTime() <= rtpTs;
		if (hasNtpTime && ntpTime.getNtpTs1() - outPart.lastNtpTs1 > 5) {
			long sentNtpTs1 = sendRtcpPktSR(rtpTs);
			outPart.lastNtpTs1 = sentNtpTs1;
		}
	}

	/**
	 * send Rtcp SR
	 * @param rtpTs
	 * @return the NTP time millil sent
	 */
	private long sendRtcpPktSR(long rtpTs) {
		long ntpTimeMills = getNtpTimeMillis(rtpTs);
		RtcpPktSR sr = new RtcpPktSR(outPart.ssrc(), sentPktCount, sentOctetCount, null);
		sr.rtpTs = rtpTs;
		sr.ntpTs1 = NtpTime.getNtpTs1(ntpTimeMills);
		sr.ntpTs2 = NtpTime.getNtpTs2(ntpTimeMills);
		
		sendRtcpPkt(sr);
		return sr.ntpTs1;
	}



	private void sendRtcpPktBye(String reason) {
		Charset utf8 = Charset.forName("UTF-8");
		RtcpPktBYE bye = new RtcpPktBYE(new long[]{outPart.ssrc()}, null == reason ? null : reason.getBytes(utf8));
	
		sendRtcpPkt(bye);
	}

	public void sendRtcpPkt(RtcpPkt sr) {
		sr.encode();
		final byte[] rawPkt = sr.rawPkt;
		final int payloadLength = rawPkt.length;

		// 依次发送 rtp 包
		ByteBufAllocator alloc = channel().alloc();

		ByteBuf payload = alloc.buffer(4 + payloadLength);
		payload.writeByte('$');
		payload.writeByte(rtcpChannel());
		payload.writeShort(payloadLength);

		payload.writeBytes(rawPkt);

		if(writeAndFlush(payload, null)) {
			logger.info("stream#{} sent {}", mediaStream.getStreamIndex(), sr);
		}
	}

	
	private boolean writeAndFlush(ByteBuf data,
			GenericFutureListener<? extends Future<? super Void>> onComplete) {
		Channel channel = channel();
		if (channel.isWritable()) {
			ChannelPromise promise = channel.newPromise();
			if (null != onComplete) {
				promise.addListener(onComplete);
			}
			
			channel.writeAndFlush(data, promise);

			return true;
		}
		
		
		return false;
	}
	
	public Channel channel() {
		return channel;
	}

	public int rtcpChannel() {
		return rtcpSession().rtcpChannel();
	}

	public int rtpChannel() {
		return rtpChannel;
	}

	public void rtpChannel(int rtpChannel) {
		this.rtpChannel = rtpChannel;
	}

	@Override
	public void endSession(String reason) {
		sendRtcpPktBye(reason);
	}

	@Override
	protected void generateCNAME() {
		SocketAddress addr = channel.localAddress();

		String hostname = null;

		if (addr instanceof InetSocketAddress) {
			InetSocketAddress inet = (InetSocketAddress) addr;
			hostname = inet.getHostName();
		}

		if ((null == hostname || "0.0.0.0".equals(hostname))
				&& System.getenv("HOSTNAME") != null) {
			hostname = System.getenv("HOSTNAME");
		}

		cname = System.getProperty("user.name") + "@" + hostname;
	}

	public InterLeavedRTCPSession rtcpSession() {
		return (InterLeavedRTCPSession) rtcpSession;
	}

	/**
	 * Find out whether a participant with this SSRC is known.
	 * 
	 * If the user is unknown, and the system is operating in unicast mode, try
	 * to match the ip-address of the sender to the ip address of a previously
	 * unmatched target
	 * 
	 * @param ssrc
	 *            the SSRC of the participant
	 * @param packet
	 *            the packet that notified us
	 * @return the relevant participant, possibly newly created
	 */
	public Participant findParticipant() {
		Participant p = partDb().getParticipant(ssrc);
		if (p == null) {
			p = new InterLeavedParticipant(this, ssrc);
			partDb().addParticipant(2, p);
		}
		return p;
	}

	public MediaStream getMediaStream() {
		return mediaStream;
	}
}
