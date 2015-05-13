/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.spdy;

import static io.netty.handler.codec.spdy.SpdyCodecUtil.SPDY_MAX_NV_LENGTH;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.util.AsciiString;
import io.netty.util.ByteString;
import io.netty.util.CharsetUtil;

import java.util.Set;

public class SpdyHeaderBlockRawEncoder extends SpdyHeaderBlockEncoder {

    private final int version;

    public SpdyHeaderBlockRawEncoder(SpdyVersion version) {
        if (version == null) {
            throw new NullPointerException("version");
        }
        this.version = version.getVersion();
    }

    private static void setLengthField(ByteBuf buffer, int writerIndex, int length) {
        buffer.setInt(writerIndex, length);
    }

    private static void writeLengthField(ByteBuf buffer, int length) {
        buffer.writeInt(length);
    }

    @Override
    public ByteBuf encode(ByteBufAllocator alloc, SpdyHeadersFrame frame) throws Exception {
        Set<CharSequence> names = frame.headers().names();
        int numHeaders = names.size();
        if (numHeaders == 0) {
            return Unpooled.EMPTY_BUFFER;
        }
        if (numHeaders > SPDY_MAX_NV_LENGTH) {
            throw new IllegalArgumentException(
                    "header block contains too many headers");
        }
        ByteBuf headerBlock = alloc.heapBuffer();
        writeLengthField(headerBlock, numHeaders);
        for (CharSequence name: names) {
            final ByteString nameBytes = new ByteString(name, CharsetUtil.UTF_8);
            int length = nameBytes.length();
            writeLengthField(headerBlock, length);
            ByteBufUtil.copy(nameBytes, 0, headerBlock, length);
            int savedIndex = headerBlock.writerIndex();
            int valueLength = 0;
            writeLengthField(headerBlock, valueLength);
            for (CharSequence value: frame.headers().getAll(name)) {
                final ByteString valueBytes = new ByteString(value, CharsetUtil.UTF_8);
                length = valueBytes.length();
                if (length > 0) {
                    ByteBufUtil.copy(valueBytes, 0, headerBlock, length);
                    headerBlock.writeByte(0);
                    valueLength += length + 1;
                }
            }
            if (valueLength != 0) {
                valueLength --;
            }
            if (valueLength > SPDY_MAX_NV_LENGTH) {
                throw new IllegalArgumentException(
                        "header exceeds allowable length: " + name);
            }
            if (valueLength > 0) {
                setLengthField(headerBlock, savedIndex, valueLength);
                headerBlock.writerIndex(headerBlock.writerIndex() - 1);
            }
        }
        return headerBlock;
    }

    @Override
    void end() {
    }
}
