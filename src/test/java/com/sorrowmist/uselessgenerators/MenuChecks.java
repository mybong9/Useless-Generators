package com.sorrowmist.uselessgenerators;

final class MenuChecks {
    private static void check(boolean condition) { if (!condition) throw new AssertionError("Menu/port check failed"); }
    static void run() {
        // Model the signed short truncation in ClientboundContainerSetDataPacket.
        for (long value : new long[]{0, 32768, 65535, 65536, Integer.MAX_VALUE, 50_000_000_000L,
                20_000_000_000_000L, FuelKind.USEFUL.perMb(), Long.MAX_VALUE}) {
            int[] words = new int[4];
            for (int part = 0; part < 4; part++) words[part] = (short) MenuNumbers.word(value, part);
            check(MenuNumbers.join(index -> words[index], 0) == value);
        }
        for (PortMode mode : PortMode.values()) {
            PortMode cursor = mode; for (int i = 0; i < 4; i++) cursor = cursor.next();
            check(cursor == mode && PortMode.decode(mode.ordinal()) == mode);
        }
        check(!PortMode.DISABLED.input && !PortMode.DISABLED.output);
        check(PortMode.INPUT.input && !PortMode.INPUT.output);
        check(!PortMode.OUTPUT.input && PortMode.OUTPUT.output);
        check(PortMode.BOTH.input && PortMode.BOTH.output);
        check(PortMode.decode(-1) == PortMode.DISABLED && PortMode.decode(255) == PortMode.DISABLED);
        for (int side = 0; side < 6; side++) {
            PortMode[] modes = new PortMode[6]; java.util.Arrays.fill(modes, PortMode.OUTPUT);
            boolean[] connected = new boolean[6]; connected[side] = true;
            check(AePortPolicy.connectedMode(modes, connected) == PortMode.OUTPUT);
            modes[side] = PortMode.INPUT;
            check(AePortPolicy.connectedMode(modes, connected) == PortMode.INPUT);
            modes[side] = PortMode.DISABLED;
            check(AePortPolicy.connectedMode(modes, connected) == PortMode.DISABLED);
            connected[side] = false;
            check(AePortPolicy.connectedMode(modes, connected) == PortMode.DISABLED);
        }
        GeneratorEnergy energy = new GeneratorEnergy(GeneratorTier.K1);
        energy.tick(0);
        check(energy.receive(Long.MAX_VALUE, true) == 50 && energy.stored() == 0);
        check(energy.receive(20, false) == 20 && energy.receive(1000, false) == 30);
        check(energy.receive(1, false) == 0 && energy.stored() == 50);
        check(energy.extract(20, false) == 20 && energy.stored() == 30);
        energy.restore(energy.capacity() - 5); energy.tick(0);
        check(energy.receive(Long.MAX_VALUE, false) == 5 && energy.stored() == energy.capacity());
        System.out.println("Menu checks passed: signed 16-bit long sync, port mode permissions and shared FE input accounting.");
    }
}
