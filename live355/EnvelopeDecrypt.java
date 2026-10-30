import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.security.*;
import java.security.spec.MGF1ParameterSpec;
import javax.crypto.*;
import javax.crypto.spec.*;

public final class EnvelopeDecrypt {
  public static void main(String[] args) throws Exception {
    byte[] storeBytes = Base64.getMimeDecoder().decode(System.getenv("FLIPCHECK_KEYSTORE_BASE64"));
    char[] password = System.getenv("FLIPCHECK_KEYSTORE_PASSWORD").toCharArray();
    Path path = Files.createTempFile("rarok-live-keystore-", ".bin");
    try {
      Files.write(path, storeBytes);
      KeyStore store = KeyStore.getInstance(path.toFile(), password);
      String alias = System.getenv("FLIPCHECK_KEY_ALIAS");
      if (alias == null || alias.isEmpty()) {
        Enumeration<String> aliases = store.aliases();
        while (aliases.hasMoreElements()) { String item = aliases.nextElement(); if (store.isKeyEntry(item)) {alias = item; break;} }
      }
      java.security.cert.Certificate certificate = store.getCertificate(alias);
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
      StringBuilder hex = new StringBuilder(); for (byte b : digest) hex.append(String.format("%02x", b & 255));
      if (!hex.toString().equals("cf7cb81d2bcb16fd34b4e8ed87691d586417a579b5723a3d8d97cfd77386f04c")) throw new IllegalStateException("SIGNING_CERTIFICATE_MISMATCH");
      String kp = System.getenv("FLIPCHECK_KEY_PASSWORD");
      Key key = store.getKey(alias, (kp == null || kp.isEmpty() ? new String(password) : kp).toCharArray());
      Properties properties = new Properties(); try (InputStream in = Files.newInputStream(Paths.get(args[0]))) { properties.load(in); }
      Cipher wrap = Cipher.getInstance("RSA/ECB/OAEPPadding");
      wrap.init(Cipher.DECRYPT_MODE, key, new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
      byte[] symmetric = wrap.doFinal(Base64.getDecoder().decode(properties.getProperty("wrapped_key")));
      Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
      aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(symmetric, "AES"), new GCMParameterSpec(128, Base64.getDecoder().decode(properties.getProperty("iv"))));
      byte[] data = aes.doFinal(Base64.getDecoder().decode(properties.getProperty("ciphertext")));
      Path output = Paths.get(args[1]); Files.write(output, data);
      Files.setPosixFilePermissions(output, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
      Arrays.fill(symmetric, (byte)0); Arrays.fill(data, (byte)0);
      System.out.println("ENCRYPTED_CONFIGURATION_LOADED");
    } finally {Files.deleteIfExists(path); Arrays.fill(storeBytes, (byte)0); Arrays.fill(password, '\0');}
  }
}
