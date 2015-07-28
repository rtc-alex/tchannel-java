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
package com.uber.tchannel.ping;

import com.uber.tchannel.messages.AbstractInitMessage;
import com.uber.tchannel.messages.AbstractMessage;
import com.uber.tchannel.messages.ErrorMessage;
import com.uber.tchannel.messages.InitRequest;
import com.uber.tchannel.messages.InitResponse;
import com.uber.tchannel.messages.PingRequest;
import com.uber.tchannel.messages.PingResponse;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;

import java.util.HashMap;

public class PingClientHandler extends ChannelHandlerAdapter {

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        InitRequest initRequest = new InitRequest(42,
                AbstractInitMessage.DEFAULT_VERSION,
                new HashMap<String, String>() {
                    {
                        put(AbstractInitMessage.HOST_PORT_KEY, "0.0.0.0:0");
                        put(AbstractInitMessage.PROCESS_NAME_KEY, "test-process");
                    }
                }
        );
        ChannelFuture f = ctx.writeAndFlush(initRequest);
        f.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {

        AbstractMessage message = (AbstractMessage) msg;

        switch (message.getMessageType()) {
            case InitResponse:
                // Ensure Handshake success
                InitResponse initResponse = (InitResponse) message;
                System.out.println(initResponse);

                // Fire back a PingRequest
                PingRequest pingRequest = new PingRequest(99);
                ChannelFuture f = ctx.writeAndFlush(pingRequest);
                f.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                break;
            case PingResponse:
                // Ensure we've received the PingResponse
                PingResponse pingResponse = (PingResponse) message;
                System.out.println(pingResponse);

                // We're done, close the connection.
                ctx.close();
                break;
            case Error:
                ErrorMessage errorMessage = (ErrorMessage) message;
                System.err.println(errorMessage);
                break;
            default:
                System.err.println(String.format("Unexpected Message: %s", message));
                ctx.close();
                break;
        }

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

}