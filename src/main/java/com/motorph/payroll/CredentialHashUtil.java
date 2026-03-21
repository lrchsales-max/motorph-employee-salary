package com.motorph.payroll;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;

// Utility to convert plaintext credentials CSV to salted SHA-256 hashed CSV.
// Output format for password column: SHA256$<saltHex>$<hashHex>
public class CredentialHashUtil {
    public static void main(String[] args) throws IOException {
        String in = "resources/credentials.csv";
        String out = "resources/credentials_hashed.csv";
        SecureRandom rnd = new SecureRandom();
        try (BufferedReader br = Files.newBufferedReader(Paths.get(in));
             BufferedWriter bw = Files.newBufferedWriter(Paths.get(out))) {
            String header = br.readLine();
            if (header != null) bw.write(header + System.lineSeparator());
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",", 3);
                if (parts.length < 3) continue;
                String user = parts[0].trim();
                String pass = parts[1].trim();
                String role = parts[2].trim();
                byte[] salt = new byte[8];
                rnd.nextBytes(salt);
                String saltHex = bytesToHex(salt);
                String hashHex = sha256Hex(saltHex + pass);
                String outPass = "SHA256$" + saltHex + "$" + hashHex;
                bw.write(user + "," + outPass + "," + role + System.lineSeparator());
            }
        }
        System.out.println("Wrote hashed credentials to resources/credentials_hashed.csv");
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(input.getBytes("UTF-8"));
            return bytesToHex(h);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
