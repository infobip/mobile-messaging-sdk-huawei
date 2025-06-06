# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/mstipanov/Programs/android-sdk-macosx/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-ignorewarnings
-keepattributes *Annotation*
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable

# Keep Huawei classes
-keep class com.huawei.** { *; }
-keep interface com.huawei.** { *; }
-keep class android.telephony.HwTelephonyManager { *; }
-keep class com.huawei.android.os.BuildEx$VERSION { *; }
-keep class com.huawei.android.telephony.ServiceStateEx { *; }
-keep class com.huawei.hianalytics.** { *; }
-keep class com.huawei.libcore.io.ExternalStorageFile { *; }
-keep class com.huawei.secure.android.** { *; }
-keep class com.huawei.updatesdk.** { *; }
-keep class com.huawei.agconnect.** { *; }
-keep class com.huawei.hms.** { *; }
-keep class com.huawei.android.** { *; }
-keep class com.huawei.libcore.** { *; }

-dontwarn com.google.crypto.tink.subtle.Ed25519Sign$KeyPair
-dontwarn com.google.crypto.tink.subtle.Ed25519Sign
-dontwarn com.google.crypto.tink.subtle.Ed25519Verify
-dontwarn com.google.crypto.tink.subtle.X25519
-dontwarn org.bouncycastle.asn1.ASN1Encodable
-dontwarn org.bouncycastle.asn1.pkcs.PrivateKeyInfo
-dontwarn org.bouncycastle.asn1.x509.AlgorithmIdentifier
-dontwarn org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
-dontwarn org.bouncycastle.cert.X509CertificateHolder
-dontwarn org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
-dontwarn org.bouncycastle.crypto.BlockCipher
-dontwarn org.bouncycastle.crypto.CipherParameters
-dontwarn org.bouncycastle.crypto.InvalidCipherTextException
-dontwarn org.bouncycastle.crypto.engines.AESEngine
-dontwarn org.bouncycastle.crypto.modes.GCMBlockCipher
-dontwarn org.bouncycastle.crypto.params.AEADParameters
-dontwarn org.bouncycastle.crypto.params.KeyParameter
-dontwarn org.bouncycastle.jce.provider.BouncyCastleProvider
-dontwarn org.bouncycastle.openssl.PEMException
-dontwarn org.bouncycastle.openssl.PEMKeyPair
-dontwarn org.bouncycastle.openssl.PEMParser
-dontwarn org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
-dontwarn org.bouncycastle.crypto.prng.SP800SecureRandom
-dontwarn org.bouncycastle.crypto.prng.SP800SecureRandomBuilder