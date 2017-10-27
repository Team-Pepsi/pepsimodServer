/*
 * Adapted from the Wizardry License
 *
 * Copyright (c) 2017 Team Pepsi
 *
 * Permission is hereby granted to any persons and/or organizations using this software to copy, modify, merge, publish, and distribute it.
 * Said persons and/or organizations are not allowed to use the software or any derivatives of the work for commercial use or any other means to generate income, nor are they allowed to claim this software as their own.
 *
 * The persons and/or organizations are also disallowed from sub-licensing and/or trademarking this software without explicit permission from Team Pepsi.
 *
 * Any persons and/or organizations using this software must disclose their source code and have it publicly available, include this license, provide sufficient credit to the original authors of the project (IE: Team Pepsi), as well as provide a link to the original project.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package team.pepsi.pepsimod.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.ReadTimeoutException;
import team.pepsi.pepsimod.common.util.CryptUtils;
import team.pepsi.pepsimod.common.util.SerializableUtils;
import team.pepsi.pepsimod.common.util.Zlib;
import team.pepsi.pepsimod.server.packet.ClientRequest;
import team.pepsi.pepsimod.server.packet.Packet;
import team.pepsi.pepsimod.server.packet.PepsiPacket;
import team.pepsi.pepsimod.server.packet.ServerPepsimodSend;

import java.util.HashMap;

import static team.pepsi.pepsimod.server.Server.bannedIPs;
import static team.pepsi.pepsimod.server.Server.protocol;

public class PepsiServerHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        try {
            Packet packet = new Packet(buf);
            System.out.println("Handling message with ID " + packet.getId());
            if (bannedIPs.contains(ctx.channel().remoteAddress().toString().split(":")[0])) {
                PepsiPacket.closeSession(ctx, "You are IP banned!", true);
                return;
            }
            int id = packet.getId();
            if (id == 0) {
                ClientRequest pck = new ClientRequest(packet.buffer);
                pck.decode();
                System.out.println("Client " + pck.username + ", HWID " + pck.hwid + ", next request " + pck.nextRequest + ", protocol " + pck.protocol + ", MC version " + pck.version + ", IP " + ctx.channel().remoteAddress().toString().split(":")[0]);
                if (pck.protocol != protocol) {
                    PepsiPacket.closeSession(ctx, "You're using an outdated launcher!", true);
                    return;
                }
                User user = (User) Server.tag.getSerializable(pck.username);
                if (user == null) {
                    PepsiPacket.closeSession(ctx, "Invalid credentials!", false);
                    return;
                } else {
                    if (user.isValidHWID(pck.hwid)) {
                        if (user.isHWIDSlotFree()) {
                            user.addHWID(pck.hwid);
                        }
                        switch (pck.nextRequest) {
                            case 0:
                                System.out.println("Sending...");
                                HashMap<String, byte[]> classes = Server.version_to_pepsimod.get(pck.version), assets = Server.version_to_assets.get(pck.version);
                                if (classes == null || assets == null) {
                                    PepsiPacket.closeSession(ctx, "You're using an unsupported version!", true);
                                    return;
                                }
                                byte[] classesProcessed = Zlib.deflate(CryptUtils.encrypt(SerializableUtils.toBytes(classes), user.password), 7);
                                byte[] assetsProcessed = Zlib.deflate(CryptUtils.encrypt(SerializableUtils.toBytes(assets), user.password), 7);
                                ServerPepsimodSend send = new ServerPepsimodSend();
                                send.classes = classesProcessed;
                                send.assets = assetsProcessed;
                                send.config = user.config == null ? user.config = "{}" : user.config;
                                send.encode();
                                ctx.writeAndFlush(send.buffer);
                                return;
                            case 1:
                                user.password = pck.password;
                                PepsiPacket.closeSession(ctx, "Success!", false);
                                break;
                            case 2:
                                user.config = pck.config;
                                PepsiPacket.closeSession(ctx, "Success!", false);
                                break;
                        }
                    } else {
                        PepsiPacket.closeSession(ctx, "Invalid HWID! Ask DaPorkchop_ for a reset!", true);
                        return;
                    }
                }
            }
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
            PepsiPacket.closeSession(ctx, "bad packet", false);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (!(cause instanceof ReadTimeoutException)) {
            cause.printStackTrace();
        }
        ctx.close();
    }
}
