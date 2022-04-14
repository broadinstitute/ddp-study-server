package org.broadinstitute.ddp.model.address;

import java.io.Serializable;

public enum OLCPrecision implements Serializable {
    LEAST(20., 2, 2200), LESS(1., 4, 110.), MEDIUM(0.05, 6, 5.5), MORE(0.0025, 8, 0.275), MOST(0.000125, 11, 0.014);

    private double blockSizeInDegrees;
    private int codeLength;
    private double approximateSideLengthInKm;

    private OLCPrecision(double blockSizeInDegrees, int codeLength, double approximateSideLengthInKm) {
        this.blockSizeInDegrees = blockSizeInDegrees;
        this.codeLength = codeLength;
        this.approximateSideLengthInKm = approximateSideLengthInKm;
    }

    public double getBlockSizeInDegrees() {
        return blockSizeInDegrees;
    }

    public int getCodeLength() {
        return codeLength;
    }

    public double getApproximateSideLengthInKm() {
        return approximateSideLengthInKm;
    }
}
