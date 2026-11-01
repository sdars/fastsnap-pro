/*
 * Decompiled with CFR 0.152.
 */
package com.fastsnap.core;

public final class Lic {
    private final String owner;
    private final long expires;

    public Lic(String string, long l) {
        this.owner = string;
        this.expires = l;
    }

    public static Lic parse(String string) {
        if (string == null || string.length() != 19) {
            return null;
        }
        String string2 = string.substring(0, 16);
        long l = Long.parseLong(string.substring(17));
        int n = 0;
        for (int i = 0; i < string2.length(); ++i) {
            n = n * 31 + string2.charAt(i) & 0xFFFF;
        }
        if (n != Integer.parseInt(string2.substring(14, 16), 16)) {
            return null;
        }
        return new Lic(string2, l);
    }

    public boolean valid(long l) {
        return this.expires >= l;
    }

    public String owner() {
        return this.owner;
    }

    public long expires() {
        return this.expires;
    }
}

