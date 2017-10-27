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

import java.io.Serializable;

public class User implements Serializable {
    private static final long serialVersionUID = 380246685237890169L;
    public String username = null;
    public String password = null;
    public String config = "{}";
    public String[] hwids = new String[]{null, null};

    public boolean isValidHWID(String hwid) {
        if (isHWIDSlotFree()) {
            return true;
        }

        return containsHWID(hwid);
    }

    public boolean containsHWID(String hwid) {
        for (String s : hwids) {
            if (hwid.equals(s)) {
                return true;
            }
        }

        return false;
    }

    public boolean isHWIDSlotFree() {
        for (String s : hwids) {
            if (s == null) {
                return true;
            }
        }

        return false;
    }

    public void addHWID(String hwid) {
        if (isHWIDSlotFree() && !containsHWID(hwid)) {
            for (int i = 0; i < hwids.length; i++) {
                if (hwids[i] == null) {
                    hwids[i] = hwid;
                    return;
                }
            }
        }
    }

    public void addHWIDSlots(int count) {
        if (count <= hwids.length) {
            return;
        }

        String[] newHwids = new String[count];
        for (int i = 0; i < hwids.length; i++) {
            newHwids[i] = hwids[i];
        }

        hwids = newHwids;
    }
}
