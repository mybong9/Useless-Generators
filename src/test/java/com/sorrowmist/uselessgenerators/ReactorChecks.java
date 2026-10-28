package com.sorrowmist.uselessgenerators;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

final class ReactorChecks {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void run() {
        check(ReactorLayout.CELLS.size() == 16008 && ReactorLayout.MATERIAL_COUNT == 11812, "Solid/clearance blueprint count");
        int[] counts = new int[ReactorLayout.Part.values().length];
        for (var cell : ReactorLayout.CELLS) counts[cell.part().ordinal()]++;
        check(java.util.Arrays.equals(counts, new int[]{4048, 216, 32, 1, 6338, 1172, 4196, 1, 1, 1, 1, 1}), "Bill of materials, five ME ports and required air");
        check(ReactorLayout.at(39, 16, 39).part() == ReactorLayout.Part.AIR, "Universe chamber stays empty");
        check(ReactorLayout.HATCH_SLOTS.size() == 4053, "Placement guidance shares the validator's hatch positions");
        for (var port : ReactorLayout.Part.values()) if (ReactorLayout.isHatch(port)) {
            check(ReactorLayout.acceptsHatch(ReactorLayout.Part.CASING, port), "Casings accept all hatch kinds");
            for (var slot : ReactorLayout.Part.values()) if (slot != ReactorLayout.Part.CASING)
                check(ReactorLayout.acceptsHatch(slot, port) == (slot == port), "Mandatory ports are type-specific; glass, core, coils and air reject hatches");
        }
        for (int i = 0; i < UniverseEffects.COUNT; i++) {
            var star = UniverseEffects.sample(i, 1200, true);
            check(Math.sqrt(star.x()*star.x()+star.y()*star.y()+star.z()*star.z()) + star.size() < UniverseEffects.SKY_RADIUS,
                    "Round stars and planets stay inside the sky sphere");
        }
        for (var cell : ReactorLayout.CELLS) if (cell.part() != ReactorLayout.Part.AIR) {
            double x = Math.max(0, Math.max(cell.x() - UniverseEffects.CENTER_X, UniverseEffects.CENTER_X - cell.x() - 1));
            double y = Math.max(0, Math.max(cell.y() - UniverseEffects.CENTER_Y, UniverseEffects.CENTER_Y - cell.y() - 1));
            double z = Math.max(0, Math.max(cell.z() - UniverseEffects.CENTER_Z, UniverseEffects.CENTER_Z - cell.z() - 1));
            check(x*x+y*y+z*z > UniverseEffects.SKY_RADIUS * UniverseEffects.SKY_RADIUS, "Background sphere does not intersect any required structure block");
        }
        for (var mesh : new UniverseMesh.Vertex[][]{UniverseMesh.SKY, UniverseMesh.SKY_FAR, UniverseMesh.CORE, UniverseMesh.PLANET}) {
            check(mesh.length % 4 == 0, "Complete sphere quad topology");
            for (var v : mesh) {
                check(Math.abs(v.x()*v.x()+v.y()*v.y()+v.z()*v.z()-1) < 1e-10, "Sphere vertices stay on the unit sphere");
                check(v.u() >= 0 && v.u() <= 1 && v.v() >= 0 && v.v() <= 1, "Valid panoramic texture coordinates");
            }
        }
        for (int rotation = 0; rotation < 4; rotation++) {
            for (int path = 0; path < UniverseEffects.PATHS; path++) for (int j = 0; j <= UniverseEffects.segments(path); j++) {
                var p = UniverseEffects.pathPoint(path, (double) j / UniverseEffects.segments(path), rotation * 3000);
                check(Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z()), "Finite effect path");
                check(Math.abs(p.x()) + UniverseEffects.pathWidth(path) < UniverseEffects.HORIZONTAL_BOUND
                        && Math.abs(p.z()) + UniverseEffects.pathWidth(path) < UniverseEffects.HORIZONTAL_BOUND
                        && Math.abs(p.y()) + UniverseEffects.pathWidth(path) < UniverseEffects.VERTICAL_BOUND, "Paths fit the render bounds");
                if (path < 3) check(p.x()*p.x()+p.y()*p.y()+p.z()*p.z() < UniverseEffects.SKY_RADIUS * UniverseEffects.SKY_RADIUS, "Orbital tracks stay inside the universe sky");
                if (path == 10 || path == 11) check(p.x()*p.x()+p.y()*p.y()+p.z()*p.z() > 17*17, "Exterior rings clear the spherical vessel");
            }
            Set<String> occupied = new HashSet<>();
            Set<String> chunks = new HashSet<>();
            for (var cell : ReactorLayout.CELLS) {
                var p = ReactorLayout.rotate(cell.x(), cell.z(), rotation);
                check(p.x() >= 0 && p.x() < 80 && p.z() >= 0 && p.z() < 80 && cell.y() >= 0 && cell.y() < 32, "Bounds");
                check(occupied.add(p.x() + ":" + cell.y() + ":" + p.z()), "Duplicate rotated position");
                chunks.add((p.x() / 16) + ":" + (p.z() / 16));
                var inverse = ReactorLayout.rotate(p.x(), p.z(), 4 - rotation);
                check(inverse.x() == cell.x() && inverse.z() == cell.z(), "Rotation round trip");
            }
            check(chunks.size() == 25, "Exactly 5x5 chunks");
        }
        check(ReactorLayout.aligned(-80, -16) && !ReactorLayout.aligned(-1, 0), "Negative coordinates");
        check(ReactorLayout.overlaps(0, 0, 0, 16, 0, 0), "Shared modules conflict");
        check(!ReactorLayout.overlaps(0, 0, 0, 80, 0, 0), "Adjacent arrays");
        check(!ReactorLayout.overlaps(0, 0, 0, 0, 32, 0), "Stacked arrays");
        GeneratorEnergy energy = new GeneratorEnergy(ReactorLayout.RATE, ReactorLayout.RATE);
        FuelBurn burn = new FuelBurn();
        var bucket = burn.burn(energy.generationRoom(), FuelKind.USEFUL.perMb(), 1000);
        check(bucket.generated() == ReactorLayout.RATE && bucket.consumedMb() == 1000, "1 B per tick at full output");
        energy.tick(bucket.generated());
        check(energy.stored() == ReactorLayout.RATE && energy.generationRoom() == 0, "One-tick buffer, no overflow");
        check(burn.burn(energy.generationRoom(), FuelKind.USEFUL.perMb(), 16000).consumedMb() == 0, "Full buffer pauses fuel");
        int[] packets = {0};
        long fe = ForgeEnergyTransfer.transfer(energy, offered -> { packets[0]++; return offered; });
        check(packets[0] == 32 && fe == 32L * Integer.MAX_VALUE, "Bounded vanilla FE work");
        long ae = NetworkEnergyTransfer.transfer(energy, 0.5, offered -> 0);
        check(ae == (1L << 40), "AE double precision budget");
        long flux = FluxEnergyTransfer.transfer(energy, offered -> offered);
        check(fe + ae + flux == ReactorLayout.RATE && energy.stored() == 0, "Shared output budget at exascale");
        energy.restore(0); burn.restore(0);
        BigInteger consumed = BigInteger.ZERO, delivered = BigInteger.ZERO;
        for (int tick = 0; tick < 300; tick++) {
            var result = burn.burn(energy.generationRoom(), FuelKind.USEFUL.perMb(), 16000);
            energy.tick(result.generated());
            consumed = consumed.add(BigInteger.valueOf(result.consumedMb()).multiply(BigInteger.valueOf(FuelKind.USEFUL.perMb())));
            long accepted = FluxEnergyTransfer.transfer(energy, offered -> switch (tickMode[0]++ % 4) {
                case 0 -> 0; case 1 -> offered / 3; case 2 -> offered - 1; default -> offered;
            });
            delivered = delivered.add(BigInteger.valueOf(accepted));
            check(consumed.equals(delivered.add(BigInteger.valueOf(energy.stored())).add(BigInteger.valueOf(burn.remainder()))), "Exact fuel accounting across partial transfers");
            if (tick % 17 == 0) {
                long saved = energy.stored(), savedFuel = burn.remainder();
                energy.restore(saved); burn.restore(savedFuel);
                check(energy.stored() == saved && burn.remainder() == savedFuel, "Restore near long boundary");
            }
        }
        System.out.println("Cosmic Heart checks passed: 11812 parts + 4196 clear cells, four rotations, 25 chunks, bounded universe effects, 9.2E/t accounting and transfer budgets.");
    }
    private static final int[] tickMode = {0};
}
