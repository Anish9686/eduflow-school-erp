package com.erp.portal.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * One-shot utility to print BCrypt hashes for seed passwords.
 * Run: java -cp target/classes:... HashGen
 */
public class HashGen {
    public static void main(String[] args) {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder(10);
        String[] roles = {
            "Admin", "Management", "Teacher", "Accountant",
            "Staffadmin", "Librarian", "Reception", "Inventory", "Support",
            "Parent", "Student"
        };
        for (String r : roles) {
            String user = r + "9661";
            String pass = r + "@9661";
            System.out.println(user + " => " + enc.encode(pass));
        }
    }
}
