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

import com.google.common.primitives.UnsignedInteger;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Author: jammehcow.
 * Date: 22/04/17.
 */
class DiscordWebhook {
    private String webhookUrl;
    private JSONObject obj;
    private JSONObject embed;

    /**
     * Instantiates a new Discord webhook.
     */
    public DiscordWebhook() {
        this.webhookUrl = "https://discordapp.com/api/webhooks/356417864082915340/UmEcuA0JL1nZDIGljMqsxc_4UhWbSZrRYS6Z73i1hKWD_qs-x3YI0b5z1c23Sr_v-lJR";
        this.obj = new JSONObject();
        this.obj.put("username", "pepsimod");
        this.obj.put("avatar_url", "https://pbs.twimg.com/profile_images/666311334837927936/zAJYBIU8.png");
        this.embed = new JSONObject();
    }

    /**
     * Sets the embed title.
     *
     * @param title the title text
     * @return this
     */
    public DiscordWebhook setTitle(String title) {
        this.embed.put("title", title);
        return this;
    }

    /**
     * Sets the embed title url.
     *
     * @param buildUrl the build url
     * @return this
     */
    public DiscordWebhook setURL(String buildUrl) {
        this.embed.put("url", buildUrl);
        return this;
    }

    /**
     * Sets the build status (for the embed's color).
     *
     * @param isSuccess if the build is successful
     * @return this
     */
    public DiscordWebhook setStatus(boolean isSuccess) {
        this.embed.put("color", (isSuccess) ? Color.GREEN.code : Color.RED.code);
        return this;
    }

    /**
     * Sets the embed description.
     *
     * @param content the content
     * @return this
     */
    public DiscordWebhook setDescription(String content) {
        this.embed.put("description", content);
        return this;
    }

    /**
     * Sets the embed's footer text.
     *
     * @param text the footer text
     * @return this
     */
    public DiscordWebhook setFooter(String text) {
        this.embed.put("footer", new JSONObject().put("text", text));
        return this;
    }

    /**
     * Send the payload to Discord.
     */
    public void send() {
        this.obj.put("embeds", new JSONArray().put(this.embed));

        try {
            HttpResponse<JsonNode> response = Unirest.post(this.webhookUrl).header("Content-Type", "application/json").body(this.obj).asJson();

            try {
                if (response.getBody() == null || response.getBody().getObject().get("embeds") == null)
                    throw new JSONException("Expected.");
                throw new IllegalStateException(response.getBody().getObject().toString(2));
            } catch (JSONException ignored) {
            }
        } catch (UnirestException e) {
            e.printStackTrace();
        }
    }

    private enum Color {
        /**
         * Green "you're sweet as" color.
         */
        GREEN(1681177), /**
         * Red "something ain't right" color.
         */
        RED(11278871);

        private UnsignedInteger code;

        Color(int code) {
            this.code = UnsignedInteger.fromIntBits(code);
        }
    }
}
