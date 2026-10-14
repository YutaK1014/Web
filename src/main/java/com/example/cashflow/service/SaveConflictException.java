package com.example.cashflow.service;

public class SaveConflictException extends IllegalArgumentException {
    public SaveConflictException(String message) { super(message); }
}
