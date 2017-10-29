package ar.edu.itba.mna.heartrate;

import org.apache.commons.math3.complex.Complex;

/**
 * Created by traie_000 on 10/28/2017.
 */

public class FFT {

    public static void fftshift(Complex[] arr) {
        Complex aux;
        for (int i=0; i < arr.length / 2 ; i++) {
            aux = arr[i];
            arr[i] = arr[arr.length / 2 + i];
            arr[arr.length / 2 + i] = aux;
        }
    }

    public static double[] complexSquare(Complex[] arr) {
        double[] ans = new double[arr.length];
        for (int i=0; i < arr.length ; i++) {
            ans[i] = Math.pow(arr[i].abs(), 2);
        }
        return ans;
    }

    public static Complex[] fft(double[] arr) {
        return fft_inner(arr, arr.length, 1, 0);
    }

    private static Complex[] fft_inner(double[] arr, int n, int s, int i) {
        Complex[] ans = new Complex[n];
        if (n == 1) {
            ans[0] = new Complex(arr[i]);
        } else {
            System.arraycopy(fft_inner(arr, n/2, 2*s, i), 0, ans, 0, n/2);
            System.arraycopy(fft_inner(arr, n/2, 2*s, i + s), 0, ans, n/2, n/2);
            Complex e = new Complex(Math.E);
            for (int k = 0; k < n/2; k++) {
                Complex ek = ans[k];
                Complex ok = ans[k+n/2].multiply(e.pow(new Complex(0, - 2 * Math.PI * k / n)));
                ans[k] = ek.add(ok);
                ans[k+n/2] = ek.subtract(ok);
            }
        }
        return ans;
    }
}
