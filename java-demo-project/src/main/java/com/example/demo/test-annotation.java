package com.example;

public class TestAnnotation {
    @Override
    public String toString() {
        return "test";
    }
    
    // This should NOT have @Override but let's see if annotation detection works
    @SuppressWarnings("unused")
    public void regularMethod() {
        System.out.println("regular");
    }
}
