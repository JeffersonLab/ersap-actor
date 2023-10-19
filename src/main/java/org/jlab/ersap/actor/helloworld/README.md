# ERSAP Hello World Project

This project demonstrates the development of ERSAP actors by creating three actors: Source, Processor, and Sink. Each actor plays a specific role in the system:

1. **Source Actor: RandomNumGenActor**
    - Generates random integer numbers within the range of 1-100.
    - Streams the generated numbers to the Processor Actor.

2. **Processor Actor: HelloWorldActor**
    - Receives integer values from the Source Actor.
    - Based on the received integer, it defines the "Hello World" message to be presented in either English or Italian.
    - Streams the generated "Hello World" message to the Sink Actor.

3. **Sink Actor**
    - Receives the "Hello World" message from the Processor Actor.
    - Prints the message to the standard output.

## Prerequisites

Before you begin, ensure you have the following prerequisites installed on your system:

- Java Development Kit (JDK) 8 or higher
- ERSAP Framework

## Getting Started

Follow these steps to set up and run the ERSAP Hello World project:

1. Install the ERSAP system, if not already installed:

   ```bash
   
   export ERSAP_HOME your_ersap_installetion_dir
   export ERSAP_USER_DATA your_user_data_dir 
   
   git clone https://github.com/JeffersonLab/ersap-java.git
   cd ersap-java
   ./gradlew deploy
   ```
2. Clone the ersap-actor repository:

   ```bash
   git clone https://github.com/JeffersonLab/ersap-actor.git
   cd ersap-actor
   ```

3. Build the project:

   ```bash
   ./gradlew deploy
   ```

4. Install data processing application configuration file:

   ```bash
   # Deploy the Source Actor
   cp ersap-java/src/main/java/org/jlab/ersap/actor/helloworld.yaml $ERSAP_USER_DATA/config/services.yaml
    ```

5. Start the application:

   ```bash
   # Start the Source Actor
   cd $ERSAP_HOME/bin/ersap-shell
   ersap> run local
    ```

6. Monitor, configure and edit data processing application:

   ```bash
   ersap> help
   ```

## Usage

The Source Actor generates random integer values within the range of 1-100, which are processed by the Processor Actor to determine the language of the "Hello World" message. The Sink Actor then prints the generated message to the standard output.

You can modify the logic in the actors as needed for your specific use case.

 