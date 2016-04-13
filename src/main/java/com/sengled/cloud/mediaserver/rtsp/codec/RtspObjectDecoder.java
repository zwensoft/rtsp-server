package com.sengled.cloud.mediaserver.rtsp.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufProcessor;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpExpectationFailedEvent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObjectDecoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.internal.AppendableCharSequence;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Decodes {@link ByteBuf}s into RTSP messages represented in
 * {@link HttpMessage}s.
 * <p>
 * <h3>Parameters that prevents excessive memory consumption</h3>
 * <table border="1">
 * <tr>
 * <th>Name</th><th>Meaning</th>
 * </tr>
 * <tr>
 * <td>{@code maxInitialLineLength}</td>
 * <td>The maximum length of the initial line
 *     (e.g. {@code "SETUP / RTSP/1.0"} or {@code "RTSP/1.0 200 OK"})
 *     If the length of the initial line exceeds this value, a
 *     {@link TooLongFrameException} will be raised.</td>
 * </tr>
 * <tr>
 * <td>{@code maxHeaderSize}</td>
 * <td>The maximum length of all headers.  If the sum of the length of each
 *     header exceeds this value, a {@link TooLongFrameException} will be raised.</td>
 * </tr>
 * <tr>
 * <td>{@code maxContentLength}</td>
 * <td>The maximum length of the content.  If the content length exceeds this
 *     value, a {@link TooLongFrameException} will be raised.</td>
 * </tr>
 * </table>
 * @author 陈修恒
 * @date 2016年4月11日
 * @see HttpObjectDecoder
 */
public abstract class  RtspObjectDecoder extends ByteToMessageDecoder {
    private static final String EMPTY_VALUE = "";
    private static final Logger logger = LoggerFactory.getLogger(RtspObjectDecoder.class);

    private final int maxChunkSize;
    private final boolean chunkedSupported;
    protected final boolean validateHeaders;
    private final HeaderParser headerParser;
    private final LineParser lineParser;

    private HttpMessage message;
    private long chunkSize;
    private long contentLength = Long.MIN_VALUE;
    private volatile boolean resetRequested;

    private int rtpChannel;
    private int rtpSize;
    
    // These will be updated by splitHeader(...)
    private CharSequence name;
    private CharSequence value;

    private LastHttpContent trailer;

    /**
     * The internal state of {@link RtspObjectDecoder}.
     * <em>Internal use only</em>.
     */
    private enum State {
        SKIP_CONTROL_CHARS,
        
        READ_INITIAL,
        READ_HEADER,
        READ_VARIABLE_LENGTH_CONTENT,
        READ_FIXED_LENGTH_CONTENT,
        READ_CHUNK_SIZE,
        READ_CHUNKED_CONTENT,
        READ_CHUNK_DELIMITER,
        READ_CHUNK_FOOTER,
        BAD_MESSAGE,
        UPGRADED,

        READ_RTP_CONTROL,
        READ_RTP_SIZE,
        READ_RTP_CHANNEL,
        READ_RTP_DATA
    }

    private State currentState = State.SKIP_CONTROL_CHARS;

    /**
     * Creates a new instance with the default
     * {@code maxInitialLineLength (4096}}, {@code maxHeaderSize (8192)}, and
     * {@code maxChunkSize (8192)}.
     */
    protected RtspObjectDecoder() {
        this(4096, 8192, 8192, true);
    }

    
    /**
     * Creates a new instance with the specified parameters.
     */
    protected RtspObjectDecoder(int maxInitialLineLength, int maxHeaderSize, int maxContentLength) {
        this(maxInitialLineLength, maxHeaderSize, maxContentLength * 2, false);
    }
    
    /**
     * Creates a new instance with the specified parameters.
     */
    protected RtspObjectDecoder(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean chunkedSupported) {
        this(maxInitialLineLength, maxHeaderSize, maxChunkSize, chunkedSupported, true);
    }

