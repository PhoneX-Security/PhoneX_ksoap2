/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenixpho.phoenixksoap2;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fetches all JAXB generated entities from original project and downloads it here.
 * @author ph4r05
 */
public class Sync {
    public static final String SRC_PATH = "/Users/dusanklinec/workspace/PhoenixSoap/phoenix-soap-parent/phoenix-soap-app/target/generated-sources/jaxb/com/phoenix/soap/beans";
    public static final String DST_PATH = "/Users/dusanklinec/workspace/PhoenixKsoap2/src/main/java/com/phoenix/soap/beans";
    
    public Sync() {
        // nothing to do
    }
    
    public static void main( String[] args )
    {  
        File srcDir = new File(SRC_PATH);
        if (srcDir.exists()==false || srcDir.isDirectory()==false || srcDir.canRead()==false){
            System.err.println("Something is wrong with source directory: " + SRC_PATH);
            System.exit(1);
        }
        
        File dstPath = new File(DST_PATH);
        if (dstPath.exists()==false || dstPath.isDirectory()==false || dstPath.canWrite()==false){
            System.err.println("Something is wrong with destination directory: " + DST_PATH);
            System.exit(1);
        }
        
        // delete all files in dst path
        Process p; 
        try {
            p = Runtime.getRuntime().exec("/bin/rm " + DST_PATH + "/*");
            p.waitFor(); 
        } catch (Exception ex) {
            System.err.println("Cannot delete old files in dst directory");
            ex.printStackTrace(System.err);
        }
        
        for (File child : srcDir.listFiles()){
            if (child.isFile()==false) continue;
            if ("package-info.java".equals(child.getName())) continue;
            System.out.println("Found file: " + child.getName());
            
            try {
                Files.copy(child.toPath(), new File(DST_PATH + "/" + child.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                System.err.println("Problem with toPath conversion");
                ex.printStackTrace(System.err);
            }
        }
    }
}
