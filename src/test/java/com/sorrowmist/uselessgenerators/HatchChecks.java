package com.sorrowmist.uselessgenerators;

/** Network rejection and exascale energy accounting stay bounded without starting a game. */
final class HatchChecks {
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); }
    static void run() {
        for(int[] size:new int[][]{{320,240},{427,240},{854,480},{1280,720},{1920,1080}}) {
            for(int[] panel:new int[][]{{520,364},{396,302}}) {
                var view=com.sorrowmist.uselesscore.client.ui.UiViewport.fit(size[0],size[1],panel[0],panel[1]);
                int left=(view.canvasWidth()-panel[0])/2,top=(view.canvasHeight()-panel[1])/2;
                check(view.physical(left)>=0&&view.physical(top)>=0,"Fitted menu stays inside viewport");
                check(view.physical(left+panel[0])<=size[0]&&view.physical(top+panel[1])<=size[1],"Entire inventory remains reachable");
                for(int slot=0;slot<16;slot++) {
                    double x=left+MeHatchLayout.cellX(slot)+11,y=top+MeHatchLayout.markY(slot)+11;
                    check(Math.abs(view.local(x*view.scale())-x)<.001&&Math.abs(view.local(y*view.scale())-y)<.001,"Ghost drop and rendered slot share coordinates at every GUI scale");
                }
            }
        }
        check(HatchTransferPolicy.target(2_100_000_000L)==2_100_000_000L,"Manual 2.1G target survives unchanged");
        check(HatchTransferPolicy.target(Long.MAX_VALUE)==2_100_000_000L,"Oversized packets saturate at 2.1G");
        check(HatchTransferPolicy.target(Long.MIN_VALUE)==1,"Corrupt negative target cannot overflow");
        check(HatchTransferPolicy.room(HatchTransferPolicy.MAX_TARGET,2_099_999_999L)==1,"Refilling near 2.1G preserves the last item");
        check(HatchTransferPolicy.MAX_TARGET<Integer.MAX_VALUE,"Forge fluid and item capabilities stay signed-int safe");
        HatchTransferPolicy schedule = new HatchTransferPolicy();
        check(schedule.nextDelay(false) == 10 && schedule.nextDelay(false) == 20
                && schedule.nextDelay(false) == 40 && schedule.nextDelay(false) == 80, "Missing stock backs off");
        for (int i = 0; i < 10000; i++) check(schedule.nextDelay(false) == 80, "Idle retry interval stays capped");
        check(schedule.nextDelay(true) == 5, "Successful transfer resumes fast refill");
        schedule.wake(); check(schedule.nextDelay(false) == 10, "Local consumption resets the backoff");
        check(HatchTransferPolicy.accepted(-1, 64000) == 0, "Reject negative network reply");
        check(HatchTransferPolicy.accepted(Long.MAX_VALUE, 64000) == 64000, "Cannot debit more than offered");
        check(HatchTransferPolicy.room(ReactorLayout.RATE, Long.MAX_VALUE) == 0, "Corrupt oversized storage saturates");
        check(HatchTransferPolicy.room(ReactorLayout.RATE, ReactorLayout.RATE - 1) == 1, "One FE of room near long limit");
        GeneratorEnergy source = new GeneratorEnergy(ReactorLayout.RATE, ReactorLayout.RATE);
        source.tick(ReactorLayout.RATE);
        long rejected = FluxEnergyTransfer.transfer(source, offered -> 0);
        check(rejected == 0 && source.stored() == ReactorLayout.RATE, "Full ME storage preserves every FE");
        long first = FluxEnergyTransfer.transfer(source, offered -> offered / 2);
        long second = FluxEnergyTransfer.transfer(source, offered -> offered);
        check(first + second == ReactorLayout.RATE && source.stored() == 0, "Two energy hatches share the controller budget");
        check(FluxEnergyTransfer.transfer(source, offered -> offered) == 0, "No second emission from an empty buffer");
        System.out.println("ME hatch policy checks passed: capped retries, rejection, saturating capacity and shared 9.2E output budget.");
    }
}