    /**
     * Creates a new instance with the specified parameters.
     */
    protected RtspObjectDecoder(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize,
            boolean chunkedSupported, boolean validateHeaders) {

        if (maxInitialLineLength <= 0) {
            throw new IllegalArgumentException(
                    "maxInitialLineLength must be a positive integer: " +
                            maxInitialLineLength);
        }
        if (maxHeaderSize <= 0) {
            throw new IllegalArgumentException(
                    "maxHeaderSize must be a positive integer: " +
                            maxHeaderSize);
        }
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException(
                    "maxChunkSize must be a positive integer: " +
                            maxChunkSize);
        }
        this.maxChunkSize = maxChunkSize;
        this.chunkedSupported = chunkedSupported;
        this.validateHeaders = validateHeaders;
        AppendableCharSequence seq = new AppendableCharSequence(128);
        lineParser = new LineParser(seq, maxInitialLineLength);
        headerParser = new HeaderParser(seq, maxHeaderSize);
    }

    

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
         if (resetRequested) {
            resetNow();
         }
         
         logger.debug("{}", buffer);
         
         switch (currentState) {
            case SKIP_CONTROL_CHARS: {
                if (!skipControlCharacters(buffer)) {
                    return;
                }
                
                if (buffer.readableBytes() < 1) {
                    return;
                }

                int rIndex = buffer.readerIndex();
                int control = buffer.getUnsignedByte(rIndex);
                if ('$' == control) {
                    currentState = State.READ_RTP_CONTROL;
                    decodeInterleavedFrame(ctx, buffer, out);
                } else {
                    currentState = State.READ_INITIAL;
                    decodeRtspObject(ctx, buffer, out);
                }
                return;
            }
            case READ_INITIAL:
            case READ_HEADER:
            case READ_VARIABLE_LENGTH_CONTENT:
            case READ_FIXED_LENGTH_CONTENT:
            case READ_CHUNK_SIZE:
            case READ_CHUNKED_CONTENT:
            case READ_CHUNK_DELIMITER:
            case READ_CHUNK_FOOTER:
            case BAD_MESSAGE:
            case UPGRADED:
                decodeRtspObject(ctx, buffer, out);
                break;
            case READ_RTP_CONTROL:
            case READ_RTP_SIZE:
            case READ_RTP_CHANNEL:
            case READ_RTP_DATA:
                decodeInterleavedFrame(ctx, buffer, out);
                break;
            default:
                throw new IllegalStateException("unsupported state[" + currentState + "]");
                
             
        }
    }

    
    protected void decodeInterleavedFrame(ChannelHandlerContext ctx,
                                          ByteBuf buffer,
                                          List<Object> out) {
        while (buffer.readableBytes() > 0) {
            int rIndex = buffer.readerIndex();
            switch (currentState) {
                case READ_RTP_CONTROL: {
                    int control = buffer.readUnsignedByte();
                    if ('$' != control) {
                        buffer.readerIndex(rIndex);
                        currentState = State.SKIP_CONTROL_CHARS;
                        return;
                    }

                    currentState = State.READ_RTP_CHANNEL;
                    break;
                }
                case READ_RTP_CHANNEL: {
                    if (buffer.readableBytes() < 1) {
                        return;
                    }

                    rtpChannel = buffer.readUnsignedByte();
                    currentState = State.READ_RTP_SIZE;
                    break;
                }
                case READ_RTP_SIZE: {
                    if (buffer.readableBytes() < 2) {
                        return;
                    }
                    rtpSize = buffer.readUnsignedShort();
                    currentState = State.READ_RTP_DATA;
                    break;
                }
                case READ_RTP_DATA: {
                    if (buffer.readableBytes() < rtpSize) {
                        return;
                    }

                    ByteBuf frame = buffer.readSlice(rtpSize).retain();
                    out.add(new DefaultInterleavedFrame(rtpChannel, frame));
                    currentState = State.READ_RTP_CONTROL;
                    break;
                }
                case BAD_MESSAGE:
                case READ_CHUNKED_CONTENT:
                case READ_CHUNK_DELIMITER:
                case READ_CHUNK_FOOTER:
                case READ_CHUNK_SIZE:
                case READ_FIXED_LENGTH_CONTENT:
                case READ_HEADER:
                case READ_INITIAL:
                case READ_VARIABLE_LENGTH_CONTENT:
                case SKIP_CONTROL_CHARS:
                case UPGRADED:
                default:
                    return;

            }
        }
    }

    protected void decodeRtspObject(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) {
       switch (currentState) {
           case READ_INITIAL: try {
               AppendableCharSequence line = lineParser.parse(buffer);
               if (line == null) {
                   return;
               }
               String[] initialLine = splitInitialLine(line);
               if (initialLine.length < 3) {
                   // Invalid initial line - ignore.
                   currentState = State.SKIP_CONTROL_CHARS;
                   return;
               }

               message = createMessage(initialLine);
               currentState = State.READ_HEADER;
               // fall-through
           } catch (Exception e) {
               out.add(invalidMessage(buffer, e));
               return;
           }
           case READ_HEADER: try {
               State nextState = readHeaders(buffer);
               if (nextState == null) {
                   return;
               }
               currentState = nextState;
               switch (nextState) {
                   case SKIP_CONTROL_CHARS:
                       // fast-path
                       // No content is expected.
                       out.add(message);
                       out.add(LastHttpContent.EMPTY_LAST_CONTENT);
                       resetNow();
                       return;
                   case READ_CHUNK_SIZE:
                       if (!chunkedSupported) {
                           throw new IllegalArgumentException("Chunked messages not supported");
                       }
                       // Chunked encoding - generate HttpMessage first.  HttpChunks will follow.
                       out.add(message);
                       return;
                   default:
                       /**
                        * <a href="https://tools.ietf.org/html/rfc7230#section-3.3.3">RFC 7230, 3.3.3</a> states that
                        * if a request does not have either a transfer-encoding or a content-length header then the
                        * message body length is 0. However for a response the body length is the number of octets
                        * received prior to the server closing the connection. So we treat this as variable length
                        * chunked encoding.
                        */
                       long contentLength = contentLength();
                       if (contentLength == 0 || contentLength == -1 && isDecodingRequest()) {
                           out.add(message);
                           out.add(LastHttpContent.EMPTY_LAST_CONTENT);
                           resetNow();
                           return;
                       }

                       assert nextState == State.READ_FIXED_LENGTH_CONTENT ||
                               nextState == State.READ_VARIABLE_LENGTH_CONTENT;

                       out.add(message);

                       if (nextState == State.READ_FIXED_LENGTH_CONTENT) {
                           // chunkSize will be decreased as the READ_FIXED_LENGTH_CONTENT state reads data chunk by
                           // chunk.
                           chunkSize = contentLength;
                       }

                       // We return here, this forces decode to be called again where we will decode the content
                       return;
               }
           } catch (Exception e) {
               out.add(invalidMessage(buffer, e));
               return;
           }
           case READ_VARIABLE_LENGTH_CONTENT: {
               // Keep reading data as a chunk until the end of connection is reached.
               int toRead = Math.min(buffer.readableBytes(), maxChunkSize);
               if (toRead > 0) {
                   ByteBuf content = buffer.readSlice(toRead).retain();
                   out.add(new DefaultHttpContent(content));
               }
               return;
           }
           case READ_FIXED_LENGTH_CONTENT: {
               int readLimit = buffer.readableBytes();

               // Check if the buffer is readable first as we use the readable byte count
               // to create the HttpChunk. This is needed as otherwise we may end up with
               // create a HttpChunk instance that contains an empty buffer and so is
               // handled like it is the last HttpChunk.
               //
               // See https://github.com/netty/netty/issues/433
               if (readLimit == 0) {
                   return;
               }

               int toRead = Math.min(readLimit, maxChunkSize);
               if (toRead > chunkSize) {
                   toRead = (int) chunkSize;
               }
               ByteBuf content = buffer.readSlice(toRead).retain();
               chunkSize -= toRead;

               if (chunkSize == 0) {
                   // Read all content.
                   out.add(new DefaultLastHttpContent(content, validateHeaders));
                   resetNow();
               } else {
                   out.add(new DefaultHttpContent(content));
               }
               return;
           }
           /**
            * everything else after this point takes care of reading chunked content. basically, read chunk size,
            * read chunk, read and ignore the CRLF and repeat until 0
            */
           case READ_CHUNK_SIZE: try {
               AppendableCharSequence line = lineParser.parse(buffer);
               if (line == null) {
                   return;
               }
               int chunkSize = getChunkSize(line.toString());
               this.chunkSize = chunkSize;
               if (chunkSize == 0) {
                   currentState = State.READ_CHUNK_FOOTER;
                   return;
               }
               currentState = State.READ_CHUNKED_CONTENT;
               // fall-through
           } catch (Exception e) {
               out.add(invalidChunk(buffer, e));
               return;
           }
           case READ_CHUNKED_CONTENT: {
               assert chunkSize <= Integer.MAX_VALUE;
               int toRead = Math.min((int) chunkSize, maxChunkSize);
               toRead = Math.min(toRead, buffer.readableBytes());
               if (toRead == 0) {
                   return;
               }
               HttpContent chunk = new DefaultHttpContent(buffer.readSlice(toRead).retain());
               chunkSize -= toRead;

               out.add(chunk);

               if (chunkSize != 0) {
                   return;
               }
               currentState = State.READ_CHUNK_DELIMITER;
               // fall-through
           }
           case READ_CHUNK_DELIMITER: {
               final int wIdx = buffer.writerIndex();
               int rIdx = buffer.readerIndex();
               while (wIdx > rIdx) {
                   byte next = buffer.getByte(rIdx++);
                   if (next == HttpConstants.LF) {
                       currentState = State.READ_CHUNK_SIZE;
                       break;
                   }
               }
               buffer.readerIndex(rIdx);
               return;
           }
           case READ_CHUNK_FOOTER: try {
               LastHttpContent trailer = readTrailingHeaders(buffer);
               if (trailer == null) {
                   return;
               }
               out.add(trailer);
               resetNow();
               return;
           } catch (Exception e) {
               out.add(invalidChunk(buffer, e));
               return;
           }
           case BAD_MESSAGE: {
               // Keep discarding until disconnection.
               buffer.skipBytes(buffer.readableBytes());
               break;
           }
           case UPGRADED: {
               int readableBytes = buffer.readableBytes();
               if (readableBytes > 0) {
                   // Keep on consuming as otherwise we may trigger an DecoderException,
                   // other handler will replace this codec with the upgraded protocol codec to
                   // take the traffic over at some point then.
                   // See https://github.com/netty/netty/issues/2173
                   out.add(buffer.readBytes(readableBytes));
               }
               break;
           }
            case READ_RTP_CHANNEL:
            case READ_RTP_CONTROL:
            case READ_RTP_DATA:
            case READ_RTP_SIZE:
            case SKIP_CONTROL_CHARS:
            default:
                break;
       }
    }
    
    @Override
    protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        decode(ctx, in, out);

        // Handle the last unfinished message.
        if (message != null) {
            boolean chunked = HttpHeaders.isTransferEncodingChunked(message);
            if (currentState == State.READ_VARIABLE_LENGTH_CONTENT && !in.isReadable() && !chunked) {
                // End of connection.
                out.add(LastHttpContent.EMPTY_LAST_CONTENT);
                reset();
                return;
            }
            // Check if the closure of the connection signifies the end of the content.
            boolean prematureClosure;
            if (isDecodingRequest() || chunked) {
                // The last request did not wait for a response.
                prematureClosure = true;
            } else {
                // Compare the length of the received content and the 'Content-Length' header.
                // If the 'Content-Length' header is absent, the length of the content is determined by the end of the
                // connection, so it is perfectly fine.
                prematureClosure = contentLength() > 0;
            }
            resetNow();

            if (!prematureClosure) {
                out.add(LastHttpContent.EMPTY_LAST_CONTENT);
            }
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof ChannelInputShutdownEvent) {
            // The decodeLast method is invoked when a channelInactive event is encountered.
            // This method is responsible for ending requests in some situations and must be called
            // when the input has been shutdown.
            super.channelInactive(ctx);
        } else if (evt instanceof HttpExpectationFailedEvent) {
            switch (currentState) {
            case READ_FIXED_LENGTH_CONTENT:
            case READ_VARIABLE_LENGTH_CONTENT:
            case READ_CHUNK_SIZE:
                reset();
                break;
            default:
                break;
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    protected boolean isContentAlwaysEmpty(HttpMessage msg) {
        if (msg instanceof HttpResponse) {
            HttpResponse res = (HttpResponse) msg;
            int code = res.getStatus().code();

            // Correctly handle return codes of 1xx.
            //
            // See:
            //     - http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html Section 4.4
            //     - https://github.com/netty/netty/issues/222
            if (code >= 100 && code < 200) {
                // One exception: Hixie 76 websocket handshake response
                return !(code == 101 && !res.headers().contains(HttpHeaders.Names.SEC_WEBSOCKET_ACCEPT)
                         && res.headers().contains(HttpHeaders.Names.UPGRADE, HttpHeaders.Values.WEBSOCKET, true));
            }

            switch (code) {
                case 204: case 205: case 304:
                    return true;
            }
        }
        return false;
    }

    /**
     * Resets the state of the decoder so that it is ready to decode a new message.
     * This method is useful for handling a rejected request with {@code Expect: 100-continue} header.
     */
    public void reset() {
        resetRequested = true;
    }

    private void resetNow() {
        HttpMessage message = this.message;
        this.message = null;
        name = null;
        value = null;
        contentLength = Long.MIN_VALUE;
        lineParser.reset();
        headerParser.reset();
        trailer = null;
        if (!isDecodingRequest()) {
            HttpResponse res = (HttpResponse) message;
            if (res != null && res.getStatus().code() == 101) {
                currentState = State.UPGRADED;
                return;
            }
        }

        currentState = State.SKIP_CONTROL_CHARS;
    }

    private HttpMessage invalidMessage(ByteBuf in, Exception cause) {
        currentState = State.BAD_MESSAGE;

        // Advance the readerIndex so that ByteToMessageDecoder does not complain
        // when we produced an invalid message without consuming anything.
        in.skipBytes(in.readableBytes());

        if (message != null) {
            message.setDecoderResult(DecoderResult.failure(cause));
        } else {
            message = createInvalidMessage();
            message.setDecoderResult(DecoderResult.failure(cause));
        }

        HttpMessage ret = message;
        message = null;
        return ret;
    }

    private HttpContent invalidChunk(ByteBuf in, Exception cause) {
        currentState = State.BAD_MESSAGE;

        // Advance the readerIndex so that ByteToMessageDecoder does not complain
        // when we produced an invalid message without consuming anything.
        in.skipBytes(in.readableBytes());

        HttpContent chunk = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER);
        chunk.setDecoderResult(DecoderResult.failure(cause));
        message = null;
        trailer = null;
        return chunk;
    }

    private static boolean skipControlCharacters(ByteBuf buffer) {
        boolean skiped = false;
        final int wIdx = buffer.writerIndex();
        int rIdx = buffer.readerIndex();
        while (wIdx > rIdx) {
            int c = buffer.getUnsignedByte(rIdx++);
            if (!(Character.isISOControl(c) && '$' != c) && !Character.isWhitespace(c)) {
                rIdx--;
                skiped = true;
                break;
            }
        }
        buffer.readerIndex(rIdx);
        return skiped;
    }

    private State readHeaders(ByteBuf buffer) {
        final HttpMessage message = this.message;
        final HttpHeaders headers = message.headers();

        AppendableCharSequence line = headerParser.parse(buffer);
        if (line == null) {
            return null;
        }
        if (line.length() > 0) {
            do {
                char firstChar = line.charAt(0);
                if (name != null && (firstChar == ' ' || firstChar == '\t')) {
                    StringBuilder buf = new StringBuilder(value.length() + line.length() + 1);
                    buf.append(value)
                       .append(' ')
                       .append(line.toString().trim());
                    value = buf.toString();
                } else {
                    if (name != null) {
                        headers.add(name, value);
                    }
                    splitHeader(line);
                }

                line = headerParser.parse(buffer);
                if (line == null) {
                    return null;
                }
            } while (line.length() > 0);
        }

        // Add the last header.
        if (name != null) {
            headers.add(name, value);
        }
        // reset name and value fields
        name = null;
        value = null;

        State nextState;

        if (isContentAlwaysEmpty(message)) {
            HttpHeaders.removeTransferEncodingChunked(message);
            nextState = State.SKIP_CONTROL_CHARS;
        } else if (HttpHeaders.isTransferEncodingChunked(message)) {
            nextState = State.READ_CHUNK_SIZE;
        } else if (contentLength() >= 0) {
            nextState = State.READ_FIXED_LENGTH_CONTENT;
        } else {
            nextState = State.READ_VARIABLE_LENGTH_CONTENT;
        }
        return nextState;
    }

    private long contentLength() {
        if (contentLength == Long.MIN_VALUE) {
            contentLength = HttpHeaders.getContentLength(message, -1);
        }
        return contentLength;
    }

    private LastHttpContent readTrailingHeaders(ByteBuf buffer) {
        AppendableCharSequence line = headerParser.parse(buffer);
        if (line == null) {
            return null;
        }
        CharSequence lastHeader = null;
        if (line.length() > 0) {
            LastHttpContent trailer = this.trailer;
            if (trailer == null) {
                trailer = this.trailer = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER, validateHeaders);
            }
            do {
                char firstChar = line.charAt(0);
                if (lastHeader != null && (firstChar == ' ' || firstChar == '\t')) {
                    List<String> current = trailer.trailingHeaders().getAll(lastHeader);
                    if (!current.isEmpty()) {
                        int lastPos = current.size() - 1;
                        String lineTrimmed = line.toString().trim();
                        CharSequence currentLastPos = current.get(lastPos);
                        StringBuilder b = new StringBuilder(currentLastPos.length() + lineTrimmed.length());
                        b.append(currentLastPos)
                         .append(lineTrimmed);
                        current.set(lastPos, b.toString());
                    } else {
                        // Content-Length, Transfer-Encoding, or Trailer
                    }
                } else {
                    splitHeader(line);
                    CharSequence headerName = name;
                    if (!HttpHeaders.equalsIgnoreCase(HttpHeaders.Names.CONTENT_LENGTH, headerName) &&
                            !HttpHeaders.equalsIgnoreCase(HttpHeaders.Names.TRANSFER_ENCODING, headerName) &&
                            !HttpHeaders.equalsIgnoreCase(HttpHeaders.Names.TRAILER, headerName)) {
                        trailer.trailingHeaders().add(headerName, value);
                    }
                    lastHeader = name;
                    // reset name and value fields
                    name = null;
                    value = null;
                }

                line = headerParser.parse(buffer);
                if (line == null) {
                    return null;
                }
            } while (line.length() > 0);

            this.trailer = null;
            return trailer;
        }

        return LastHttpContent.EMPTY_LAST_CONTENT;
    }

    protected abstract boolean isDecodingRequest();
    protected abstract HttpMessage createMessage(String[] initialLine) throws Exception;
    protected abstract HttpMessage createInvalidMessage();

    private static int getChunkSize(String hex) {
        hex = hex.trim();
        for (int i = 0; i < hex.length(); i ++) {
            char c = hex.charAt(i);
            if (c == ';' || Character.isWhitespace(c) || Character.isISOControl(c)) {
                hex = hex.substring(0, i);
                break;
            }
        }

        return Integer.parseInt(hex, 16);
    }

    private static String[] splitInitialLine(AppendableCharSequence sb) {
        int aStart;
        int aEnd;
        int bStart;
        int bEnd;
        int cStart;
        int cEnd;

        aStart = findNonWhitespace(sb, 0);
        aEnd = findWhitespace(sb, aStart);

        bStart = findNonWhitespace(sb, aEnd);
        bEnd = findWhitespace(sb, bStart);

        cStart = findNonWhitespace(sb, bEnd);
        cEnd = findEndOfString(sb);

        return new String[] {
                sb.subStringUnsafe(aStart, aEnd),
                sb.subStringUnsafe(bStart, bEnd),
                cStart < cEnd? sb.subStringUnsafe(cStart, cEnd) : "" };
    }

    private void splitHeader(AppendableCharSequence sb) {
        final int length = sb.length();
        int nameStart;
        int nameEnd;
        int colonEnd;
        int valueStart;
        int valueEnd;

        nameStart = findNonWhitespace(sb, 0);
        for (nameEnd = nameStart; nameEnd < length; nameEnd ++) {
            char ch = sb.charAt(nameEnd);
            if (ch == ':' || Character.isWhitespace(ch)) {
                break;
            }
        }

        for (colonEnd = nameEnd; colonEnd < length; colonEnd ++) {
            if (sb.charAt(colonEnd) == ':') {
                colonEnd ++;
                break;
            }
        }

        name = sb.subStringUnsafe(nameStart, nameEnd);
        valueStart = findNonWhitespace(sb, colonEnd);
        if (valueStart == length) {
            value = EMPTY_VALUE;
        } else {
            valueEnd = findEndOfString(sb);
            value = sb.subStringUnsafe(valueStart, valueEnd);
        }
    }

    private static int findNonWhitespace(AppendableCharSequence sb, int offset) {
        for (int result = offset; result < sb.length(); ++result) {
            if (!Character.isWhitespace(sb.charAtUnsafe(result))) {
                return result;
            }
        }
        return sb.length();
    }

    private static int findWhitespace(AppendableCharSequence sb, int offset) {
        for (int result = offset; result < sb.length(); ++result) {
            if (Character.isWhitespace(sb.charAtUnsafe(result))) {
                return result;
            }
        }
        return sb.length();
    }

    private static int findEndOfString(AppendableCharSequence sb) {
        for (int result = sb.length() - 1; result > 0; --result) {
            if (!Character.isWhitespace(sb.charAtUnsafe(result))) {
                return result + 1;
            }
        }
        return 0;
    }

    private static class HeaderParser implements ByteBufProcessor {
        private final AppendableCharSequence seq;
        private final int maxLength;
        private int size;

        HeaderParser(AppendableCharSequence seq, int maxLength) {
            this.seq = seq;
            this.maxLength = maxLength;
        }

        public AppendableCharSequence parse(ByteBuf buffer) {
            final int oldSize = size;
            seq.reset();
            int i = buffer.forEachByte(this);
            if (i == -1) {
                size = oldSize;
                return null;
            }
            buffer.readerIndex(i + 1);
            return seq;
        }

        public void reset() {
            size = 0;
        }

        @Override
        public boolean process(byte value) throws Exception {
            char nextByte = (char) value;
            if (nextByte == HttpConstants.CR) {
                return true;
            }
            if (nextByte == HttpConstants.LF) {
                return false;
            }

            if (++ size > maxLength) {
                // TODO: Respond with Bad Request and discard the traffic
                //    or close the connection.
                //       No need to notify the upstream handlers - just log.
                //       If decoding a response, just throw an exception.
                throw newException(maxLength);
            }

            seq.append(nextByte);
            return true;
        }

        protected TooLongFrameException newException(int maxLength) {
            return new TooLongFrameException("HTTP header is larger than " + maxLength + " bytes.");
        }
    }

    private static final class LineParser extends HeaderParser {

        LineParser(AppendableCharSequence seq, int maxLength) {
            super(seq, maxLength);
        }

        @Override
        public AppendableCharSequence parse(ByteBuf buffer) {
            reset();
            return super.parse(buffer);
        }

        @Override
        protected TooLongFrameException newException(int maxLength) {
            return new TooLongFrameException("An HTTP line is larger than " + maxLength + " bytes.");
        }
    }


}
