package com.sorrowmist.uselessgenerators;

final class AePortPolicy {
    private AePortPolicy() {}
    static PortMode connectedMode(PortMode[] modes, boolean[] connected) {
        boolean input = false, output = false;
        for (int i = 0; i < 6; i++) if (connected[i]) {
            input |= modes[i].input; output |= modes[i].output;
        }
        return output ? (input ? PortMode.BOTH : PortMode.OUTPUT) : input ? PortMode.INPUT : PortMode.DISABLED;
    }
}
