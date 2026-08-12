package com.codegym.aiplanning.sandbox;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class TikaSandbox {
    public static void main(String[] args) throws Exception {
        Tika tika = new Tika();
        
        // Mock a zip file (which docx is based on)
        byte[] docxContent = "PK\u0003\u0004 some docx zip content".getBytes();
        
        InputStream is1 = new ByteArrayInputStream(docxContent);
        String detected1 = tika.detect(is1);
        System.out.println("Without name: " + detected1);
        
        InputStream is2 = new ByteArrayInputStream(docxContent);
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "test.docx");
        String detected2 = tika.detect(is2, metadata);
        System.out.println("With metadata: " + detected2);
        
        InputStream is3 = new ByteArrayInputStream(docxContent);
        String detected3 = tika.detect(is3, "test.docx");
        System.out.println("With name: " + detected3);
    }
}
