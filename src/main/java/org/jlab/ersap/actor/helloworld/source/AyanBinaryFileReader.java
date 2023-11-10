package org.jlab.ersap.actor.helloworld.source;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;


public class AyanBinaryFileReader {
    
    private DataInputStream dataInputStream;
    private String fileName;
    private FileInputStream file;
    private ObjectInputStream ins;
    private byte[] bytes;
    public AyanBinaryFileReader(String fileName){
        try{
            file = new FileInputStream(fileName);
            ins = new ObjectInputStream(file);
        } catch (FileNotFoundException e){
            e.printStackTrace();
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    public int readFileContent() {
        // Todo: Note that this is streaming pipeline.
        //       Added an option to inform user about the ned of the file
        //       Otherwise you will see an "empty engine result" ErsapException thrown
         try {
             return ins.readInt();
        } catch (IOException e){
            e.printStackTrace();
            return -1;
        }
    }

    public void exit() {
        try {
            dataInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        exit();
    }
}
