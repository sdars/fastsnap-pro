/*
 * Decompiled with CFR 0.152.
 */
package com.fastsnap.core;

import com.fastsnap.core.Lic;

public final class App {
    public static final String VERSION = "3.2.1";

    public static void main(String[] stringArray) {
        String string = stringArray.length > 0 ? stringArray[0] : "";
        Lic lic = Lic.parse(string);
        if (lic == null) {
            System.out.println("FastSnap Pro 3.2.1)");
            System.out.println("invalid serial");
            System.exit(2);
        }
        if (!lic.valid(System.currentTimeMillis())) {
            System.out.println("FastSnap Pro 3.2.1)");
            System.out.println("license expired for " + lic.owner());
            System.exit(3);
        }
        System.out.println("FastSnap Pro 3.2.1)");
        System.out.println("licensed to " + lic.owner() + " until " + lic.expires());
    }
}

