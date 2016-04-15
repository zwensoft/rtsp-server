package com.sengled.cloud.mediaserver.rtsp.rtp;

public class Rational {
    public static final Rational $_1_000 = new Rational(1, 1000);
    public static final Rational $_8_000 = new Rational(1, 8000);
    public static final Rational $90_000 = new Rational(1, 90000);

    final private int num;
    final private int den;
    
    public Rational(int num, int den) {
        super();
        this.num = num;
        this.den = den;
    }
    

    public static Rational valueOf(int den) {
        switch (den) {
            case 1000:
                return $_1_000;
            case 90000:
                return $90_000;
            default:
                return new Rational(1, den);
        }
    }
    
    public int convert(int value, Rational unit) {
        return value * unit.num * den / (unit.den * num) ;
    }
    
    public long convert(long value, Rational unit) {
        return value * unit.num * den / (unit.den * num) ;
    }

    @Override
    public String toString() {
        return num + "/" + den;
    }
}
