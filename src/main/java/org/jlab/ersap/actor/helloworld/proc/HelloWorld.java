package org.jlab.ersap.actor.helloworld.proc;

public class HelloWorld {
    // Defines "Hello World" in English or Italian based on the input number
    public String defineHelloWorld(int number) {
        if (number < 50) {
            return "Hello World";
        } else {
            return "Ciao Mondo";
        }
    }

    // Testing the class
    public static void main(String[] args) {
        HelloWorld df = new HelloWorld();

        // Test with a few numbers
        System.out.println(df.defineHelloWorld(30));  // Expected output: Hello World
        System.out.println(df.defineHelloWorld(60));  // Expected output: Ciao Mondo
    }

}
