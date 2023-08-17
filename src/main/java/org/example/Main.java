package org.example;

public class Main {
    public static void main(String[] args) {
        System.out.println("Consuming Messages from Queue");
        new Consumer().sendStreamToKafka();
    }
}