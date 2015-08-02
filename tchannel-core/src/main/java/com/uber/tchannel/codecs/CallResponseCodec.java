/*
 * Copyright (c) 2015 Uber Technologies, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.uber.tchannel.codecs;

import com.uber.tchannel.checksum.ChecksumType;
import com.uber.tchannel.framing.TFrame;
import com.uber.tchannel.messages.CallMessage;
import com.uber.tchannel.messages.CallResponse;
import com.uber.tchannel.messages.MessageType;
import com.uber.tchannel.tracing.Trace;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;

import java.util.List;
import java.util.Map;

public final class CallResponseCodec extends MessageToMessageCodec<TFrame, CallResponse> {

    @Override
    protected void encode(ChannelHandlerContext ctx, CallResponse msg, List<Object> out) throws Exception {
        /**
         * Allocate a buffer for the rest of the pipeline
         *
         * TODO: Figure out sane initial buffer size allocation
         */
        ByteBuf buffer = ctx.alloc().buffer(CallMessage.MAX_ARG1_LENGTH, TFrame.MAX_FRAME_LENGTH);

        // flags:1
        buffer.writeByte(msg.getFlags());

        // code:1
        buffer.writeByte(msg.getCode().byteValue());

        // tracing:25
        CodecUtils.encodeTrace(msg.getTracing(), buffer);

        // headers -> nh:1 (hk~1 hv~1){nh}
        CodecUtils.encodeSmallHeaders(msg.getHeaders(), buffer);

        // csumtype:1
        buffer.writeByte(msg.getChecksumType().byteValue());

        // (csum:4){0,1}
        CodecUtils.encodeChecksum(msg.getChecksum(), msg.getChecksumType(), buffer);

        /**
         * Payload
         *
         * TODO: hmm we've already written these bytes, why are we writing them again? we should build a composite
         * buffer via {@link io.netty.buffer.Unpooled.wrappedBuffer}. The only trick there is that we need to
         * combine the length of both buffers, which is non-trivial other than just recording it and passing it
         * through the whole pipeline along with the Msg.
         */
        buffer.writeBytes(msg.getPayload());

        TFrame frame = new TFrame(buffer.writerIndex(), MessageType.CallResponse, msg.getId(), buffer);
        out.add(frame);

    }

    @Override
    protected void decode(ChannelHandlerContext ctx, TFrame frame, List<Object> out) throws Exception {
        // flags:1
        byte flags = frame.payload.readByte();

        // code:1
        CallResponse.CallResponseCode code = CallResponse.CallResponseCode.fromByte(frame.payload.readByte()).get();

        // tracing:25
        Trace trace = CodecUtils.decodeTrace(frame.payload);

        // headers -> nh:1 (hk~1, hv~1){nh}
        Map<String, String> headers = CodecUtils.decodeSmallHeaders(frame.payload);

        // csumtype:1
        ChecksumType checksumType = ChecksumType.fromByte(frame.payload.readByte()).get();

        // (csum:4){0,1}
        int checksum = CodecUtils.decodeChecksum(checksumType, frame.payload);

        // arg1~2 arg2~2 arg3~2
        int payloadSize = frame.size - frame.payload.readerIndex();
        ByteBuf payload = frame.payload.readSlice(payloadSize);
        payload.retain();

        CallResponse req = new CallResponse(
                frame.id, flags, code, trace, headers, checksumType, checksum, payload
        );
        out.add(req);
    }

}