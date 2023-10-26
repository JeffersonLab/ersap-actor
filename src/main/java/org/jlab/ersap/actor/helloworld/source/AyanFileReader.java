package org.jlab.ersap.actor.helloworld.source;

import java.io.*;
import java.util.ArrayList;


public class AyanFileReader {
    
    private DataInputStream dataInputStream;
    private String fileName;
    private BufferedReader br;

    public AyanFileReader(String fileName){
        this.fileName = fileName;
        try{
            br = new BufferedReader(new FileReader(fileName));
        } catch (FileNotFoundException e){
            e.printStackTrace();
        }
    }

    public String readFileContent() {
        // Todo: Note that this is streaming pipeline.
        //       Added an option to inform user about the ned of the file
        //       Otherwise you will see an "empty engine result" ErsapException thrown
         try {
             String l = br.readLine();
             if (l == null) {
                 l = "end_of_file";
             }
             return l;
        } catch (IOException e){
            e.printStackTrace();
        }
        return "";
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
