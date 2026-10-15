package com.haoyang.qdblocker;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/** Grants visibility to the official, certificate-pinned local mitm plugin. */
public final class PhoneBridge {
    static final String PACKAGE = "com.pcapdroid.mitm";
    private static final String SIGNER = "ac701125e653189b2bbaa78fba60c1136b88b8780078db0dc4257f019c81c9fe";
    private PhoneBridge() {}

    static boolean isOfficial(Context context) {
        byte[] digest = new byte[SIGNER.length() / 2];
        for (int i = 0; i < digest.length; i++) digest[i] = (byte) Integer.parseInt(SIGNER.substring(i * 2, i * 2 + 2), 16);
        return context.getPackageManager().hasSigningCertificate(PACKAGE, digest, PackageManager.CERT_INPUT_SHA256);
    }

    static void prepare(Context context) {
        if (isOfficial(context)) context.grantUriPermission(PACKAGE, ProbeProvider.URI, Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    static void initialize(Context context) {
        // Public builds start with an empty blacklist; existing saved rules stay intact.
        prepare(context);
    }
}
