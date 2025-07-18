package com.example;

class BaseClass {
    public void baseMethod() {
        System.out.println("base");
    }
}

public class DerivedClass extends BaseClass {
    @Override
    public void baseMethod() {
        System.out.println("derived");
    }
    
    @Override
    public String toString() {
        return "DerivedClass";
    }
}
