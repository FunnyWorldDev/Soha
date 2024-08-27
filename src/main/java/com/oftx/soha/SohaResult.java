package com.oftx.soha;

public class SohaResult {
    public int status;
    public double result;
    public double randomRate;
    public double resultAmount;

    public SohaResult(int status, double result, double randomRate, double resultAmount) {
        this.status = status;
        this.result = result;
        this.randomRate = randomRate;
        this.resultAmount = resultAmount;
    }
}
