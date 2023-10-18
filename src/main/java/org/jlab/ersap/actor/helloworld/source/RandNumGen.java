package org.jlab.ersap.actor.helloworld.source;

import java.util.Random;

public class RandNumGen {
    private final Random random;

    // Constructor
    public RandNumGen() {
        this.random = new Random();
    }

    // Generates a random number between 1 and 100 (inclusive)
    public int generate() {
        return random.nextInt(100) + 1;  // nextInt(100) generates a number between 0 (inclusive) and 100 (exclusive)
    }

    // Testing the class
    public static void main(String[] args) {
        RandNumGen rng = new RandNumGen();

        // Generate and print out 10 random numbers as a demonstration
        for (int i = 0; i < 10; i++) {
            System.out.println(rng.generate());
        }
    }

}
