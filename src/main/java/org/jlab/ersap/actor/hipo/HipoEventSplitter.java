package org.jlab.ersap.actor.hipo;

import j4np.hipo5.data.Bank;
import j4np.hipo5.io.HipoReader;

public class HipoEventSplitter {

//    public static void main(String[] args){
//        HipoReader r = new HipoReader("infile.h5");
//
//        // Get ADC data banks
//
//        Bank[] b = r.getBanks("FTOF::adc","ECAL::adc","FTCAL::adc","FTHODO::adc","FTTRK::adc",
//                "HTCC::adc","BST::adc","CTOF::adc","CND::adc","LTCC::adc","BMT::adc","FMT::adc",
//                "HEL::adc","RF::adc","BAND::adc","RASTER::adc");
//
//        while(r.nextEvent(b)==true){
//            System.out.println("**** next event\n");
//
//            // b[0] will be ADC data from the FTOF, b[1] ECAL, and etc.
//            // Your code should define a stream ID per bank and stream them to an LB
//
//            // Below is an example of additional information you can extract using HIPO API from banks for FTOF and ECAL. Yet, we do not need this for streaming.
//
//            //---- read FTOF
//            int nrowsFTOF = b[0].getRows();
//            for(int i = 0; i < nrowsFTOF; i++)
//                System.out.printf("FTOF : row %3d, sec %4d, layer %4d, paddle %4d, ADC = %6d\n",i+1,
//                        b[0].getInt("sector",i),b[0].getInt("layer",i),b[0].getInt("component",i),b[0].getInt("ADC",i) );
//
//
//            //---- read ECAL
//            int nrowsECAL = b[1].getRows();
//            for(int i = 0; i < nrowsECAL; i++)
//                System.out.printf("ECAL : row %3d, sec %4d, layer %4d, paddle %4d, ADC = %6d\n",i+1,
//                        b[1].getInt("sector",i),b[1].getInt("layer",i),b[1].getInt("component",i),b[1].getInt("ADC",i) );
//        }
//    }

}
